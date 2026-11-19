import {useCallback,useEffect,useMemo,useState} from "react";
import {invoke} from "@tauri-apps/api/core";
import {Image,Music2,Video,RefreshCw,Search,FolderOpen,HardDrive,Check,FileAudio,FileVideo,FileImage,Grid2X2,List,CheckSquare,Square} from "lucide-react";

type MediaItem={name:string;path:string;category:string;extension:string;sizeBytes:number;modifiedUnix:number|null};
type Category="All"|"Photos"|"Videos"|"Music";
const filters:Category[]=["All","Photos","Videos","Music"];
const formatSize=(n:number)=>n<1024?n+" B":n<1048576?(n/1024).toFixed(1)+" KB":n<1073741824?(n/1048576).toFixed(1)+" MB":(n/1073741824).toFixed(2)+" GB";
const categoryIcon=(category:string)=>category==="Photos"?Image:category==="Videos"?Video:Music2;
const fileIcon=(category:string)=>category==="Photos"?FileImage:category==="Videos"?FileVideo:FileAudio;

export default function MediaLibrary(){
 const [items,setItems]=useState<MediaItem[]>([]),[filter,setFilter]=useState<Category>("All"),[query,setQuery]=useState(""),[busy,setBusy]=useState(false),[error,setError]=useState(""),[selected,setSelected]=useState<string[]>([]),[view,setView]=useState<"grid"|"list">("grid");
 const refresh=useCallback(async()=>{setBusy(true);setError("");try{setItems(await invoke<MediaItem[]>("scan_media_library"));setSelected([]);}catch(e){setError(String(e));}finally{setBusy(false);}},[]);
 useEffect(()=>{void refresh();},[refresh]);
 const visible=useMemo(()=>items.filter(x=>(filter==="All"||x.category===filter)&&x.name.toLowerCase().includes(query.toLowerCase())),[items,filter,query]);
 const count=(c:string)=>items.filter(x=>x.category===c).length;
 const allVisibleSelected=visible.length>0&&visible.every(x=>selected.includes(x.path));
 const toggle=(path:string)=>setSelected(current=>current.includes(path)?current.filter(x=>x!==path):[...current,path]);
 const toggleAll=()=>setSelected(current=>allVisibleSelected?current.filter(x=>!visible.some(v=>v.path===x)):[...new Set([...current,...visible.map(v=>v.path)])]);
 const CategoryIcon=({category}:{category:string})=>{const Icon=categoryIcon(category);return <Icon size={19}/>};
 return <section className="media-library">
  <header className="media-heading"><div><small>YOUR DEVICE · LOCAL MEDIA</small><h1>Media library</h1><p>Browse the photos, music and videos stored on this PC. Find what you need and select items easily.</p></div><button onClick={()=>void refresh()} disabled={busy} className="media-refresh"><RefreshCw size={17} className={busy?"spin":""}/>{busy?"Scanning…":"Refresh"}</button></header>
  <nav className="media-tabs" aria-label="Media categories">
   {filters.map(c=><button key={c} onClick={()=>{setFilter(c);setSelected([]);}} className={filter===c?"media-tab active":"media-tab"}><CategoryIcon category={c==="All"?"Photos":c}/><span>{c==="Photos"?"Images":c==="All"?"All media":c}</span><b>{c==="All"?items.length:count(c)}</b></button>)}
  </nav>
  <div className="media-tools"><div className="media-search"><Search size={19}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder={`Search device ${filter==="All"?"media":filter.toLowerCase()}`}/>{query&&<button onClick={()=>setQuery("")} aria-label="Clear search">×</button>}</div><div className="media-view-toggle"><button className={view==="grid"?"chosen":""} onClick={()=>setView("grid")} aria-label="Grid view"><Grid2X2 size={17}/></button><button className={view==="list"?"chosen":""} onClick={()=>setView("list")} aria-label="List view"><List size={18}/></button></div></div>
  <div className="media-selection"><div><button onClick={toggleAll}><CheckSquare size={16}/>{allVisibleSelected?"Clear visible":"Select all"}</button><button onClick={()=>setSelected([])}>Clear selection</button></div><span>{selected.length} selected</span></div>
  <div className="media-result-count">SHOWING {visible.length} OF {items.length}</div>
  {error&&<div className="notice">{error}</div>}
  {visible.length===0?<div className="media-empty"><FolderOpen size={34}/><b>{busy?"Scanning your media folders…":"Nothing to show yet"}</b><span>{query?"Try another search.":"No matching media was found in your Pictures, Videos or Music folders."}</span></div>:<div className={view==="grid"?"media-grid":"media-list"}>
   {visible.map(item=>{const Icon=fileIcon(item.category);const chosen=selected.includes(item.path);return <button key={item.path} className={`media-item ${chosen?"selected":""} ${view}`} onClick={()=>toggle(item.path)} aria-pressed={chosen}>
    <div className="media-art"><Icon size={view==="grid"?34:22}/><span className="media-extension">{item.extension.toUpperCase()}</span><span className="media-check">{chosen?<Check size={16}/>:<Square size={16}/>}</span></div>
    <div className="media-item-copy"><b title={item.name}>{item.name}</b><small>{item.category} · {formatSize(item.sizeBytes)}</small></div>
   </button>;})}
  </div>}
  <footer className="media-footnote"><HardDrive size={14}/> Scans Pictures, Videos and Music in your Windows user folders. Custom folders, other drives, thumbnails, playback and conversion are not connected in this version.</footer>
 </section>;
}
