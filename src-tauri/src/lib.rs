pub mod engine;
pub mod protocol;

use mdns_sd::{ServiceDaemon, ServiceEvent};
use serde::Serialize;
use std::collections::BTreeSet;
use std::time::{Duration, Instant};

const PLAZMA_SERVICE_TYPE: &str = "_plazmas._tcp.local.";
const DISCOVERY_WINDOW: Duration = Duration::from_secs(3);

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct AppStatus {
    app: &'static str,
    version: &'static str,
    platform: &'static str,
    transfer_engine: &'static str,
    protocol_version: u16,
    discovery: &'static str,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct DiscoveredPeer {
    instance_name: String,
    platform: String,
    addresses: Vec<String>,
    port: u16,
    protocol_version: u16,
    fingerprint: Option<String>,
    authenticated: bool,
}

#[tauri::command]
fn get_app_status() -> AppStatus {
    AppStatus {
        app: "Plazma S PC",
        version: env!("CARGO_PKG_VERSION"),
        platform: if cfg!(target_os = "windows") { "Windows" }
            else if cfg!(target_os = "macos") { "macOS" }
            else if cfg!(target_os = "linux") { "Linux" } else { "Unknown platform" },
        transfer_engine: "TLS 1.3 LAN streaming",
        protocol_version: protocol::PROTOCOL_VERSION,
        discovery: "mDNS advertises only while the secure receiver is active",
    }
}

/// Discovery metadata is untrusted; a peer is not authenticated until its TLS
/// certificate fingerprint is explicitly checked by the sender.
#[tauri::command]
fn discover_peers() -> Result<Vec<DiscoveredPeer>, String> {
    let daemon = ServiceDaemon::new().map_err(|error| format!("mDNS unavailable: {error}"))?;
    let receiver = daemon.browse(PLAZMA_SERVICE_TYPE).map_err(|error| format!("Could not start mDNS browse: {error}"))?;
    let deadline = Instant::now() + DISCOVERY_WINDOW;
    let mut peers = Vec::new();
    let mut seen = BTreeSet::new();
    while Instant::now() < deadline {
        let remaining = deadline.saturating_duration_since(Instant::now());
        match receiver.recv_timeout(remaining.min(Duration::from_millis(250))) {
            Ok(ServiceEvent::ServiceResolved(service)) => {
                let app = service.get_property_val_str("app").unwrap_or_default();
                let version = service.get_property_val_str("protocolVersion").and_then(|v| v.parse::<u16>().ok());
                let fingerprint = service.get_property_val_str("fingerprint").unwrap_or_default();
                let tls = service.get_property_val_str("tls");
                if app != "plazma-s" || version != Some(protocol::PROTOCOL_VERSION) || tls != Some("1.3") ||
                    fingerprint.len() != 64 || !fingerprint.bytes().all(|b| b.is_ascii_hexdigit()) { continue; }
                let addresses: Vec<String> = service.get_addresses_v4().into_iter().map(|ip| ip.to_string()).collect();
                if addresses.is_empty() || !seen.insert(service.get_fullname().to_owned()) { continue; }
                peers.push(DiscoveredPeer {
                    instance_name: service.get_fullname().to_owned(),
                    platform: service.get_property_val_str("platform").unwrap_or("Unknown").to_owned(),
                    addresses,
                    port: service.get_port(),
                    protocol_version: version.unwrap_or_default(),
                    fingerprint: Some(fingerprint.to_owned()),
                    authenticated: false,
                });
            }
            Ok(_) => {}
            Err(mdns_sd::RecvTimeoutError::Timeout) => {}
            Err(mdns_sd::RecvTimeoutError::Disconnected) => break,
        }
    }
    let _ = daemon.stop_browse(PLAZMA_SERVICE_TYPE);
    let _ = daemon.shutdown();
    Ok(peers)
}



#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct MediaItem {
    name: String,
    path: String,
    category: String,
    extension: String,
    size_bytes: u64,
    modified_unix: Option<u64>,
}

#[tauri::command]
fn scan_media_library() -> Result<Vec<MediaItem>, String> {
    use std::{collections::VecDeque, env, fs, path::{Path, PathBuf}, time::UNIX_EPOCH};
    let home = env::var_os("USERPROFILE").or_else(|| env::var_os("HOME"))
        .map(PathBuf::from).ok_or("Could not determine the current user's home folder")?;
    let roots = [
        ("Photos", home.join("Pictures")),
        ("Videos", home.join("Videos")),
        ("Music", home.join("Music")),
    ];
    let mut items = Vec::new();
    let mut queue = VecDeque::new();
    for (category, root) in roots {
        if root.is_dir() { queue.push_back((root, category, 0usize)); }
    }
    let mut visited = 0usize;
    while let Some((folder, category, depth)) = queue.pop_front() {
        if depth > 5 || visited >= 5000 { continue; }
        let entries = match fs::read_dir(&folder) { Ok(entries) => entries, Err(_) => continue };
        for entry in entries.flatten() {
            if visited >= 5000 { break; }
            let path = entry.path();
            let metadata = match fs::symlink_metadata(&path) { Ok(m) => m, Err(_) => continue };
            if metadata.file_type().is_symlink() { continue; }
            if metadata.is_dir() {
                if depth < 5 { queue.push_back((path, category, depth + 1)); }
                continue;
            }
            if !metadata.is_file() { continue; }
            visited += 1;
            let ext = path.extension().and_then(|v| v.to_str()).unwrap_or("").to_ascii_lowercase();
            let allowed = match category {
                "Photos" => ["jpg","jpeg","png","gif","webp","bmp","tif","tiff","heic","avif"].contains(&ext.as_str()),
                "Videos" => ["mp4","m4v","mov","mkv","avi","webm","wmv","mpeg","mpg","3gp"].contains(&ext.as_str()),
                _ => ["mp3","m4a","aac","wav","flac","ogg","opus","wma","aiff"].contains(&ext.as_str()),
            };
            if !allowed { continue; }
            items.push(MediaItem {
                name: path.file_name().and_then(|v| v.to_str()).unwrap_or("Media file").to_owned(),
                path: path.to_string_lossy().into_owned(),
                category: category.to_owned(),
                extension: ext,
                size_bytes: metadata.len(),
                modified_unix: metadata.modified().ok().and_then(|t| t.duration_since(UNIX_EPOCH).ok()).map(|d| d.as_secs()),
            });
        }
    }
    items.sort_by(|a,b| b.modified_unix.cmp(&a.modified_unix).then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase())));
    Ok(items)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_process::init())
        .manage(engine::EngineState::default())
        .invoke_handler(tauri::generate_handler![
            get_app_status,
            discover_peers,
            scan_media_library,
            engine::start_receiver,
            engine::stop_receiver,
            engine::respond_to_transfer_offer,
            engine::send_files
        ])
        .run(tauri::generate_context!())
        .expect("error while running Plazma S PC");
}
