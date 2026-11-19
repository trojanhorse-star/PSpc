import {useEffect,useState} from "react";
import {invoke} from "@tauri-apps/api/core";
import {listen} from "@tauri-apps/api/event";

type Peer={instanceName:string;platform:string;addresses:string[];port:number;protocolVersion:number;fingerprint?:string|null;authenticated:boolean};
type Receiver={listening:boolean;port:number;fingerprint:string};
type Offer={transferId:string;senderName:string;senderPlatform:string;fileCount:number;totalBytes:number;fileNames:string[]};

export default function TransferPanel(){
 const [peers,setPeers]=useState<Peer[]>([]);
 const [receiver,setReceiver]=useState<Receiver|null>(null);
 const [address,setAddress]=useState("");
 const [port,setPort]=useState("53317");
 const [fingerprint,setFingerprint]=useState("");
 const [paths,setPaths]=useState("");
 const [pending,setPending]=useState<Offer|null>(null);
 const [message,setMessage]=useState("");
 const [busy,setBusy]=useState(false);
 useEffect(()=>{let unlisten:(()=>void)|undefined;listen<Offer>("plazma-transfer-offer",event=>setPending(event.payload)).then(fn=>unlisten=fn);return()=>unlisten?.();},[]);
 const discover=async()=>{setBusy(true);setMessage("");try{const found=await invoke<Peer[]>("discover_peers");setPeers(found);setMessage(found.length?"Found "+found.length+" Plazma S device(s). Verify the fingerprint before connecting.":"No Plazma S devices found. Start the receiver on the other PC or enter its IP manually.");}catch(e){setMessage(String(e));}finally{setBusy(false);}};
 const start=async()=>{setBusy(true);try{const result=await invoke<Receiver>("start_receiver",{port:Number(port)||53317});setReceiver(result);setMessage("Secure receiver is listening. Share this certificate fingerprint with the sending PC.");}catch(e){setMessage(String(e));}finally{setBusy(false);}};
 const stop=async()=>{try{await invoke("stop_receiver");setReceiver(null);setMessage("Receiver stopped; local service advertisement withdrawn.");}catch(e){setMessage(String(e));}};
 const send=async()=>{const selected=paths.split(/\r?\n/).map(s=>s.trim()).filter(Boolean);if(!address||!fingerprint||!selected.length){setMessage("Enter the destination IP, verified 64-character fingerprint and at least one full file path.");return;}setBusy(true);try{await invoke("send_files",{address,port:Number(port)||53317,expectedFingerprint:fingerprint.trim().replaceAll(":","").toLowerCase(),files:selected,displayName:"Plazma S PC"});setMessage("Receiver confirmed the transfer and verified the files.");}catch(e){setMessage(String(e));}finally{setBusy(false);}};
 const respond=async(accept:boolean)=>{if(!pending)return;try{await invoke("respond_to_transfer_offer",{transferId:pending.transferId,accept});setPending(null);setMessage(accept?"Transfer approved. The sender can now stream the files.":"Transfer declined.");}catch(e){setMessage(String(e));}};
 return <section className="transfer-panel">
  <div className="transfer-panel-head"><div><small>LOCAL NETWORK · TLS 1.3</small><h2>PC to PC transfer</h2><p>Start receiving, discover another PC, or connect using its IP address and verified certificate fingerprint.</p></div><button onClick={discover} disabled={busy}>Discover PCs</button></div>
  <div className="transfer-columns">
   <article className="transfer-card"><h3>Receive on this PC</h3><p>Incoming transfers wait for your approval. Received files are saved in Plazma S PC's application data folder under <b>Received</b>.</p>{receiver?<><div className="transfer-state">Listening on port {receiver.port}</div><label>Device certificate fingerprint</label><code>{receiver.fingerprint}</code><button className="transfer-secondary" onClick={stop}>Stop receiver</button></>:<button onClick={start} disabled={busy}>Start secure receiver</button>}
    {pending&&<div className="incoming-offer"><b>Incoming transfer request</b><p>{pending.senderName} · {pending.senderPlatform}</p><p>{pending.fileCount} file(s) · {pending.totalBytes.toLocaleString()} bytes</p><ul>{pending.fileNames.slice(0,5).map(name=><li key={name}>{name}</li>)}</ul><div className="offer-actions"><button onClick={()=>respond(true)}>Approve</button><button className="transfer-secondary" onClick={()=>respond(false)}>Decline</button></div></div>}
   </article>
   <article className="transfer-card"><h3>Send to another PC</h3><label>Discovered devices</label><div className="peer-list">{peers.map(peer=><button key={peer.instanceName} className="peer-choice" onClick={()=>{setAddress(peer.addresses[0]??"");setPort(String(peer.port));setFingerprint(peer.fingerprint??"");}}><b>{peer.instanceName}</b><span>{peer.platform} · {peer.addresses.join(",")}:{peer.port}</span><small>{peer.fingerprint??"No fingerprint announced"} · Not authenticated</small></button>)}</div><label>Destination IP address</label><input value={address} onChange={e=>setAddress(e.target.value)} placeholder="192.168.1.25"/><label>Port</label><input value={port} onChange={e=>setPort(e.target.value)} inputMode="numeric"/><label>Expected certificate fingerprint (SHA-256)</label><input value={fingerprint} onChange={e=>setFingerprint(e.target.value)} placeholder="64 hexadecimal characters"/><label>Full file paths (one per line)</label><textarea value={paths} onChange={e=>setPaths(e.target.value)} placeholder={"C:\\Users\\You\\Documents\\file.pdf\nC:\\Users\\You\\Pictures\\photo.jpg"} rows={4}/><button onClick={send} disabled={busy}>Connect securely and send</button></article>
  </div>
  {message&&<div className="transfer-message">{message}</div>}
  <small className="transfer-footnote">Peer discovery is untrusted metadata. The sender pins the receiver's TLS certificate before sending. The receiver must approve each transfer. This milestone sends regular files only; folder trees, resume and progress UI are follow-ups.</small>
 </section>;
}
