package com.snoop.web

// Self-contained page (no CDN): inline CSS + JS, live over SSE. Kept free of the `$` character so it
// can live in a Kotlin raw string without template-expansion escaping.
internal val INDEX_HTML: String = """
<!doctype html>
<html lang='en'>
<head>
<meta charset='utf-8'>
<meta name='viewport' content='width=device-width, initial-scale=1'>
<title>Snoop</title>
<link rel='icon' href='data:image/svg+xml,%3Csvg%20xmlns=%22http://www.w3.org/2000/svg%22%20viewBox=%220%200%2024%2024%22%20fill=%22none%22%20stroke=%22%231B1B20%22%20stroke-width=%221.8%22%20stroke-linecap=%22round%22%20stroke-linejoin=%22round%22%3E%3Ccircle%20cx=%2210.5%22%20cy=%2210.5%22%20r=%226.6%22%3E%3C/circle%3E%3Cpath%20d=%22M20.2%2020.2l-4.3-4.3%22%3E%3C/path%3E%3Cpath%20d=%22M6.9%2011.1l1.9-2.4%201.9%203.2%201.9-4.1%201.6%203.3%22%3E%3C/path%3E%3C/svg%3E'>
<style>
:root{--bg:#f7f7fb;--surface:#ffffff;--surface-var:#f1f1f6;--on-surface:#1b1b20;--on-surface-var:#6b7280;--outline:#d1d1db;--outline-var:#e6e6ee;--live:#16a34a;
--ok-bg:#dcfce7;--ok-fg:#16a34a;--info-bg:#dbeafe;--info-fg:#2563eb;--warn-bg:#fef3c7;--warn-fg:#b45309;--err-bg:#fee2e2;--err-fg:#dc2626;--neu-bg:#f3f4f6;--neu-fg:#6b7280;--ecom-bg:#d1fae5;--ecom-fg:#047857;
--jk:#1d4ed8;--js:#9a3412;--jn:#6d28d9;--jb:#047857;--jp:#6b7280;--acc-fill:var(--on-surface);--acc-on:var(--bg);}
@media (prefers-color-scheme:dark){:root{--bg:#0f0f14;--surface:#17171f;--surface-var:#20202a;--on-surface:#e9e9ef;--on-surface-var:#9aa0aa;--outline:#3a3a46;--outline-var:#26262f;--live:#22c55e;
--ok-bg:#16321f;--ok-fg:#4ade80;--info-bg:#16243d;--info-fg:#60a5fa;--warn-bg:#33270f;--warn-fg:#fbbf24;--err-bg:#3a1a1a;--err-fg:#f87171;--neu-bg:#23232b;--neu-fg:#9aa0aa;--ecom-bg:#123229;--ecom-fg:#34d399;
--jk:#60a5fa;--js:#fdba74;--jn:#a78bfa;--jb:#34d399;--jp:#9aa0aa;}}
*{box-sizing:border-box}
html,body{height:100%}
body{margin:0;display:flex;flex-direction:column;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,Roboto,sans-serif;background:var(--bg);color:var(--on-surface);font-size:14px}
.mono{font-family:ui-monospace,'JetBrains Mono',Menlo,monospace}
header{flex-shrink:0;background:var(--bg);border-bottom:1px solid var(--outline-var);padding:12px 16px;display:flex;flex-direction:column;gap:10px}
.hrow{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.hrow-top{justify-content:space-between}
.hleft{display:flex;align-items:center;gap:12px}
.actions{display:flex;align-items:center;gap:8px;flex-shrink:0}
.brand{display:flex;align-items:center;gap:8px;font-weight:600;font-size:22px;flex-shrink:0}
.dot{width:8px;height:8px;border-radius:50%;background:var(--live)}
.dot.off{background:var(--outline)}
.count{color:var(--on-surface-var);font-size:12px}
.search{flex:1;min-width:180px;height:40px;display:flex;align-items:center;gap:8px;padding:0 14px;border:1px solid var(--outline-var);border-radius:999px;background:var(--surface);color:var(--on-surface-var)}
.search svg{flex-shrink:0}
.search input{border:none;outline:none;background:transparent;color:var(--on-surface);font-size:14px;flex:1;font-family:inherit}
.chips{display:flex;gap:6px;flex-wrap:wrap;align-items:center}
.chip{display:inline-flex;align-items:center;gap:6px;padding:6px 12px;border:1px solid var(--outline-var);border-radius:999px;background:var(--surface);color:var(--on-surface);cursor:pointer;font-size:13px;font-weight:500;white-space:nowrap}
.chip.on{background:var(--acc-fill);color:var(--acc-on);border-color:transparent}
.chipdot{width:8px;height:8px;border-radius:50%;flex-shrink:0}
.iconbtn{width:36px;height:36px;display:flex;align-items:center;justify-content:center;border:1px solid var(--outline-var);border-radius:10px;background:var(--surface);color:var(--on-surface);cursor:pointer;flex-shrink:0}
.iconbtn:hover{background:var(--surface-var)}
main{flex:1;overflow:hidden;display:flex;padding:12px;gap:12px}
.list{flex:1;overflow:auto;background:var(--surface);border:1px solid var(--outline-var);border-radius:18px}
.detail{width:0;overflow:auto;background:var(--surface);transition:width .15s ease;border-radius:18px}
.detail.open{width:46%;min-width:360px;border:1px solid var(--outline-var)}
.row{display:flex;gap:12px;align-items:center;padding:12px 16px;border-bottom:1px solid var(--outline-var);cursor:pointer}
.list .row:last-child{border-bottom:none}
.row:hover{background:var(--surface-var)}
.row.sel{background:var(--surface-var)}
.time{color:var(--on-surface-var);font-size:12px;white-space:nowrap;flex-shrink:0}
.pill{padding:2px 8px;border-radius:6px;font-size:12px;font-weight:600;white-space:nowrap;display:inline-block}
.grow{flex:1;min-width:0}
.title{font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:14px}
.sub{color:var(--on-surface-var);font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;display:flex;align-items:center;gap:4px;margin-top:2px}
.right{text-align:right;display:flex;flex-direction:column;align-items:flex-end;gap:2px;flex-shrink:0}
.abox{width:28px;height:28px;border-radius:8px;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.hgroup{display:flex;align-items:center;gap:8px;padding:10px 16px;border-bottom:1px solid var(--outline-var);font-size:13px;font-weight:600;cursor:pointer}
.hgroup .chev{color:var(--on-surface-var);display:flex;transition:transform .15s ease}
.hgroup .chev.open{transform:rotate(90deg)}
.hlabel{margin-left:auto;color:var(--on-surface-var);font-weight:400;font-size:11px}
.jk{color:var(--jk)}.js{color:var(--js)}.jn{color:var(--jn)}.jb{color:var(--jb)}.jp{color:var(--jp)}
.dwrap{padding:20px}
.dheadrow{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}
.dtitle{font-weight:700;font-size:17px;word-break:break-all}
.dsub{color:var(--on-surface-var);font-size:12px;margin-top:2px}
.pillrow{display:flex;gap:8px;margin-top:12px;overflow-x:auto;padding-bottom:2px}
.tabs{display:flex;gap:20px;margin-top:16px;border-bottom:1px solid var(--outline-var)}
.tab{padding:8px 0;font-size:13px;font-weight:500;color:var(--on-surface-var);cursor:pointer;border-bottom:2px solid transparent;margin-bottom:-1px}
.tab.on{color:var(--on-surface);font-weight:700;border-bottom-color:var(--on-surface)}
.card{border:1px solid var(--outline-var);border-radius:12px;padding:12px;margin-top:16px}
.section{text-transform:uppercase;font-size:10px;letter-spacing:.4px;color:var(--on-surface-var);font-weight:600;margin:14px 0 6px}
.section:first-child{margin-top:0}
.shead{cursor:pointer;user-select:none;display:flex;align-items:center;gap:6px;margin:0 0 6px;text-transform:uppercase;font-size:11px;font-weight:600;color:var(--on-surface-var)}
.shead .chev{display:flex;transition:transform .15s ease}
.shead .chev.open{transform:rotate(90deg)}
.shrow{display:flex;align-items:center;justify-content:space-between;margin-top:16px}
.shrow .section{margin:0}
.kv{display:flex;gap:12px;padding:6px 0;border-bottom:1px solid var(--outline-var)}
.kv:last-child{border-bottom:none}
.kv .k{flex:.42;color:var(--on-surface-var)}
.kv .v{flex:.58;word-break:break-all;font-size:13px}
.kv.end .v{text-align:right}
.redact{display:inline-block;width:64px;height:14px;background:var(--on-surface);border-radius:3px;vertical-align:middle}
.code{background:var(--surface-var);border-radius:10px;padding:12px;overflow:auto;font-size:12px;white-space:pre-wrap;word-break:break-word;line-height:1.75}
.seg{display:inline-flex;background:var(--surface-var);border-radius:999px;padding:3px}
.seg span{padding:4px 12px;border-radius:999px;cursor:pointer;font-size:12px;font-weight:600;color:var(--on-surface-var)}
.seg span.on{background:var(--on-surface);color:var(--bg)}
.copybtn{display:flex;align-items:center;gap:6px;padding:6px 12px;border:1px solid var(--outline-var);border-radius:8px;background:var(--surface);color:var(--on-surface);cursor:pointer;font-size:12px;font-weight:600;white-space:nowrap}
.corr{display:flex;align-items:center;gap:10px;padding:12px 14px;border-radius:13px;background:var(--surface-var);border:1px solid var(--outline-var);cursor:pointer;margin-top:16px}
.corr .grow{font-weight:600;font-size:14px}
.empty{display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;padding:40px 20px;text-align:center}
.emptyicon{width:64px;height:64px;border-radius:50%;background:var(--surface-var);display:flex;align-items:center;justify-content:center;color:var(--on-surface-var);margin-bottom:16px}
.emptytitle{font-weight:600;font-size:16px;margin-bottom:6px}
.emptysub{color:var(--on-surface-var);font-size:13px;max-width:280px}
.toastwrap{position:fixed;left:0;right:0;bottom:24px;display:flex;justify-content:center;pointer-events:none;z-index:50}
.toast{background:var(--on-surface);color:var(--bg);padding:10px 18px;border-radius:10px;font-size:13px;font-weight:600;box-shadow:0 6px 20px rgba(0,0,0,.25);opacity:0;transform:translateY(8px);transition:opacity .2s ease,transform .2s ease}
.toast.show{opacity:1;transform:translateY(0)}
</style>
</head>
<body>
<header>
  <div class='hrow hrow-top'>
    <div class='hleft'>
      <span class='brand'><svg width='20' height='20' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><circle cx='10.5' cy='10.5' r='6.6'></circle><path d='M20.2 20.2l-4.3-4.3'></path><path d='M6.9 11.1l1.9-2.4 1.9 3.2 1.9-4.1 1.6 3.3'></path></svg>Snoop</span>
      <span id='dot' class='dot'></span>
      <span id='count' class='count mono'>0</span>
    </div>
    <div class='actions'>
      <button class='iconbtn' id='pauseToggle' title='Pause' onclick='togglePause()'><span id='pauseIcon' style='display:inline-flex'></span><span id='playIcon' style='display:none'></span></button>
      <button class='iconbtn' id='clearBtn' title='Clear' onclick='clearAll()'></button>
      <button class='iconbtn' id='exportBtn' title='Export' onclick='exportJson()'></button>
    </div>
  </div>
  <div class='hrow'>
    <div class='search'><svg width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round'><circle cx='11' cy='11' r='7'></circle><path d='M20 20l-3.2-3.2'></path></svg><input id='q' placeholder='Search url or event'></div>
    <div class='chips' id='typechips'></div>
    <div class='chips' id='chanchips'></div>
  </div>
</header>
<main>
  <div class='list' id='list'></div>
  <div class='detail' id='detail'></div>
</main>
<div class='toastwrap'><div class='toast' id='toast'></div></div>
<script>
var entries=[],latest=null,paused=false,typeFilter='all',channelFilter=null,search='',selectedId=null,collapsedGroups={},openHeaders={},detailTab='overview',rawBody=false,rawParams=false,toastTimer=null;

var CHANNEL_LIGHT=[['#DBEAFE','#1D4ED8'],['#D1FAE5','#047857'],['#FEF3C7','#B45309'],['#FEE2E2','#B91C1C'],['#EDE9FE','#6D28D9'],['#FCE7F3','#BE185D'],['#CCFBF1','#0F766E']];
var CHANNEL_DARK=[['#16243D','#60A5FA'],['#123229','#34D399'],['#33270F','#FBBF24'],['#3A1A1A','#F87171'],['#241F44','#A78BFA'],['#381626','#F472B6'],['#0F2E2A','#2DD4BF']];
var mql=window.matchMedia('(prefers-color-scheme: dark)');
var channelPalette=mql.matches?CHANNEL_DARK:CHANNEL_LIGHT;
mql.addEventListener('change',function(e){channelPalette=e.matches?CHANNEL_DARK:CHANNEL_LIGHT;render();});

var ICONS={
  search:{d:'<circle cx="11" cy="11" r="7"></circle><path d="M20 20l-3.2-3.2"></path>',w:1.6},
  chevron:{d:'<path d="M9 6l6 6-6 6"></path>',w:1.6},
  clear:{d:'<path d="M4 7h16"></path><path d="M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"></path><path d="M6 7l1 12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-12"></path>',w:1.6},
  export:{d:'<path d="M12 4v11"></path><path d="M8 11l4 4 4-4"></path><path d="M5 20h14"></path>',w:1.6},
  copy:{d:'<rect x="9" y="9" width="11" height="11" rx="2"></rect><path d="M6 15H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1"></path>',w:1.6},
  link:{d:'<path d="M9 17H7a4 4 0 0 1 0-8h1"></path><path d="M15 7h2a4 4 0 0 1 0 8h-1"></path><path d="M8 12h8"></path>',w:1.6},
  phone:{d:'<rect x="7" y="3" width="10" height="18" rx="2"></rect><path d="M11 18h2"></path>',w:1.6},
  bars:{d:'<path d="M5 20V11"></path><path d="M12 20V4"></path><path d="M19 20v-6"></path>',w:1.6},
  pause:{d:'<rect x="6" y="5" width="4" height="14" rx="1.3"></rect><rect x="14" y="5" width="4" height="14" rx="1.3"></rect>',fill:true},
  play:{d:'<path d="M7 5l12 7-12 7z"></path>',fill:true}
};
function icon(name,size){
  var ic=ICONS[name];
  if(!ic)return '';
  size=size||18;
  if(ic.fill){
    return '<svg width="'+size+'" height="'+size+'" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">'+ic.d+'</svg>';
  }
  return '<svg width="'+size+'" height="'+size+'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="'+ic.w+'" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg">'+ic.d+'</svg>';
}

function esc(s){s=(s==null)?'':(''+s);return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
function pad(n,l){n=''+n;while(n.length<l)n='0'+n;return n;}
function clock(ts){var d=new Date(ts);return pad(d.getHours(),2)+':'+pad(d.getMinutes(),2)+':'+pad(d.getSeconds(),2)+'.'+pad(d.getMilliseconds(),3);}
function dur(ms){if(ms==null)return '';return ms<1000?(ms+' ms'):((ms/1000).toFixed(1)+' s');}

function pal(ch){var h=0;ch=ch||'';for(var i=0;i<ch.length;i++){h=(h*31+ch.charCodeAt(i))|0;}var idx=((h%channelPalette.length)+channelPalette.length)%channelPalette.length;return channelPalette[idx];}
function chanStyle(pair){return 'background:'+pair[0]+';color:'+pair[1];}
function methodTint(m){m=(m||'').toUpperCase();if(m==='GET')return 'info';if(m==='POST')return 'ok';if(m==='PUT'||m==='PATCH')return 'warn';if(m==='DELETE')return 'err';return 'neu';}
function statusTint(e){if(e.state==='failed')return 'err';if(e.state==='pending')return 'neu';var s=e.status||0;if(s<300)return 'ok';if(s<400)return 'info';if(s<500)return 'warn';return 'err';}
function tintStyle(key){return 'background:var(--'+key+'-bg);color:var(--'+key+'-fg)';}
function statusLabel(e){if(e.state==='failed')return 'ERR';if(e.state==='pending')return '...';return ''+e.status;}
function pill(text,style,mono){return "<span class='pill"+(mono?' mono':'')+"' style='"+style+"'>"+esc(text)+"</span>";}

function matches(e){
  if(typeFilter!=='all'&&e.type!==typeFilter)return false;
  if(channelFilter&&e.channel!==channelFilter)return false;
  if(search){
    var q=search.toLowerCase();
    var hay=e.type==='network'?((e.path||'')+' '+(e.host||'')+' '+(e.method||'')):((e.name||'')+' '+(e.channel||''));
    if(hay.toLowerCase().indexOf(q)<0)return false;
  }
  return true;
}

function rowHtml(e){
  if(e.type==='network'){
    return "<div class='row"+(e.id===selectedId?' sel':'')+"' onclick='sel(\""+e.id+"\")'>"+
      "<span class='time mono'>"+clock(e.ts)+"</span>"+
      pill(e.method,tintStyle(methodTint(e.method)),true)+
      "<div class='grow'><div class='title'>"+esc(e.path||'/')+"</div><div class='sub'>"+esc(e.host)+"</div></div>"+
      "<div class='right'>"+pill(statusLabel(e),tintStyle(statusTint(e)),true)+"<div class='sub mono'>"+esc(dur(e.durationMillis))+"</div></div>"+
      "</div>";
  }
  var pair=pal(e.channel);
  var sub;
  if(e.correlatedCallId){
    var call=null;for(var i=0;i<entries.length;i++){if(entries[i].id===e.correlatedCallId){call=entries[i];break;}}
    sub=icon('link',12)+esc(call?(' sent over '+call.method+' '+call.path):' sent over a call');
  }else{
    var n=(e.properties||[]).length;
    sub=n+(n===1?' param':' params');
  }
  return "<div class='row"+(e.id===selectedId?' sel':'')+"' onclick='sel(\""+e.id+"\")'>"+
    "<span class='time mono'>"+clock(e.ts)+"</span>"+
    "<div class='abox' style='"+chanStyle(pair)+"'>"+icon('bars',16)+"</div>"+
    "<div class='grow'><div class='title'>"+esc(e.name)+"</div><div class='sub'>"+sub+"</div></div>"+
    pill(e.channel,chanStyle(pair),false)+
    "</div>";
}

function emptyHtml(){
  var nothing=entries.length===0;
  var title=nothing?'Nothing captured yet':'No matches';
  var sub=nothing?'Network calls and analytics events will show up here as they happen.':'Try a different filter or search.';
  return "<div class='empty'><div class='emptyicon'>"+icon('search',28)+"</div><div class='emptytitle'>"+esc(title)+"</div><div class='emptysub'>"+esc(sub)+"</div></div>";
}

function listHtml(){
  var groups=[],screen=null,bucket=[],gkey=null;
  entries.forEach(function(e){
    if(e.type==='analytics'&&e.name==='screen_view'){
      if(bucket.length)groups.push({screen:screen,key:gkey,entries:bucket});
      var sp=(e.properties||[]).filter(function(k){return k.name==='screen';});
      screen=sp.length?sp[0].value:e.name;gkey=e.id;bucket=[e];
    }else{bucket.push(e);}
  });
  if(bucket.length)groups.push({screen:screen,key:gkey,entries:bucket});
  var html='';
  groups.slice().reverse().forEach(function(g){
    var vis=g.entries.filter(matches);
    if(!vis.length)return;
    var k=g.key||vis[0].id;
    if(g.screen){
      var col=!!collapsedGroups[k];
      var label;
      if(col){
        var events=0,calls=0;
        vis.forEach(function(x){if(x.type==='analytics')events++;else calls++;});
        var parts=[];
        if(events>0)parts.push(events+(events===1?' event':' events'));
        if(calls>0)parts.push(calls+(calls===1?' call':' calls'));
        label=parts.join(' · ');
      }else{
        label='screen_view';
      }
      html+="<div class='hgroup' onclick='toggleGroup(\""+k+"\")'>"+
        "<span class='chev"+(col?'':' open')+"'>"+icon('chevron',14)+"</span>"+
        icon('phone',14)+" "+esc(g.screen)+
        "<span class='hlabel'>"+esc(label)+"</span></div>";
      if(col)return;
    }
    html+=vis.slice().reverse().map(rowHtml).join('');
  });
  return html;
}
function toggleGroup(k){collapsedGroups[k]=!collapsedGroups[k];render();}

function kvHtml(items,alignEnd){
  return (items||[]).map(function(k){
    var v=k.redacted?"<span class='redact'></span>":esc(k.value);
    return "<div class='kv"+(alignEnd?' end':'')+"'><div class='k mono'>"+esc(k.name)+"</div><div class='v mono'>"+v+"</div></div>";
  }).join('');
}
function pretty(s){if(!s)return '';try{return JSON.stringify(JSON.parse(s),null,2);}catch(e){return s;}}
function compact(s){if(!s)return '';try{return JSON.stringify(JSON.parse(s));}catch(e){return s;}}
function hl(s){
  return esc(s).replace(/("(?:[^"\\]|\\.)*")(\s*:)?|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|\b(true|false|null)\b|([{}\[\],])/g,function(m,str,colon,num,kw,punc){
    if(str)return "<span class='"+(colon?'jk':'js')+"'>"+str+"</span>"+(colon?("<span class='jp'>"+colon+"</span>"):'');
    if(num)return "<span class='jn'>"+num+"</span>";
    if(kw)return "<span class='jb'>"+kw+"</span>";
    if(punc)return "<span class='jp'>"+punc+"</span>";
    return m;
  });
}
function toggleHeaders(k){openHeaders[k]=!openHeaders[k];renderDetail();}
function headersSection(label,items,key){
  var open=!!openHeaders[key];
  var h="<div class='shead' onclick='toggleHeaders(\""+key+"\")'><span class='chev"+(open?' open':'')+"'>"+icon('chevron',12)+"</span>"+esc(label)+" ("+(items||[]).length+")</div>";
  if(open)h+=(kvHtml(items,false)||"<div class='sub'>None</div>");
  return h;
}
function segHtml(active,fn){
  return "<span class='seg'><span class='"+(active?'':'on')+"' onclick='"+fn+"(false)'>Pretty</span><span class='"+(active?'on':'')+"' onclick='"+fn+"(true)'>Raw</span></span>";
}
function setRaw(v){rawBody=v;renderDetail();}
function setRawParams(v){rawParams=v;renderDetail();}

function curlCmd(e){
  var parts=['curl'];
  if((e.method||'GET').toUpperCase()!=='GET')parts.push('-X '+e.method.toUpperCase());
  parts.push("'"+e.url+"'");
  (e.requestHeaders||[]).forEach(function(k){parts.push("-H '"+k.name+": "+(''+k.value).replace(/'/g,"'\\''")+"'");});
  if(e.requestBody)parts.push("--data '"+e.requestBody.replace(/'/g,"'\\''")+"'");
  return parts.join(' \\\n  ');
}
function fallbackCopy(t){var ta=document.createElement('textarea');ta.value=t;document.body.appendChild(ta);ta.select();try{document.execCommand('copy');}catch(err){}document.body.removeChild(ta);}
function copyText(text,message){
  var done=function(){toast(message);};
  if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(text).then(done,function(){fallbackCopy(text);done();});}
  else{fallbackCopy(text);done();}
}
function copyCurl(id){
  var e=null;for(var i=0;i<entries.length;i++){if(entries[i].id===id){e=entries[i];break;}}
  if(!e)return;
  copyText(curlCmd(e),'Copied as cURL');
}
function copyPayload(id){
  var e=null;for(var i=0;i<entries.length;i++){if(entries[i].id===id){e=entries[i];break;}}
  if(!e)return;
  var obj={};(e.properties||[]).forEach(function(k){obj[k.name]=k.value;});
  copyText(JSON.stringify(obj,null,2),'Copied payload');
}

function toast(msg){
  var t=document.getElementById('toast');
  t.textContent=msg;
  t.className='toast show';
  if(toastTimer)clearTimeout(toastTimer);
  toastTimer=setTimeout(function(){t.className='toast';},1800);
}
function clearAll(){
  fetch('/api/clear',{method:'POST'}).then(function(){return fetch('/api/entries');}).then(function(r){return r.json();}).then(function(j){
    latest=j;entries=j;selectedId=null;toast('Cleared');render();
  });
}
function exportJson(){
  var data=JSON.stringify(entries,null,2);
  var blob=new Blob([data],{type:'application/json'});
  var url=URL.createObjectURL(blob);
  var a=document.createElement('a');
  a.href=url;a.download='snoop-export.json';
  document.body.appendChild(a);a.click();document.body.removeChild(a);
  URL.revokeObjectURL(url);
  toast('Exported '+entries.length+' entries');
}
function togglePause(){
  paused=!paused;
  if(!paused&&latest){entries=latest;}
  render();
}
function setDetailTab(t){detailTab=t;renderDetail();}

function requestResponseHtml(key,label,headers,body){
  var h="<div class='card'>"+headersSection('Headers',headers,key)+"</div>";
  h+="<div class='card'>";
  h+="<div class='shrow' style='margin-top:0'><div class='section'>"+esc(label)+"</div>"+(body?segHtml(rawBody,'setRaw'):'')+"</div>";
  if(!body){
    h+="<div class='sub'>Empty</div>";
  }else{
    h+="<div class='code mono' style='margin-top:8px'>"+(rawBody?esc(compact(body)):hl(pretty(body)))+"</div>";
  }
  h+="</div>";
  return h;
}
function networkDetailHtml(e){
  var h="<div class='dwrap'>";
  h+="<div class='dheadrow'><div><div class='dtitle'>"+esc(e.method)+" "+esc(e.path||'/')+"</div><div class='dsub'>"+esc(e.host)+"</div></div>"+
    "<button class='copybtn' onclick='copyCurl(\""+e.id+"\")'>"+icon('copy',14)+" Copy as cURL</button></div>";
  h+="<div class='pillrow'>"+pill(statusLabel(e),tintStyle(statusTint(e)),true);
  if(e.durationMillis!=null)h+=pill(dur(e.durationMillis),tintStyle('neu'),true);
  h+="</div>";
  h+="<div class='tabs'>";
  ['overview','request','response'].forEach(function(t){
    h+="<div class='tab"+(detailTab===t?' on':'')+"' onclick='setDetailTab(\""+t+"\")'>"+t.charAt(0).toUpperCase()+t.slice(1)+"</div>";
  });
  h+="</div>";
  if(detailTab==='overview'){
    h+="<div class='card'>";
    h+="<div class='section'>URL</div><div class='mono' style='word-break:break-all'>"+esc(e.url)+"</div>";
    if(e.error)h+="<div class='section'>Error</div><div style='color:var(--err-fg)'>"+esc(e.error)+"</div>";
    if(e.correlatedCallId)h+="<div class='section'>Analytics</div><div>An analytics event was sent over this call.</div>";
    h+="</div>";
  }else if(detailTab==='request'){
    h+=requestResponseHtml(e.id+':req','Body',e.requestHeaders,e.requestBody);
  }else{
    h+=requestResponseHtml(e.id+':res','Body',e.responseHeaders,e.responseBody);
  }
  h+="</div>";
  return h;
}
function analyticsDetailHtml(e){
  var pair=pal(e.channel);
  var h="<div class='dwrap'>";
  h+="<div class='dheadrow'><div><div class='dtitle'>"+esc(e.name)+"</div><div class='dsub'>event · "+esc(e.channel)+"</div></div>"+
    "<button class='copybtn' onclick='copyPayload(\""+e.id+"\")'>"+icon('copy',14)+" Copy payload</button></div>";
  h+="<div class='pillrow'>"+pill(e.channel,chanStyle(pair),false);
  (e.annotations||[]).forEach(function(a){
    var style=a==='ecommerce'?tintStyle('ecom'):tintStyle('neu');
    h+=pill(a,style,false);
  });
  h+=pill(clock(e.ts),tintStyle('neu'),true);
  h+="</div>";
  if(e.correlatedCallId){
    var call=null;for(var i=0;i<entries.length;i++){if(entries[i].id===e.correlatedCallId){call=entries[i];break;}}
    if(call){
      h+="<div class='corr' onclick='sel(\""+call.id+"\")'>"+icon('link',16)+
        "<div class='grow'>"+esc(call.method+' '+call.path)+"</div>"+
        pill(statusLabel(call),tintStyle(statusTint(call)),true)+
        icon('chevron',16)+"</div>";
    }
  }
  h+="<div class='shrow'><div class='section'>Parameters</div>"+segHtml(rawParams,'setRawParams')+"</div>";
  if(!(e.properties||[]).length){
    h+="<div class='sub'>None</div>";
  }else if(rawParams){
    var obj={};e.properties.forEach(function(k){obj[k.name]=k.redacted?'•••':k.value;});
    h+="<div class='code mono' style='margin-top:8px'>"+esc(JSON.stringify(obj,null,2))+"</div>";
  }else{
    h+="<div class='card' style='margin-top:8px'>"+kvHtml(e.properties,true)+"</div>";
  }
  h+="</div>";
  return h;
}
function renderDetail(){
  var d=document.getElementById('detail');
  var e=null;for(var i=0;i<entries.length;i++){if(entries[i].id===selectedId){e=entries[i];break;}}
  if(!e){d.className='detail';d.innerHTML='';return;}
  d.className='detail open';
  d.innerHTML=e.type==='network'?networkDetailHtml(e):analyticsDetailHtml(e);
}
function sel(id){
  if(id!==selectedId){detailTab='overview';rawBody=false;rawParams=false;}
  selectedId=id;render();
}
function setType(t){typeFilter=t;if(t==='network')channelFilter=null;render();}
function setChan(c){if(channelFilter===c){channelFilter=null;}else{channelFilter=c;typeFilter='analytics';}render();}

function render(){
  document.getElementById('count').textContent=entries.length;
  document.getElementById('dot').className='dot'+(paused?' off':'');
  var chans=[];entries.forEach(function(e){if(e.type==='analytics'&&chans.indexOf(e.channel)<0)chans.push(e.channel);});
  var tc=[['all','All'],['network','Network'],['analytics','Analytics']].map(function(t){
    return "<span class='chip"+(typeFilter===t[0]?' on':'')+"' onclick='setType(\""+t[0]+"\")'>"+t[1]+"</span>";
  }).join('');
  document.getElementById('typechips').innerHTML=tc;
  document.getElementById('chanchips').innerHTML=chans.map(function(c){
    var pair=pal(c);
    var on=channelFilter===c;
    var style=on?('background:'+pair[0]+';color:'+pair[1]+';border:2px solid '+pair[1]):'';
    return "<span class='chip"+(on?' on':'')+"' style='"+style+"' onclick='setChan(\""+c+"\")'><span class='chipdot' style='background:"+pair[1]+"'></span>"+esc(c)+"</span>";
  }).join('');
  document.getElementById('list').innerHTML=listHtml()||emptyHtml();
  renderDetail();
  document.getElementById('pauseIcon').style.display=paused?'none':'inline-flex';
  document.getElementById('playIcon').style.display=paused?'inline-flex':'none';
}

document.getElementById('pauseIcon').innerHTML=icon('pause',18);
document.getElementById('playIcon').innerHTML=icon('play',18);
document.getElementById('clearBtn').innerHTML=icon('clear',18);
document.getElementById('exportBtn').innerHTML=icon('export',18);
document.getElementById('q').addEventListener('input',function(ev){search=ev.target.value;render();});
var es=new EventSource('/api/stream');
es.onmessage=function(ev){latest=JSON.parse(ev.data);if(!paused){entries=latest;render();}};
fetch('/api/entries').then(function(r){return r.json();}).then(function(j){if(!latest){latest=j;entries=j;render();}});
render();
</script>
</body>
</html>
"""
