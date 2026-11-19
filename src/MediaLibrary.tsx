import {useCallback,useEffect,useMemo,useState} from "react";
import {invoke} from "@tauri-apps/api/core";
import {Image,Music2,Video,RefreshCw,Search,FolderOpen,HardDrive} from "lucide-react";

type MediaItem={name:string;path:string;category:string;extension:string;sizeBytes:number;modifiedUnix:number|null};
const filters=["All","Photos","Videos","Music"];
const formatSize=(n:number)=>n<1024?n+" B":n<1048576?(n/1024).toFixed(1)+" KB":n<1073741824?(n/1048576).toFixed(1)+" MB":(n/1073741824).toFixed(2)+" GB";
export default function MediaLibrary(){
 const [items,setItems]=useState<MediaItem[]>([]),[filter,setFilter]=useState("All"),[query,setQuery]=useState(""),[busy,setBusy]=useState(false),[error,setError]=useState("");
 const refresh=useCallback(async()=>{setBusy(true);setError("");try{setItems(await invoke<MediaItem[]>("scan_media_library"));}catch(e){setError(String(e));}finally{setBusy(false);}},[]);
 useEffect(()=>{void refresh();},[refresh]);
 const visible=useMemo(()=>items.filter(x=>(filter==="All"||x.category===filter)&&x.name.toLowerCase().includes(query.toLowerCase())),[items,filter,query]);
 const count=(c:string)=>items.filter(x=>x.category===c).length;
 const Icon=({category}:{category:string})=>category==="Photos"?<Image size={17}/>:category==="Videos"?<Video size={17}/>:<Music2 size={17}/>;
 return <section style={{marginTop:28,color:"#252a35"}}>
  <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",gap:16,flexWrap:"wrap"}}><div><div style={{fontSize:12,letterSpacing:2,color:"#89919d"}}>ON THIS PC</div><h2 style={{margin:"8px 0",fontSize:26}}>Media library</h2><p style={{margin:0,color:"#89919d"}}>Browse photos, videos and music in your Windows user folders.</p></div><button onClick={()=>void refresh()} disabled={busy} className="primary"><RefreshCw size={15}/>{busy?"Scanning…":"Refresh library"}</button></div>
  <div style={{display:"grid",gridTemplateColumns:"repeat(3,minmax(0,1fr))",gap:12,margin:"22px 0"}}>
   {filters.slice(1).map(c=><button key={c} onClick={()=>setFilter(filter===c?"All":c)} style={{textAlign:"left",padding:16,borderRadius:14,border:filter===c?"1px solid #8b72e8":"1px solid #e4e7ed",background:filter===c?"#f4f1ff":"white",color:"#303642",cursor:"pointer"}}><div style={{display:"flex",alignItems:"center",gap:8}}><Icon category={c}/><b>{c}</b></div><strong style={{display:"block",fontSize:25,marginTop:8}}>{count(c)}</strong><small style={{color:"#89919d"}}>items found</small></button>)}
  </div>
  <div style={{display:"flex",gap:12,alignItems:"center",flexWrap:"wrap",marginBottom:14}}><div className="search" style={{flex:1,minWidth:220}}><Search size={15}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search your media"/></div>{filters.map(c=><button key={c} onClick={()=>setFilter(c)} style={{border:0,borderRadius:20,padding:"8px 13px",background:filter===c?"#252a35":"#eef0f4",color:filter===c?"white":"#555d69",cursor:"pointer"}}>{c}</button>)}</div>
  {error&&<div className="notice">{error}</div>}
  <div style={{border:"1px solid #e7e9ee",borderRadius:14,overflow:"hidden",background:"white"}}>
   <div style={{padding:"13px 16px",borderBottom:"1px solid #eceef2",display:"flex",justifyContent:"space-between",color:"#77808d",fontSize:12}}><span>{visible.length} MEDIA FILES</span><span>NAME · TYPE · SIZE</span></div>
   {visible.length===0?<div style={{padding:38,textAlign:"center",color:"#89919d"}}><FolderOpen size={28}/><p>{busy?"Scanning your media folders…":"No matching media found in Pictures, Videos or Music."}</p></div>:visible.map(item=><div key={item.path} style={{padding:"12px 16px",display:"flex",alignItems:"center",gap:12,borderBottom:"1px solid #f0f1f4"}}><span style={{width:36,height:36,borderRadius:10,background:"#f1effa",display:"grid",placeItems:"center",color:"#6d5ac6"}}><Icon category={item.category}/></span><span style={{minWidth:0,flex:1}}><b style={{display:"block",overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap",fontSize:13}}>{item.name}</b><small style={{color:"#89919d"}}>{item.category} · .{item.extension}</small></span><small style={{color:"#89919d",whiteSpace:"nowrap"}}>{formatSize(item.sizeBytes)}</small></div>)}
  </div>
  <p style={{fontSize:12,color:"#89919d",marginTop:12}}><HardDrive size={13} style={{verticalAlign:"middle",marginRight:5}}/>Scans Pictures, Videos and Music under your Windows user profile (maximum 5,000 files and 5 folder levels). Other drives and custom folders are not scanned yet.</p>
 </section>;
}
