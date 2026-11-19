//! TLS-pinned LAN transfer engine. Discovery records are never treated as identity.
use crate::protocol::{self, ControlMessage, DeviceIdentity, DevicePlatform, FileManifestEntry, PeerCapabilities, TransferDecision, TransferDecisionKind, TransferOffer};
use hex::FromHex;
use mdns_sd::{ServiceDaemon, ServiceInfo};
use rcgen::generate_simple_self_signed;
use rustls::{pki_types::{CertificateDer, PrivateKeyDer, ServerName}, ClientConfig, ServerConfig};
use rustls::client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier};
use serde::{de::DeserializeOwned, Serialize};
use sha2::{Digest, Sha256};
use std::{collections::HashMap, fs, io::{BufReader, Read}, net::IpAddr, path::{Path, PathBuf}, sync::Arc, time::{Duration, Instant}};
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::{fs as async_fs, io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt}, net::TcpListener, sync::{oneshot, Mutex}};
use tauri::async_runtime::JoinHandle;
use tokio_rustls::{TlsAcceptor, TlsConnector};
use uuid::Uuid;

const SERVICE_TYPE: &str = "_plazmas._tcp.local.";
const MAX_CONTROL_FRAME: usize = 1024 * 1024;
const CHUNK_BYTES: usize = 1024 * 1024;
const MAX_TRANSFER_BYTES: u64 = 100 * 1024 * 1024 * 1024;
const MAX_PENDING_TRANSFERS: usize = 8;

#[derive(Clone)]
pub struct EngineState {
    pending: Arc<Mutex<HashMap<String, oneshot::Sender<bool>>>>,
    listener: Arc<Mutex<Option<JoinHandle<()>>>>,
    mdns: Arc<Mutex<Option<ServiceDaemon>>>,
    connection_slots: Arc<tokio::sync::Semaphore>,
    connection_attempts: Arc<Mutex<HashMap<IpAddr, Vec<Instant>>>>,
}

impl Default for EngineState {
    fn default() -> Self {
        Self {
            pending: Arc::new(Mutex::new(HashMap::new())),
            listener: Arc::new(Mutex::new(None)),
            mdns: Arc::new(Mutex::new(None)),
            connection_slots: Arc::new(tokio::sync::Semaphore::new(8)),
            connection_attempts: Arc::new(Mutex::new(HashMap::new())),
        }
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct PendingOffer {
    transfer_id: String,
    sender_name: String,
    sender_platform: String,
    file_count: usize,
    total_bytes: u64,
    file_names: Vec<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ReceiverStatus {
    listening: bool,
    port: u16,
    fingerprint: String,
}

struct LocalIdentity {
    certs: Vec<CertificateDer<'static>>,
    key: PrivateKeyDer<'static>,
    fingerprint: String,
}

fn load_identity(directory: &Path) -> Result<LocalIdentity, String> {
    fs::create_dir_all(directory).map_err(|e| format!("identity directory: {e}"))?;
    let cert_path = directory.join("device-cert.pem");
    let key_path = directory.join("device-key.pem");
    if !cert_path.exists() || !key_path.exists() {
        let generated = generate_simple_self_signed(vec!["plazma-s.local".into()])
            .map_err(|e| format!("certificate generation: {e}"))?;
        fs::write(&cert_path, generated.cert.pem()).map_err(|e| format!("write certificate: {e}"))?;
        fs::write(&key_path, generated.key_pair.serialize_pem()).map_err(|e| format!("write private key: {e}"))?;
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&key_path, fs::Permissions::from_mode(0o600))
            .map_err(|e| format!("protect private key: {e}"))?;
    }
    let mut cert_reader = BufReader::new(fs::File::open(&cert_path).map_err(|e| e.to_string())?);
    let certs = rustls_pemfile::certs(&mut cert_reader)
        .collect::<Result<Vec<_>, _>>().map_err(|e| format!("read certificate: {e}"))?;
    if certs.len() != 1 { return Err("expected one device certificate".into()); }
    let mut key_reader = BufReader::new(fs::File::open(&key_path).map_err(|e| e.to_string())?);
    let key = rustls_pemfile::private_key(&mut key_reader)
        .map_err(|e| format!("read private key: {e}"))?
        .ok_or("device private key missing")?;
    let fingerprint = hex::encode(Sha256::digest(certs[0].as_ref()));
    Ok(LocalIdentity { certs, key, fingerprint })
}

fn tls_server_config(identity: LocalIdentity) -> Result<Arc<ServerConfig>, String> {
    let provider = Arc::new(rustls::crypto::ring::default_provider());
    let config = ServerConfig::builder_with_provider(provider)
        .with_protocol_versions(&[&rustls::version::TLS13]).map_err(|e| e.to_string())?
        .with_no_client_auth()
        .with_single_cert(identity.certs, identity.key).map_err(|e| format!("TLS identity: {e}"))?;
    Ok(Arc::new(config))
}

#[derive(Debug)]
struct PinnedCertificateVerifier {
    expected: [u8; 32],
    provider: Arc<rustls::crypto::CryptoProvider>,
}
impl ServerCertVerifier for PinnedCertificateVerifier {
    fn verify_server_cert(&self, cert: &CertificateDer<'_>, _intermediates: &[CertificateDer<'_>], _name: &ServerName<'_>, _ocsp: &[u8], _now: rustls::pki_types::UnixTime) -> Result<ServerCertVerified, rustls::Error> {
        let actual = Sha256::digest(cert.as_ref());
        if actual.as_slice() == self.expected { Ok(ServerCertVerified::assertion()) }
        else { Err(rustls::Error::InvalidCertificate(rustls::CertificateError::BadEncoding)) }
    }
    fn verify_tls12_signature(&self, message: &[u8], cert: &CertificateDer<'_>, dss: &rustls::DigitallySignedStruct) -> Result<HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls12_signature(message, cert, dss, &self.provider.signature_verification_algorithms)
    }
    fn verify_tls13_signature(&self, message: &[u8], cert: &CertificateDer<'_>, dss: &rustls::DigitallySignedStruct) -> Result<HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls13_signature(message, cert, dss, &self.provider.signature_verification_algorithms)
    }
    fn supported_verify_schemes(&self) -> Vec<rustls::SignatureScheme> {
        self.provider.signature_verification_algorithms.supported_schemes()
    }
}

fn tls_client_config(fingerprint: &str) -> Result<Arc<ClientConfig>, String> {
    let expected = <[u8; 32]>::from_hex(fingerprint).map_err(|_| "fingerprint must be 64 hexadecimal characters")?;
    let provider = Arc::new(rustls::crypto::ring::default_provider());
    let verifier = Arc::new(PinnedCertificateVerifier { expected, provider: provider.clone() });
    let config = ClientConfig::builder_with_provider(provider)
        .with_protocol_versions(&[&rustls::version::TLS13]).map_err(|e| e.to_string())?
        .dangerous().with_custom_certificate_verifier(verifier).with_no_client_auth();
    Ok(Arc::new(config))
}

async fn write_json<W: AsyncWrite + Unpin, T: Serialize>(stream: &mut W, value: &T) -> Result<(), String> {
    let bytes = serde_json::to_vec(value).map_err(|e| e.to_string())?;
    if bytes.len() > MAX_CONTROL_FRAME { return Err("control frame too large".into()); }
    stream.write_u32(bytes.len() as u32).await.map_err(|e| e.to_string())?;
    stream.write_all(&bytes).await.map_err(|e| e.to_string())
}
async fn read_json<R: AsyncRead + Unpin, T: DeserializeOwned>(stream: &mut R) -> Result<T, String> {
    let len = stream.read_u32().await.map_err(|e| e.to_string())? as usize;
    if len == 0 || len > MAX_CONTROL_FRAME { return Err("invalid control frame length".into()); }
    let mut bytes = vec![0; len];
    tokio::time::timeout(Duration::from_secs(60), stream.read_exact(&mut bytes)).await.map_err(|_| "chunk data timed out")?.map_err(|e| e.to_string())?;
    serde_json::from_slice(&bytes).map_err(|e| format!("invalid control message: {e}"))
}
fn file_sha256(path: &Path) -> Result<String, String> {
    let mut file = fs::File::open(path).map_err(|e| format!("open source file: {e}"))?;
    let mut digest = Sha256::new();
    let mut buffer = vec![0u8; CHUNK_BYTES];
    loop { let n = file.read(&mut buffer).map_err(|e| e.to_string())?; if n == 0 { break; } digest.update(&buffer[..n]); }
    Ok(hex::encode(digest.finalize()))
}
fn safe_name(name: &str) -> String {
    let normalized = name.replace('\\', "/");
    let base = normalized.rsplit('/').next().unwrap_or("received_file");
    let clean: String = base.chars().map(|c| if c.is_control() || "<>:\"/\\|?*".contains(c) { '_' } else { c }).collect();
    let clean = clean.trim().trim_matches('.');
    if clean.is_empty() { "received_file".into() } else { clean.chars().take(160).collect() }
}

#[tauri::command]
pub async fn start_receiver(app: AppHandle, state: State<'_, EngineState>, port: Option<u16>) -> Result<ReceiverStatus, String> {
    if state.listener.lock().await.is_some() { return Err("receiver already running".into()); }
    let directory = app.path().app_data_dir().map_err(|e| e.to_string())?;
    let identity = load_identity(&directory.join("identity"))?;
    let fingerprint = identity.fingerprint.clone();
    let tls = TlsAcceptor::from(tls_server_config(identity)?);
    let listener = TcpListener::bind(("0.0.0.0", port.unwrap_or(53317))).await.map_err(|e| format!("bind receiver: {e}"))?;
    let actual_port = listener.local_addr().map_err(|e| e.to_string())?.port();

    // Publish only after the TLS listener is bound and ready to accept connections.
    let local_ip = local_ip_address::local_ip().map_err(|e| format!("local network address unavailable: {e}"))?;
    let daemon = ServiceDaemon::new().map_err(|e| format!("mDNS unavailable: {e}"))?;
    let instance = format!("plazma-{}", &Uuid::new_v4().simple().to_string()[..8]);
    let platform = if cfg!(target_os = "windows") { "windows" } else { "other" };
    let props = [
        ("app", "plazma-s"),
        ("protocolVersion", "1"),
        ("platform", platform),
        ("fingerprint", fingerprint.as_str()),
        ("tls", "1.3"),
    ];
    let service = ServiceInfo::new(SERVICE_TYPE, &instance, "plazma-s.local.", local_ip.to_string(), actual_port, &props[..])
        .map_err(|e| format!("create mDNS service: {e}"))?;
    daemon.register(service).map_err(|e| format!("advertise receiver: {e}"))?;

    let pending = state.pending.clone();
    let connection_slots = state.connection_slots.clone();
    let connection_attempts = state.connection_attempts.clone();
    let app_handle = app.clone();
    let receive_dir = directory.join("Received");
    async_fs::create_dir_all(&receive_dir).await.map_err(|e| e.to_string())?;
    let task = tauri::async_runtime::spawn(async move {
        loop {
            let (socket, peer) = match listener.accept().await { Ok(v) => v, Err(_) => break };
            // Bound unauthenticated sockets and throttle repeated connection attempts per source IP.
            let now = Instant::now();
            let allowed = {
                let mut attempts = connection_attempts.lock().await;
                attempts.retain(|_, times| { times.retain(|time| now.duration_since(*time) < Duration::from_secs(60)); !times.is_empty() });
                let times = attempts.entry(peer.ip()).or_default();
                if times.len() >= 12 { false } else { times.push(now); true }
            };
            if !allowed { continue; }
            let permit = match connection_slots.clone().try_acquire_owned() { Ok(permit) => permit, Err(_) => continue };
            let acceptor = tls.clone();
            let pending = pending.clone();
            let app_handle = app_handle.clone();
            let receive_dir = receive_dir.clone();
            tokio::spawn(async move {
                let _permit = permit;
                if let Ok(Ok(mut stream)) = tokio::time::timeout(Duration::from_secs(10), acceptor.accept(socket)).await {
                    let _ = receive_offer(&mut stream, &app_handle, &pending, &receive_dir).await;
                }
            });
        }
    });
    *state.listener.lock().await = Some(task);
    *state.mdns.lock().await = Some(daemon);
    Ok(ReceiverStatus { listening: true, port: actual_port, fingerprint })
}

#[tauri::command]
pub async fn stop_receiver(state: State<'_, EngineState>) -> Result<(), String> {
    if let Some(task) = state.listener.lock().await.take() { task.abort(); }
    if let Some(daemon) = state.mdns.lock().await.take() { let _ = daemon.shutdown(); }
    Ok(())
}

#[tauri::command]
pub async fn respond_to_transfer_offer(state: State<'_, EngineState>, transfer_id: String, accept: bool) -> Result<(), String> {
    let sender = state.pending.lock().await.remove(&transfer_id).ok_or("transfer offer expired or not found")?;
    sender.send(accept).map_err(|_| "transfer connection closed".to_string())
}

async fn receive_offer<S: AsyncRead + AsyncWrite + Unpin>(stream: &mut S, app: &AppHandle, pending: &Arc<Mutex<HashMap<String, oneshot::Sender<bool>>>>, receive_dir: &Path) -> Result<(), String> {
    let message: ControlMessage = tokio::time::timeout(Duration::from_secs(15), read_json(stream)).await.map_err(|_| "transfer offer timed out")??;
    let offer = match message { ControlMessage::Offer(offer) => offer, _ => return Err("expected transfer offer".into()) };
    protocol::validate_offer(&offer).map_err(|e| e.to_string())?;
    if offer.total_bytes > MAX_TRANSFER_BYTES { return Err("transfer exceeds the 100 GiB safety limit".into()); }
    let available = fs2::available_space(receive_dir).map_err(|e| format!("check receiver free space: {e}"))?;
    if offer.total_bytes > available { return Err(format!("not enough free disk space: transfer needs {} bytes, only {} bytes are available", offer.total_bytes, available)); }
    let (tx, rx) = oneshot::channel();
    {
        let mut requests = pending.lock().await;
        if requests.len() >= MAX_PENDING_TRANSFERS || requests.contains_key(&offer.transfer_id) {
            return Err("receiver is busy or transfer id is already pending".into());
        }
        requests.insert(offer.transfer_id.clone(), tx);
    }
    let event = PendingOffer {
        transfer_id: offer.transfer_id.clone(),
        sender_name: offer.sender.display_name.clone(),
        sender_platform: format!("{:?}", offer.sender.platform),
        file_count: offer.files.len(),
        total_bytes: offer.total_bytes,
        file_names: offer.files.iter().map(|f| safe_name(&f.file_name)).collect(),
    };
    if let Err(error) = app.emit("plazma-transfer-offer", event) {
        pending.lock().await.remove(&offer.transfer_id);
        return Err(error.to_string());
    }
    let accepted = match tokio::time::timeout(Duration::from_secs(300), rx).await {
        Ok(Ok(accepted)) => accepted,
        Ok(Err(_)) => {
            pending.lock().await.remove(&offer.transfer_id);
            return Err("receiver approval was cancelled".into());
        }
        Err(_) => {
            pending.lock().await.remove(&offer.transfer_id);
            return Err("receiver approval timed out".into());
        }
    };
    if !accepted {
        write_json(stream, &ControlMessage::Decision(TransferDecision {
            transfer_id: offer.transfer_id, decision: TransferDecisionKind::Reject, session_token: None, reason_code: Some("receiver_rejected".into())
        })).await?;
        return Ok(());
    }
    let token = Uuid::new_v4().simple().to_string();
    write_json(stream, &ControlMessage::Decision(TransferDecision {
        transfer_id: offer.transfer_id.clone(), decision: TransferDecisionKind::Accept, session_token: Some(token), reason_code: None
    })).await?;

    for entry in &offer.files {
        let part = receive_dir.join(format!("{}.part", Uuid::new_v4()));
        let mut output = async_fs::File::create(&part).await.map_err(|e| e.to_string())?;
        let mut offset = 0u64;
        let mut full_hash = Sha256::new();
        while offset < entry.size_bytes {
            let request: ControlMessage = tokio::time::timeout(Duration::from_secs(60), read_json(stream)).await.map_err(|_| "chunk request timed out")??;
            let chunk = match request { ControlMessage::ChunkRequest(c) => c, _ => return Err("expected chunk request".into()) };
            if chunk.transfer_id != offer.transfer_id || chunk.file_id != entry.file_id || chunk.offset_bytes != offset ||
                chunk.length_bytes == 0 || chunk.length_bytes as u64 > protocol::MAX_CHUNK_BYTES as u64 ||
                chunk.length_bytes as u64 > entry.size_bytes - offset {
                return Err("invalid chunk request".into());
            }
            let mut bytes = vec![0u8; chunk.length_bytes as usize];
            tokio::time::timeout(Duration::from_secs(60), stream.read_exact(&mut bytes)).await.map_err(|_| "chunk payload timed out")?.map_err(|e| e.to_string())?;
            let digest = hex::encode(Sha256::digest(&bytes));
            if digest != chunk.chunk_sha256 { return Err("chunk checksum mismatch".into()); }
            output.write_all(&bytes).await.map_err(|e| e.to_string())?;
            full_hash.update(&bytes);
            offset += bytes.len() as u64;
            write_json(stream, &ControlMessage::ChunkAcknowledgement(crate::protocol::ChunkAcknowledgement {
                transfer_id: offer.transfer_id.clone(), file_id: entry.file_id.clone(), next_offset_bytes: offset, chunk_sha256: digest
            })).await?;
        }
        output.flush().await.map_err(|e| e.to_string())?;
        drop(output);
        let checksum = hex::encode(full_hash.finalize());
        if entry.sha256.as_ref().is_some_and(|expected| expected != &checksum) {
            let _ = async_fs::remove_file(&part).await;
            return Err("file checksum mismatch".into());
        }
        let mut destination = receive_dir.join(safe_name(&entry.file_name));
        if destination.exists() {
            let stem = destination.file_stem().and_then(|s| s.to_str()).unwrap_or("received_file");
            let ext = destination.extension().and_then(|s| s.to_str()).unwrap_or("");
            let name = if ext.is_empty() { format!("{stem}-{}.received", &Uuid::new_v4().simple().to_string()[..8]) }
                else { format!("{stem}-{}.{}", &Uuid::new_v4().simple().to_string()[..8], ext) };
            destination = receive_dir.join(name);
        }
        async_fs::rename(&part, &destination).await.map_err(|e| e.to_string())?;
    }
    write_json(stream, &ControlMessage::State { transfer_id: offer.transfer_id, state: protocol::TransferState::Complete }).await?;
    Ok(())
}

#[tauri::command]
pub async fn send_files(app: AppHandle, address: String, port: u16, expected_fingerprint: String, files: Vec<String>, display_name: Option<String>) -> Result<(), String> {
    if files.is_empty() || files.len() > protocol::MAX_MANIFEST_FILES { return Err("invalid file selection".into()); }
    let config_dir = app.path().app_data_dir().map_err(|e| e.to_string())?;
    let local_identity = load_identity(&config_dir.join("identity"))?;
    let sender_fingerprint = local_identity.fingerprint.clone();
    let mut entries = Vec::with_capacity(files.len());
    let mut total = 0u64;
    for source in &files {
        let path = PathBuf::from(source);
        let metadata = fs::metadata(&path).map_err(|e| format!("source file unavailable: {e}"))?;
        if !metadata.is_file() { return Err("only regular files are supported in this transfer milestone".into()); }
        total = total.checked_add(metadata.len()).ok_or("transfer size overflow")?;
        entries.push(FileManifestEntry {
            file_id: Uuid::new_v4().to_string(),
            file_name: path.file_name().and_then(|n| n.to_str()).unwrap_or("file").to_string(),
            size_bytes: metadata.len(), mime_type: None, sha256: Some(file_sha256(&path)?), relative_path: None
        });
    }
    if total > MAX_TRANSFER_BYTES { return Err("transfer exceeds the 100 GiB safety limit".into()); }
    let offer = TransferOffer {
        protocol_version: protocol::PROTOCOL_VERSION,
        transfer_id: Uuid::new_v4().to_string(),
        sender: DeviceIdentity {
            device_id: sender_fingerprint[..16].to_string(),
            display_name: display_name.unwrap_or_else(|| "Plazma S PC".into()),
            platform: if cfg!(target_os = "windows") { DevicePlatform::Windows } else { DevicePlatform::Other },
            certificate_fingerprint: sender_fingerprint,
            protocol_min: protocol::PROTOCOL_VERSION,
            protocol_max: protocol::PROTOCOL_VERSION,
        },
        capabilities: PeerCapabilities { max_chunk_bytes: CHUNK_BYTES as u32, supports_resume: false, supports_folder_manifest: false },
        files: entries,
        total_bytes: total,
    };
    let socket = tokio::net::TcpStream::connect((address.as_str(), port)).await.map_err(|e| format!("connect to peer: {e}"))?;
    let config = tls_client_config(&expected_fingerprint)?;
    let connector = TlsConnector::from(config);
    let server_name = ServerName::try_from("plazma-s.local".to_string()).map_err(|e| e.to_string())?;
    let mut stream = connector.connect(server_name, socket).await.map_err(|e| format!("TLS pairing failed: {e}"))?;
    write_json(&mut stream, &ControlMessage::Offer(offer.clone())).await?;
    let decision: ControlMessage = read_json(&mut stream).await?;
    match decision {
        ControlMessage::Decision(d) if d.transfer_id == offer.transfer_id && d.decision == TransferDecisionKind::Accept && d.session_token.is_some() => {}
        ControlMessage::Decision(d) if d.decision == TransferDecisionKind::Reject => return Err("receiver rejected the transfer".into()),
        _ => return Err("invalid receiver decision".into()),
    }
    for (index, entry) in offer.files.iter().enumerate() {
        let mut input = async_fs::File::open(&files[index]).await.map_err(|e| e.to_string())?;
        let mut offset = 0u64;
        while offset < entry.size_bytes {
            let remaining = (entry.size_bytes - offset).min(CHUNK_BYTES as u64) as usize;
            let mut bytes = vec![0u8; remaining];
            input.read_exact(&mut bytes).await.map_err(|e| e.to_string())?;
            let chunk_hash = hex::encode(Sha256::digest(&bytes));
            let request = crate::protocol::ChunkRequest {
                transfer_id: offer.transfer_id.clone(), file_id: entry.file_id.clone(), offset_bytes: offset,
                length_bytes: bytes.len() as u32, chunk_sha256: chunk_hash.clone()
            };
            write_json(&mut stream, &ControlMessage::ChunkRequest(request)).await?;
            stream.write_all(&bytes).await.map_err(|e| e.to_string())?;
            let ack: ControlMessage = read_json(&mut stream).await?;
            match ack {
                ControlMessage::ChunkAcknowledgement(a) if a.transfer_id == offer.transfer_id && a.file_id == entry.file_id &&
                    a.next_offset_bytes == offset + bytes.len() as u64 && a.chunk_sha256 == chunk_hash => {}
                _ => return Err("receiver chunk acknowledgement mismatch".into()),
            }
            offset += bytes.len() as u64;
        }
    }
    match read_json::<_, ControlMessage>(&mut stream).await? {
        ControlMessage::State { transfer_id, state } if transfer_id == offer.transfer_id && state == protocol::TransferState::Complete => Ok(()),
        _ => Err("receiver did not confirm completed transfer".into()),
    }
}
