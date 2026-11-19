import {ArrowDownToLine,CheckCircle2,Clock3,RefreshCw,ShieldCheck,Sparkles} from "lucide-react";

export default function UpdateCentre({version}:{version:string}){
 return <section className="update-centre">
  <header className="update-heading"><div><small>PLAZMA S PC · SOFTWARE UPDATES</small><h1>Update centre</h1><p>Keep Plazma S secure and up to date. New versions are delivered when they are officially published.</p></div><span className="update-status"><Clock3 size={14}/> Update service setup pending</span></header>
  <article className="update-current"><div className="update-current-icon"><CheckCircle2 size={25}/></div><div className="update-current-copy"><small>INSTALLED VERSION</small><h2>Plazma S PC <span>v{version}</span></h2><p>This is the version currently installed on this computer.</p></div><span className="update-channel">STABLE CHANNEL</span></article>
  <div className="update-grid">
   <article className="update-card"><div className="update-card-icon"><RefreshCw size={18}/></div><h3>Automatic update checks</h3><p>Plazma S will check for a newer published release and notify you when an update is available.</p><span className="update-pending">Updater connection not configured yet</span></article>
   <article className="update-card"><div className="update-card-icon"><ShieldCheck size={18}/></div><h3>Verified updates</h3><p>Updates must be signed and verified before installation. The app should never install an untrusted package.</p><span className="update-pending">Signing setup required</span></article>
   <article className="update-card"><div className="update-card-icon"><ArrowDownToLine size={18}/></div><h3>Download and install</h3><p>When an update is published, Plazma S will show its version and release notes, download it, verify it, and guide installation.</p><span className="update-pending">Installation flow pending</span></article>
  </div>
  <div className="update-release"><Sparkles size={18}/><div><b>New version notifications</b><p>Release notes, download progress, verification, and restart-to-finish updates belong here—not in the transfer workspace.</p></div></div>
 </section>;
}
