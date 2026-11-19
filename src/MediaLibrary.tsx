import {useCallback,useEffect,useMemo,useRef,useState} from "react";
import {invoke} from "@tauri-apps/api/core";
import {open as chooseFolder} from "@tauri-apps/plugin-dialog";
import {RefreshCw,Search,FolderOpen,HardDrive,Check,FileAudio,FileVideo,FileImage,Grid2X2,List,CheckSquare,Square,Play,FolderPlus,AudioLines} from "lucide-react";

type MediaItem={name:string;path:string;category:string;extension:string;sizeBytes:number;modifiedUnix:number|null};
type Category="Photos"|"Videos"|"Music";
const categoryLabels:Record<Category,string>={Photos:"Images",Videos:"Videos",Music:"Audio"};
const formatSize=(n:number)=>n<1024?n+" B":n<1048576?(n/1024).toFixed(1)+" KB":n<1073741824?(n/1048576).toFixed(1)+" MB":(n/1073741824).toFixed(2)+" GB";
const fileIcon=(category:string)=>category==="Photos"?FileImage:category==="Videos"?FileVideo:FileAudio;

function MediaTile({item,view,chosen,onToggle}:{item:MediaItem;view:"grid"|"list";chosen:boolean;onToggle:()=>void}){
 const host=useRef<HTMLButtonElement>(null);const [thumbnail,setThumbnail]=useState("");
 useEffect(()=>{
  if(item.category==="Music")return;
  const node=host.current;if(!node)return;
  let cancelled=false;
  const load=async()=>{try{const src=await invoke<string>("get_media_thumbnail",{path:item.path});if(!cancelled)setThumbnail(src);}catch{/* retain the file-type fallback icon */}};
  const observer=new IntersectionObserver(entries=>{if(entries.some(e=>e.isIntersecting)){observer.disconnect();void load();}},{rootMargin:"220px"});
  observer.observe(node);return()=>{cancelled=true;observer.disconnect();};
 },[item.path,item.category]);
 const Icon=fileIcon(item.category);
 return <button ref={host} className={`media-item ${chosen?"selected":""} ${view}`} onClick={onToggle} aria-pressed={chosen}>
  <div className="media-art">{thumbnail?<img src={thumbnail} alt="" loading="lazy"/>:<Icon size={view==="grid"?34:22}/>}<span className="media-extension">{item.extension.toUpperCase()}</span><span className="media-check">{chosen?<Check size={16}/>:<Square size={16}/>}</span></div>
  <div className="media-item-copy"><b title={item.name}>{item.name}</b><small>{item.category} · {formatSize(item.sizeBytes)}</small></div>
 </button>;
}

export default function MediaLibrary({category}:{category:Category}){
 const [items,setItems]=useState<MediaItem[]>([]),[folders,setFolders]=useState<string[]>([]),[query,setQuery]=useState(""),[busy,setBusy]=useState(false),[error,setError]=useState(""),[message,setMessage]=useState(""),[selected,setSelected]=useState<string[]>([]),[view,setView]=useState<"grid"|"list">("grid");
 const refresh=useCallback(async()=>{setBusy(true);setError("");setMessage("");try{
  const standard=await invoke<MediaItem[]>("scan_media_library");const custom=folders.length?await invoke<MediaItem[]>("scan_media_folders",{folders}):[];
  const merged=new Map<string,MediaItem>();[...standard,...custom].forEach(item=>merged.set(item.path,item));
  setItems([...merged.values()].sort((a,b)=>(b.modifiedUnix??0)-(a.modifiedUnix??0)));setSelected([]);
 }catch(e){setError(String(e));}finally{setBusy(false);}},[folders]);
 useEffect(()=>{void refresh();},[refresh]);
 const addFolders=async()=>{try{const result=await chooseFolder({directory:true,multiple:true,title:"Add media folders from this PC"});if(!result)return;const added=Array.isArray(result)?result:[result];setFolders(current=>[...new Set([...current,...added])]);}catch(e){setError("Folder picker failed: "+String(e));}};
 const visible=useMemo(()=>items.filter(x=>x.category===category&&x.name.toLowerCase().includes(query.toLowerCase())),[items,category,query]);
 const allVisibleSelected=visible.length>0&&visible.every(x=>selected.includes(x.path));
 const toggle=(path:string)=>setSelected(current=>current.includes(path)?current.filter(x=>x!==path):[...current,path]);
 const toggleAll=()=>setSelected(current=>allVisibleSelected?current.filter(x=>!visible.some(v=>v.path===x)):[...new Set([...current,...visible.map(v=>v.path)])]);
 const openSelected=async()=>{setError("");try{for(const path of selected)await invoke("open_media_file",{path});setMessage("Opened "+selected.length+" file(s) with your PC's default media apps.");}catch(e){setError(String(e));}};
 const convertSelected=async()=>{setError("");setMessage("");const candidates=items.filter(x=>selected.includes(x.path)&&x.extension==="mp4");if(!candidates.length){setError("Select at least one MP4 video first.");return;}try{const outputs:string[]=[];for(const item of candidates)outputs.push(await invoke<string>("convert_video_to_mp3",{path:item.path}));setMessage("Converted "+outputs.length+" video(s). MP3 files were saved beside their original videos.");await refresh();}catch(e){setError(String(e));}};
 return <section className="media-library">
  <header className="media-heading"><div><small>YOUR DEVICE · LOCAL MEDIA</small><h1>{categoryLabels[category]}</h1><p>{category==="Photos"?"Browse and organise images on this PC.":category==="Videos"?"Browse and play videos stored on this PC.":"Browse audio files and music stored on this PC."} Add folders or drives to include more locations.</p></div><div className="media-head-actions"><button onClick={()=>void addFolders()} className="media-refresh"><FolderPlus size={16}/>Add folder / drive</button><button onClick={()=>void refresh()} disabled={busy} className="media-refresh"><RefreshCw size={17} className={busy?"spin":""}/>{busy?"Scanning…":"Refresh"}</button></div></header>
  {folders.length>0&&<div className="media-folder-chips"><FolderOpen size={15}/>{folders.map(folder=><button key={folder} onClick={()=>setFolders(current=>current.filter(x=>x!==folder))} title="Remove folder from library">{folder} ×</button>)}</div>}
  <div className="media-tools"><div className="media-search"><Search size={19}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder={`Search ${categoryLabels[category].toLowerCase()} on this PC`}/>{query&&<button onClick={()=>setQuery("")} aria-label="Clear search">×</button>}</div><div className="media-view-toggle"><button className={view==="grid"?"chosen":""} onClick={()=>setView("grid")} aria-label="Grid view"><Grid2X2 size={17}/></button><button className={view==="list"?"chosen":""} onClick={()=>setView("list")} aria-label="List view"><List size={18}/></button></div></div>
  <div className="media-selection"><div><button onClick={toggleAll}><CheckSquare size={16}/>{allVisibleSelected?"Clear visible":"Select all"}</button><button onClick={()=>setSelected([])}>Clear selection</button></div><span>{selected.length} selected</span></div>
  {selected.length>0&&<div className="media-action-bar"><span>{selected.length} item(s) selected</span><button onClick={()=>void openSelected()}><Play size={15}/>{category==="Photos"?"Open images":category==="Videos"?"Play videos":"Play audio"}</button>{category==="Videos"&&<button onClick={()=>void convertSelected()}><AudioLines size={15}/>Convert MP4 to MP3</button>}</div>}
  <div className="media-result-count">SHOWING {visible.length} {categoryLabels[category].toUpperCase()} · MAXIMUM 20,000 FILES PER SCAN</div>
  {error&&<div className="notice">{error}</div>}{message&&<div className="media-success">{message}</div>}
  {visible.length===0?<div className="media-empty"><FolderOpen size={34}/><b>{busy?"Scanning your media folders…":"Nothing to show yet"}</b><span>{query?"Try another search.":"No matching media was found. Add a folder or drive to scan more locations."}</span></div>:<div className={view==="grid"?"media-grid":"media-list"}>{visible.map(item=><MediaTile key={item.path} item={item} view={view} chosen={selected.includes(item.path)} onToggle={()=>toggle(item.path)}/>)}</div>}
  <footer className="media-footnote"><HardDrive size={14}/> Standard media folders plus custom folders or drive roots. Image and video previews are generated locally; video previews and MP4-to-MP3 conversion require FFmpeg in PATH. Playback opens your default PC media app.</footer>
 </section>;
}
