import {check} from "@tauri-apps/plugin-updater";
import {relaunch} from "@tauri-apps/plugin-process";
let started=false;
/** Check and install at startup, before the workspace or transfer engine is exposed. */
export async function runSilentStartupUpdate():Promise<void>{
 if(started)return; started=true;
 try{const update=await check();if(!update)return;await update.downloadAndInstall();await relaunch();}
 catch(error){console.error("Plazma S automatic update check failed:",error);}
}
