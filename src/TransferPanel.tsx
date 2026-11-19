import {useEffect,useState} from "react";
import {invoke} from "@tauri-apps/api/core";
import {open as chooseFiles} from "@tauri-apps/plugin-dialog";
import {listen} from "@tauri-apps/api/event";
import {Laptop,Monitor,RefreshCw,Send,ShieldCheck,Wifi} from "lucide-react";

type Peer={instanceName:string;platform:string;addresses:string[];port:number;protocolVersion:number;fingerprint?:string|null;authenticated:boolean};
type Receiver={listening:boolean;port:number;fingerprint:string};
type Offer={transferId:string;senderName:string;senderPlatform:string;fileCount:number;totalBytes:number;fileNames:string[]};

export default function TransferPanel(){
 const [peers,setPeers]=useState<Peer[]>([]);
 const [selected,setSelected]=useState<Peer|null>(null);
 const [receiver,setReceiver]=useState<Receiver|null>(null);
 const [paths,setPaths]=useState("");
 const [pending,setPending]=useState<Offer|null>(null);
 const [message,setMessage]=useState("");
 const [busy,setBusy]=useState(false);
 const [mode,setMode]=useState<"chooser"|"pc-pc"|"pc-android">("chooser");
 useEffect(()=>{let unlisten:(()=>void)|undefined;listen<Offer>("plazma-transfer-offer",event=>setPending(event.payload)).then(fn=>unlisten=fn);return()=>unlisten?.();},[]);
 const prepareNearby=async()=>{
  setBusy(true);setMessage("");
  try{
   try{const ready=await invoke<Receiver>("start_receiver",{port:53317});setReceiver(ready);}
   catch(error){const detail=String(error);if(!detail.toLowerCase().includes("already")&&!detail.toLowerCase().includes("running"))throw error;}
   const found=await invoke<Peer[]>("discover_peers");setPeers(found);setSelected(null);
   setMessage(found.length?"Nearby Plazma S PCs found. Select a computer to continue.":"No Plazma S PCs found yet. Check that both computers are on the same Wi-Fi and Plazma S is open on both.");
  }catch(error){setMessage("Could not search the local network: "+String(error));}
  finally{setBusy(false);}
 };
 const send=async()=>{
  if(!selected){setMessage("Choose a nearby PC first.");return;}
  const files=paths.split(/\r?\n/).map(s=>s.trim()).filter(Boolean);
  if(!files.length){setMessage("Add at least one file path to send.");return;}
  if(!selected.addresses[0]||!selected.fingerprint){setMessage("This PC did not provide the secure connection details required to send.");return;}
  setBusy(true);
  try{await invoke("send_files",{address:selected.addresses[0],port:selected.port,expectedFingerprint:selected.fingerprint,files,displayName:"Plazma S PC"});setMessage("The receiving PC approved the transfer and verified the files.");}
  catch(error){setMessage(String(error));}
  finally{setBusy(false);}
 };
 const respond=async(accept:boolean)=>{if(!pending)return;try{await invoke("respond_to_transfer_offer",{transferId:pending.transferId,accept});setPending(null);setMessage(accept?"Transfer approved.":"Transfer declined.");}catch(error){setMessage(String(error));}};
 const pickFiles=async()=>{try{const chosen=await chooseFiles({multiple:true,directory:false,title:"Choose files to send"});if(!chosen)return;const additions=Array.isArray(chosen)?chosen:[chosen];setPaths(current=>[...new Set([...current.split(/\\r?\\n/).filter(Boolean),...additions])].join("\\n"));}catch(error){setMessage("Could not open the file picker: "+String(error));}};
 const removePath=(path:string)=>setPaths(current=>current.split(/\\r?\\n/).filter(item=>item!==path).join("\\n"));
 if(mode==="chooser")return <section className="transfer-panel">
  <div className="transfer-panel-head"><div><small>PLAZMA S · TRANSFER HUB</small><h2>What are you connecting to?</h2><p>Choose a transfer route. Devices on your local network can find each other automatically.</p></div></div>
  <div className="transfer-columns">
   <article className="transfer-card"><h3>PC ↔ PC</h3><p>Send and receive files, folders, media and supported application packages between computers.</p><button onClick={()=>setMode("pc-pc")}>Find nearby PCs</button></article>
   <article className="transfer-card"><h3>PC → Android</h3><p>Send files and media to Android. App packages and app installation are not allowed on this route.</p><button onClick={()=>setMode("pc-android")}>Continue to Android</button></article>
  </div>
 </section>;
 if(mode==="pc-android")return <section className="transfer-panel"><div className="transfer-panel-head"><div><small>FILES & MEDIA ONLY</small><h2>PC → Android</h2><p>Android app packages are not permitted on this route.</p></div><button className="transfer-secondary" onClick={()=>setMode("chooser")}>Back to transfer modes</button></div><article className="transfer-card"><h3>Android receiving connection is being built</h3><p>This route will use nearby-device discovery and approval on the Android phone. It will not ask users to type network addresses.</p><ul><li>Files, photos, videos and music</li><li>APK/APKS/AAB/XAPK blocked</li><li>No app installation or app sharing</li></ul></article></section>;
 return <section className="transfer-panel">
  <div className="transfer-panel-head"><div><small><Wifi size={13}/> SAME WI-FI · AUTOMATIC DISCOVERY</small><h2>Send to a nearby PC</h2><p>Keep both computers on the same Wi-Fi and open Plazma S. We'll look for each other.</p></div><button onClick={prepareNearby} disabled={busy}><RefreshCw size={15}/> {busy?"Searching…":"Find nearby PCs"}</button></div>
  <div className="transfer-columns">
   <article className="transfer-card"><h3><Monitor size={18}/> Nearby computers</h3><p>{receiver?"This PC is ready to receive. Incoming transfers always need your approval.":"Find nearby computers to make this PC ready to receive too."}</p>
    {!peers.length?<div className="nearby-empty"><Laptop size={30}/><b>No computers selected</b><span>Tap “Find nearby PCs” to search your Wi-Fi network.</span></div>:<div className="peer-list">{peers.map(peer=><button key={peer.instanceName} className={"peer-choice"+(selected?.instanceName===peer.instanceName?" selected":"")} onClick={()=>setSelected(peer)}><Monitor size={19}/><span><b>{peer.instanceName.replace(/\.(_plazmas\._tcp\.local\.)?$/,"")}</b><small>{peer.platform} · On your local network</small></span><span>{selected?.instanceName===peer.instanceName?"Selected":"Choose"}</span></button>)}</div>}
    {pending&&<div className="incoming-offer"><b>Incoming transfer request</b><p>{pending.senderName} wants to send {pending.fileCount} file(s) · {pending.totalBytes.toLocaleString()} bytes.</p><ul>{pending.fileNames.slice(0,5).map(name=><li key={name}>{name}</li>)}</ul><div className="offer-actions"><button onClick={()=>respond(true)}>Accept files</button><button className="transfer-secondary" onClick={()=>respond(false)}>Decline</button></div></div>}
   </article>
   <article className="transfer-card"><h3><Send size={18}/> Send files</h3>{selected?<div className="selected-device"><Monitor size={18}/><span><b>{selected.instanceName.replace(/\.(_plazmas\._tcp\.local\.)?$/,"")}</b><small>Secure connection details handled automatically</small></span></div>:<p>First choose a computer from the nearby list.</p>}
    <label>Files to send</label><button className="transfer-secondary" onClick={pickFiles} disabled={busy}>Choose files…</button>{paths.split(/\\r?\\n/).filter(Boolean).length>0&&<div className="transfer-files-list">{paths.split(/\\r?\\n/).filter(Boolean).map(path=><div key={path}><span title={path}>{path.split(/[\\\\/]/).pop()}</span><button className="transfer-secondary" onClick={()=>removePath(path)} disabled={busy}>Remove</button></div>)}</div>}
    <button onClick={send} disabled={busy||!selected||!paths.trim()}><Send size={15}/> {busy?"Please wait…":"Connect and send"}</button>
    <div className="security-note"><ShieldCheck size={16}/><span>Encrypted connection. The other PC must approve before files are saved.</span></div>
   </article>
  </div>
  {message&&<div className="transfer-message">{message}</div>}
  <div className="transfer-footnote">Tip: both PCs must be awake, running Plazma S, and connected to the same Wi-Fi network. Some guest Wi-Fi networks block device discovery.</div>
 </section>;
}
