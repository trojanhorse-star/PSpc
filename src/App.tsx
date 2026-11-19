import { useState } from "react";
import {
  Activity, ArrowDownToLine, ArrowLeftRight, ArrowUpRight, Bell, Check, ChevronDown,
  CircleHelp, Cloud, Command, Cpu, File, FileAudio, FileImage, FileVideo, Folder,
  FolderSync, HardDrive, Home, Laptop, LockKeyhole, MoreHorizontal, Plus, Search,
  Settings, ShieldCheck, Sparkles, Upload, Wifi, Zap, Menu, X
} from "lucide-react";

type Tab = "Overview" | "Send files" | "Receive" | "Devices" | "Transfer history" | "Settings";
const nav: { label: Tab; icon: typeof Home }[] = [
  { label: "Overview", icon: Home }, { label: "Send files", icon: Upload },
  { label: "Receive", icon: ArrowDownToLine }, { label: "Devices", icon: Laptop },
  { label: "Transfer history", icon: Activity }, { label: "Settings", icon: Settings }
];

function App() {
  const [tab, setTab] = useState<Tab>("Overview");
  const [menuOpen, setMenuOpen] = useState(false);
  const [palette, setPalette] = useState("Midnight");
  const [showPalette, setShowPalette] = useState(false);
  const [notice, setNotice] = useState("Your workspace is ready. Connect another PC to begin.");
  const [dragging, setDragging] = useState(false);

  const choose = (next: Tab) => { setTab(next); setMenuOpen(false); };
  const action = (message: string) => setNotice(message);

  return <div className="app-shell">
    <aside className={`sidebar ${menuOpen ? "sidebar-open" : ""}`}>
      <div className="brand">
        <div className="brand-mark"><span /></div>
        <div className="brand-name">Plazma <b>S</b><small>PC</small></div>
        <button className="icon-btn close-menu" onClick={() => setMenuOpen(false)}><X size={18}/></button>
      </div>
      <div className="workspace-label">WORKSPACE</div>
      <nav>{nav.map(({label, icon: Icon}) => <button key={label} onClick={() => choose(label)} className={`nav-item ${tab === label ? "active" : ""}`}><Icon size={18}/><span>{label}</span>{label === "Transfer history" && <i className="nav-dot"/>}</button>)}</nav>
      <div className="sidebar-bottom">
        <div className="secure-card"><div className="secure-icon"><ShieldCheck size={17}/></div><div><b>Private by design</b><span>Peer-to-peer transfers</span></div><Check size={15} className="secure-check"/></div>
        <div className="profile"><div className="avatar">P</div><div className="profile-copy"><b>This PC</b><span>Windows desktop</span></div><MoreHorizontal size={18}/></div>
      </div>
    </aside>

    <main className="main">
      <header className="topbar">
        <button className="icon-btn mobile-menu" onClick={() => setMenuOpen(true)}><Menu size={19}/></button>
        <div className="breadcrumb"><span>Plazma S</span><i>/</i><b>{tab}</b></div>
        <div className="top-actions"><div className="connection-pill"><span/> Local network <ChevronDown size={13}/></div><button className="icon-btn" onClick={() => action("You're all caught up — no new notifications.")}><Bell size={18}/></button><div className="top-avatar">P</div></div>
      </header>

      <div className="content">
        <div className="welcome-row"><div><div className="eyebrow"><Sparkles size={13}/> YOUR PERSONAL TRANSFER HUB</div><h1>{tab === "Overview" ? "Move things. Simply." : tab}</h1><p>{tab === "Overview" ? "Everything you need to move files between your PCs, in one calm place." : pageSubtitle(tab)}</p></div><button className="help-btn" onClick={() => action("Help centre will be available in a future release.")}><CircleHelp size={16}/> Help centre</button></div>

        {tab === "Overview" ? <>
          <section className="hero-card">
            <div className="hero-glow glow-one"/><div className="hero-glow glow-two"/>
            <div className="hero-copy"><div className="hero-chip"><span className="pulse"/> READY WHEN YOU ARE</div><h2>Your devices.<br/><em>In sync.</em></h2><p>Send files directly between computers with a connection designed around your privacy.</p><div className="hero-buttons"><button className="primary-btn" onClick={() => choose("Send files")}><Upload size={17}/> Send files <ArrowUpRight size={15}/></button><button className="secondary-btn" onClick={() => choose("Devices")}><Plus size={17}/> Connect a PC</button></div></div>
            <div className="hero-art"><div className="orbit orbit-a"/><div className="orbit orbit-b"/><div className="orbit-core"><div className="core-symbol"><ArrowLeftRight size={35}/></div></div><div className="float-device device-a"><Laptop size={22}/><span>THIS PC</span><i/></div><div className="float-device device-b"><Laptop size={22}/><span>NEW PC</span><i/></div><div className="float-bit bit-a"><File size={16}/></div><div className="float-bit bit-b"><FileImage size={16}/></div><div className="float-bit bit-c"><FileVideo size={16}/></div></div>
            <div className="hero-foot"><span><LockKeyhole size={13}/> Encrypted connection</span><span><Zap size={13}/> Direct transfer</span><span><ShieldCheck size={13}/> File verification</span></div>
          </section>

          <div className="section-heading"><div><h3>Your overview</h3><p>A quick look at your transfer space</p></div><button className="text-btn" onClick={() => choose("Transfer history")}>View activity <ArrowUpRight size={14}/></button></div>
          <div className="stats-grid">
            <div className="stat-card"><div className="stat-top"><span className="stat-icon violet"><ArrowLeftRight size={17}/></span><span className="stat-kicker">ALL TIME</span></div><strong>0 <small>transfers</small></strong><div className="stat-foot">Your transfer journey starts here</div></div>
            <div className="stat-card"><div className="stat-top"><span className="stat-icon cyan"><HardDrive size={17}/></span><span className="stat-kicker">TOTAL MOVED</span></div><strong>0 <small>MB</small></strong><div className="stat-foot">Files safely delivered</div></div>
            <div className="stat-card"><div className="stat-top"><span className="stat-icon green"><ShieldCheck size={17}/></span><span className="stat-kicker">PROTECTION</span></div><strong>On <small>by default</small></strong><div className="stat-foot"><span className="green-text"><Check size={12}/> Verification planned</span></div></div>
          </div>

          <div className="lower-grid">
            <section className="panel device-panel"><div className="panel-heading"><div><h3>Connected devices</h3><p>Your trusted computers</p></div><button className="mini-link" onClick={() => choose("Devices")}>See all <ArrowUpRight size={13}/></button></div><div className="empty-device"><div className="device-illustration"><Laptop size={26}/><span><Wifi size={13}/></span></div><div><b>It's quiet in here</b><p>No other PCs connected yet. Pair a device to get moving.</p></div><button className="outline-btn" onClick={() => choose("Devices")}><Plus size={15}/> Add device</button></div></section>
            <section className="panel quick-panel"><div className="panel-heading"><div><h3>Quick actions</h3><p>Jump straight into it</p></div><div className="quick-spark"><Sparkles size={17}/></div></div><button className="quick-action" onClick={() => choose("Send files")}><span className="quick-icon violet"><Upload size={17}/></span><span><b>Send files</b><small>Choose files to transfer</small></span><ArrowUpRight size={15}/></button><button className="quick-action" onClick={() => choose("Receive")}><span className="quick-icon cyan"><ArrowDownToLine size={17}/></span><span><b>Receive files</b><small>Get files from another PC</small></span><ArrowUpRight size={15}/></button><button className="quick-action" onClick={() => choose("Settings")}><span className="quick-icon green"><FolderSync size={17}/></span><span><b>Folder sync</b><small>Set up optional sync</small></span><ArrowUpRight size={15}/></button></section>
          </div>
        </> : <section className="panel page-panel">
          {tab === "Send files" && <><div className="dropzone" onDragOver={e => {e.preventDefault();setDragging(true)}} onDragLeave={() => setDragging(false)} onDrop={e => {e.preventDefault();setDragging(false);action("File selection from drag and drop will be enabled in the desktop build.")}}><div className={`drop-icon ${dragging ? "drop-active" : ""}`}><Upload size={25}/></div><h2>Drop files to get started</h2><p>or browse your computer to choose what you'd like to send</p><button className="primary-btn" onClick={() => action("Native file picker will be connected in the Tauri desktop integration.")}><Plus size={16}/> Choose files</button><small>Any file type · Large files supported in the transfer engine milestone</small></div><div className="info-strip"><LockKeyhole size={17}/><span><b>Your files stay yours.</b> Transfers will use an encrypted direct connection between paired PCs.</span></div></>}
          {tab === "Receive" && <div className="center-state"><div className="state-icon cyan"><ArrowDownToLine size={28}/></div><h2>Ready to receive</h2><p>Your PC will appear to paired devices when the receiving service is enabled.</p><div className="receive-code"><span>PAIRING STATUS</span><b>Not connected</b><small>Device discovery is coming in the connection milestone.</small></div><button className="outline-btn" onClick={() => choose("Devices")}><Plus size={15}/> Pair another PC</button></div>}
          {tab === "Devices" && <div className="center-state"><div className="state-icon violet"><Laptop size={28}/></div><h2>Bring your PCs together</h2><p>Pair trusted Windows computers on your local network. You stay in control of every connection.</p><div className="device-steps"><div><span>01</span><b>Discover</b><small>Find PCs on your network</small></div><div><span>02</span><b>Verify</b><small>Confirm the pairing code</small></div><div><span>03</span><b>Connect</b><small>Approve the secure link</small></div></div><button className="primary-btn" onClick={() => action("Device discovery and secure pairing will be implemented in Phase 2.")}><Wifi size={16}/> Find devices</button></div>}
          {tab === "Transfer history" && <div className="center-state"><div className="state-icon green"><Activity size={28}/></div><h2>Your activity, at a glance</h2><p>Completed, active, and interrupted transfers will be collected here.</p><div className="history-empty"><File size={18}/><span>No transfers yet</span></div></div>}
          {tab === "Settings" && <div className="settings-content"><div className="settings-intro"><div className="state-icon violet"><Settings size={25}/></div><div><h2>Make Plazma yours</h2><p>Shape your workspace and how your PC behaves.</p></div></div><div className="setting-row"><div><b>Colour palette</b><p>Choose a named colour theme for your workspace.</p></div><div className="palette-wrap"><button className="select-btn" onClick={() => setShowPalette(!showPalette)}>{palette}<ChevronDown size={14}/></button>{showPalette && <div className="palette-menu">{["Midnight","Aurora","Ocean","Graphite","Rose"].map(p => <button key={p} onClick={() => {setPalette(p);setShowPalette(false);action(`${p} palette selected for the preview.`)}}>{p}{palette === p && <Check size={14}/>}</button>)}</div>}</div></div>{[["Windows startup","Launch Plazma S when you sign in","Enabled"],["Folder synchronisation","Keep selected folders in sync across PCs","Optional"],["Automatic saving","Save received files to your laptop","Enabled"],["Transfer verification","Verify file integrity after every transfer","Planned"],["System tray","Keep Plazma S available in the background","Planned"],["Notifications","Get updates on transfer progress","Planned"]].map(([name,desc,status]) => <div className="setting-row" key={name}><div><b>{name}</b><p>{desc}</p></div><span className={`setting-status ${status === "Enabled" ? "status-on" : ""}`}>{status}</span></div>)}</div>}
        </section>}
        <div className="toast"><span className="toast-dot"/>{notice}</div>
        <footer><span>PLAZMA S <b>PC</b> <i>·</i> BUILDING THE BETTER WAY TO MOVE</span><span><span className="footer-status"/> Foundation preview <i>·</i> v0.1.0</span></footer>
      </div>
    </main>
  </div>;
}

function pageSubtitle(tab: Tab) {
  const copy: Record<Tab,string> = {
    "Overview":"Your transfer space, at a glance.",
    "Send files":"Choose what you want to move to another PC.",
    "Receive":"Your PC is ready for incoming transfers.",
    "Devices":"Manage the computers you trust.",
    "Transfer history":"Every transfer, neatly accounted for.",
    "Settings":"A workspace that works your way."
  };
  return copy[tab];
}
export default App;