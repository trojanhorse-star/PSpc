use serde::Serialize;
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct AppStatus { app: &'static str, version: &'static str, platform: &'static str, transfer_engine: &'static str }
#[tauri::command]
fn get_app_status() -> AppStatus { AppStatus { app: "Plazma S PC", version: env!("CARGO_PKG_VERSION"), platform: if cfg!(target_os = "windows") { "Windows" } else if cfg!(target_os = "macos") { "macOS" } else if cfg!(target_os = "linux") { "Linux" } else { "Unknown platform" }, transfer_engine: "Foundation" } }
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() { tauri::Builder::default().invoke_handler(tauri::generate_handler![get_app_status]).run(tauri::generate_context!()).expect("error while running Plazma S PC"); }