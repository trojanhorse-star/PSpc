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
    app: &'static str, version: &'static str, platform: &'static str,
    transfer_engine: &'static str, protocol_version: u16, discovery: &'static str,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct DiscoveredPeer {
    instance_name: String, platform: String, addresses: Vec<String>, port: u16,
    protocol_version: u16, fingerprint: Option<String>, authenticated: bool,
}
#[tauri::command]
fn get_app_status() -> AppStatus {
    AppStatus { app: "Plazma S PC", version: env!("CARGO_PKG_VERSION"),
        platform: if cfg!(target_os = "windows") { "Windows" } else if cfg!(target_os = "macos") { "macOS" } else if cfg!(target_os = "linux") { "Linux" } else { "Unknown platform" },
        transfer_engine: "TLS 1.3 LAN streaming", protocol_version: protocol::PROTOCOL_VERSION,
        discovery: "mDNS advertises only while the secure receiver is active" }
}
#[tauri::command]
fn discover_peers() -> Result<Vec<DiscoveredPeer>, String> {
    let daemon = ServiceDaemon::new().map_err(|e| format!("mDNS unavailable: {e}"))?;
    let receiver = daemon.browse(PLAZMA_SERVICE_TYPE).map_err(|e| format!("Could not start mDNS browse: {e}"))?;
    let deadline = Instant::now() + DISCOVERY_WINDOW;
    let mut peers = Vec::new(); let mut seen = BTreeSet::new();
    while Instant::now() < deadline {
        let remaining = deadline.saturating_duration_since(Instant::now());
        match receiver.recv_timeout(remaining.min(Duration::from_millis(250))) {
            Ok(ServiceEvent::ServiceResolved(service)) => {
                let app = service.get_property_val_str("app").unwrap_or_default();
                let version = service.get_property_val_str("protocolVersion").and_then(|v| v.parse::<u16>().ok());
                let fingerprint = service.get_property_val_str("fingerprint").unwrap_or_default();
                if app != "plazma-s" || version != Some(protocol::PROTOCOL_VERSION) || service.get_property_val_str("tls") != Some("1.3")
                    || fingerprint.len() != 64 || !fingerprint.bytes().all(|b| b.is_ascii_hexdigit()) { continue; }
                let addresses: Vec<String> = service.get_addresses_v4().into_iter().map(|ip| ip.to_string()).collect();
                if addresses.is_empty() || !seen.insert(service.get_fullname().to_owned()) { continue; }
                peers.push(DiscoveredPeer { instance_name: service.get_fullname().to_owned(),
                    platform: service.get_property_val_str("platform").unwrap_or("Unknown").to_owned(),
                    addresses, port: service.get_port(), protocol_version: version.unwrap_or_default(),
                    fingerprint: Some(fingerprint.to_owned()), authenticated: false });
            }
            Ok(_) | Err(mdns_sd::RecvTimeoutError::Timeout) => {}
            Err(mdns_sd::RecvTimeoutError::Disconnected) => break,
        }
    }
    let _ = daemon.stop_browse(PLAZMA_SERVICE_TYPE); let _ = daemon.shutdown(); Ok(peers)
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct MediaItem { name: String, path: String, category: String, extension: String, size_bytes: u64, modified_unix: Option<u64> }

fn media_category(ext: &str) -> Option<&'static str> {
    match ext.to_ascii_lowercase().as_str() {
        "jpg"|"jpeg"|"png"|"gif"|"webp"|"bmp"|"tif"|"tiff"|"heic"|"avif" => Some("Photos"),
        "mp4"|"m4v"|"mov"|"mkv"|"avi"|"webm"|"wmv"|"mpeg"|"mpg"|"3gp" => Some("Videos"),
        "mp3"|"m4a"|"aac"|"wav"|"flac"|"ogg"|"opus"|"wma"|"aiff" => Some("Music"),
        _ => None,
    }
}
fn scan_roots(roots: Vec<(std::path::PathBuf, Option<&'static str>)>) -> Result<Vec<MediaItem>, String> {
    use std::{collections::VecDeque, fs, time::UNIX_EPOCH};
    let mut queue = VecDeque::new();
    for (root, category) in roots {
        if !root.exists() || !root.is_dir() { return Err(format!("Folder does not exist or is not accessible: {}", root.display())); }
        queue.push_back((root, category, 0usize));
    }
    let mut items = Vec::new(); let mut visited = 0usize;
    while let Some((folder, fixed_category, depth)) = queue.pop_front() {
        if depth > 8 || visited >= 20000 { continue; }
        let entries = match fs::read_dir(&folder) { Ok(v) => v, Err(_) => continue };
        for entry in entries.flatten() {
            if visited >= 20000 { break; }
            let path = entry.path();
            let metadata = match fs::symlink_metadata(&path) { Ok(v) => v, Err(_) => continue };
            if metadata.file_type().is_symlink() { continue; }
            if metadata.is_dir() { if depth < 8 { queue.push_back((path, fixed_category, depth + 1)); } continue; }
            if !metadata.is_file() { continue; }
            visited += 1;
            let ext = path.extension().and_then(|v| v.to_str()).unwrap_or("").to_ascii_lowercase();
            let category = fixed_category.or_else(|| media_category(&ext));
            let Some(category) = category else { continue; };
            if media_category(&ext).is_none() { continue; }
            items.push(MediaItem { name: path.file_name().and_then(|v| v.to_str()).unwrap_or("Media file").to_owned(),
                path: path.to_string_lossy().into_owned(), category: category.to_owned(), extension: ext,
                size_bytes: metadata.len(), modified_unix: metadata.modified().ok().and_then(|t| t.duration_since(UNIX_EPOCH).ok()).map(|d| d.as_secs()) });
        }
    }
    items.sort_by(|a,b| b.modified_unix.cmp(&a.modified_unix).then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase())));
    Ok(items)
}
#[tauri::command]
fn scan_media_library() -> Result<Vec<MediaItem>, String> {
    use std::{env, path::PathBuf};
    let home = env::var_os("USERPROFILE").or_else(|| env::var_os("HOME")).map(PathBuf::from)
        .ok_or("Could not determine the current user's home folder")?;
    scan_roots(vec![(home.join("Pictures"),Some("Photos")),(home.join("Videos"),Some("Videos")),(home.join("Music"),Some("Music"))]
        .into_iter().filter(|(p,_)| p.is_dir()).collect())
}
#[tauri::command]
fn scan_media_folders(folders: Vec<String>) -> Result<Vec<MediaItem>, String> {
    if folders.is_empty() || folders.len() > 20 { return Err("Choose between 1 and 20 folders.".into()); }
    let roots = folders.into_iter().map(|p| (std::path::PathBuf::from(p), None)).collect();
    scan_roots(roots)
}
#[tauri::command]
fn open_media_file(path: String) -> Result<(), String> {
    use std::{fs, process::Command};
    let path = fs::canonicalize(&path).map_err(|e| format!("Cannot open file: {e}"))?;
    if !path.is_file() { return Err("The selected media file no longer exists.".into()); }
    #[cfg(target_os = "windows")]
    let result = Command::new("cmd").args(["/C","start",""]).arg(&path).spawn();
    #[cfg(target_os = "macos")]
    let result = Command::new("open").arg(&path).spawn();
    #[cfg(all(unix, not(target_os = "macos")))]
    let result = Command::new("xdg-open").arg(&path).spawn();
    result.map(|_| ()).map_err(|e| format!("Could not launch the default media player: {e}"))
}
#[tauri::command]
fn convert_video_to_mp3(path: String) -> Result<String, String> {
    use std::{fs, process::Command};
    let input = fs::canonicalize(&path).map_err(|e| format!("Cannot access selected video: {e}"))?;
    if !input.is_file() || input.extension().and_then(|v|v.to_str()).map(|v|v.eq_ignore_ascii_case("mp4")) != Some(true) {
        return Err("Select an MP4 video to convert.".into());
    }
    let output = input.with_extension("mp3");
    if output.exists() { return Err(format!("{} already exists; rename it before converting.", output.file_name().unwrap_or_default().to_string_lossy())); }
    let result = Command::new("ffmpeg").args(["-n","-i"]).arg(&input).args(["-vn","-codec:a","libmp3lame","-q:a","2"]).arg(&output).output()
        .map_err(|_| "FFmpeg was not found. Install FFmpeg and add it to PATH, then try again.".to_string())?;
    if !result.status.success() { let _ = fs::remove_file(&output); return Err(format!("FFmpeg conversion failed: {}", String::from_utf8_lossy(&result.stderr).lines().last().unwrap_or("Unknown FFmpeg error"))); }
    Ok(output.to_string_lossy().into_owned())
}
#[cfg(test)]
mod media_tests {
    use super::media_category;
    #[test] fn recognizes_supported_media_extensions() {
        assert_eq!(media_category("mp4"),Some("Videos")); assert_eq!(media_category("JPG"),Some("Photos"));
        assert_eq!(media_category("mp3"),Some("Music")); assert_eq!(media_category("exe"),None);
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_process::init())
        .plugin(tauri_plugin_dialog::init())
        .manage(engine::EngineState::default())
        .invoke_handler(tauri::generate_handler![get_app_status,discover_peers,scan_media_library,scan_media_folders,open_media_file,convert_video_to_mp3,
            engine::start_receiver,engine::stop_receiver,engine::respond_to_transfer_offer,engine::send_files])
        .run(tauri::generate_context!()).expect("error while running Plazma S PC");
}
