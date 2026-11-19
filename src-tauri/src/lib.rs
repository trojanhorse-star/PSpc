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
    authenticated: bool,
}

#[tauri::command]
fn get_app_status() -> AppStatus {
    AppStatus {
        app: "Plazma S PC",
        version: env!("CARGO_PKG_VERSION"),
        platform: if cfg!(target_os = "windows") {
            "Windows"
        } else if cfg!(target_os = "macos") {
            "macOS"
        } else if cfg!(target_os = "linux") {
            "Linux"
        } else {
            "Unknown platform"
        },
        transfer_engine: "Protocol foundation",
        protocol_version: protocol::PROTOCOL_VERSION,
        discovery: "mDNS browse available; no secure listener advertised yet",
    }
}

/// Discovers Plazma S peers on the local multicast network.
///
/// Discovery metadata is untrusted. Results are only connection candidates;
/// the TLS identity/pairing handshake must authenticate a peer before use.
#[tauri::command]
fn discover_peers() -> Result<Vec<DiscoveredPeer>, String> {
    let daemon = ServiceDaemon::new().map_err(|error| format!("mDNS unavailable: {error}"))?;
    let receiver = daemon
        .browse(PLAZMA_SERVICE_TYPE)
        .map_err(|error| format!("Could not start mDNS browse: {error}"))?;

    let deadline = Instant::now() + DISCOVERY_WINDOW;
    let mut peers = Vec::new();
    let mut seen = BTreeSet::new();

    while Instant::now() < deadline {
        let remaining = deadline.saturating_duration_since(Instant::now());
        match receiver.recv_timeout(remaining.min(Duration::from_millis(250))) {
            Ok(ServiceEvent::ServiceResolved(service)) => {
                let app = service.get_property_val_str("app").unwrap_or_default();
                let version = service
                    .get_property_val_str("protocolVersion")
                    .and_then(|value| value.parse::<u16>().ok());
                let platform = service.get_property_val_str("platform").unwrap_or("Unknown");
                if app != "plazma-s" || version != Some(protocol::PROTOCOL_VERSION) {
                    continue;
                }

                let addresses: Vec<String> = service
                    .get_addresses_v4()
                    .into_iter()
                    .map(|address| address.to_string())
                    .collect();
                if addresses.is_empty() || !seen.insert(service.get_fullname().to_owned()) {
                    continue;
                }

                peers.push(DiscoveredPeer {
                    instance_name: service.get_fullname().to_owned(),
                    platform: platform.to_owned(),
                    addresses,
                    port: service.get_port(),
                    protocol_version: version.unwrap_or_default(),
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

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![get_app_status, discover_peers])
        .run(tauri::generate_context!())
        .expect("error while running Plazma S PC");
}
