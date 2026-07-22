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
<style>
:root{--bg:#f7f7fb;--panel:#ffffff;--line:#e6e6ee;--fg:#1b1b20;--muted:#6b7280;--accent:#6d5efc;--tint:#00000008;--jk:#1d4ed8;--js:#9a3412;--jn:#6d28d9;--jb:#047857;}
@media (prefers-color-scheme:dark){:root{--bg:#0f0f14;--panel:#17171f;--line:#26262f;--fg:#e9e9ef;--muted:#9aa0aa;--accent:#8b7dff;--tint:#ffffff0a;--jk:#60a5fa;--js:#fdba74;--jn:#a78bfa;--jb:#34d399;}}
*{box-sizing:border-box}
body{margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:var(--bg);color:var(--fg);font-size:14px}
header{position:sticky;top:0;background:var(--panel);border-bottom:1px solid var(--line);padding:10px 16px;display:flex;gap:12px;align-items:center;flex-wrap:wrap;z-index:10}
.brand{font-weight:700;font-size:18px}
.dot{width:9px;height:9px;border-radius:50%;background:#16a34a}
.dot.off{background:var(--muted)}
.count{color:var(--muted);font-size:12px}
input#q{flex:1;min-width:160px;padding:8px 12px;border:1px solid var(--line);border-radius:10px;background:var(--bg);color:var(--fg);font-size:14px}
.chips{display:flex;gap:6px;flex-wrap:wrap}
.chip{padding:5px 12px;border:1px solid var(--line);border-radius:999px;background:var(--panel);color:var(--fg);cursor:pointer;font-size:13px}
.chip.on{background:var(--fg);color:var(--bg);border-color:transparent}
.btn{margin-left:auto;display:flex;align-items:center;gap:6px;padding:7px 14px;border:1px solid var(--line);border-radius:10px;background:var(--panel);color:var(--fg);cursor:pointer;font-weight:600;font-size:13px}
.btn.paused{background:#d9770622;color:#d97706;border-color:transparent}
main{display:flex;height:calc(100vh - 58px);padding:10px 12px;gap:10px}
.list{flex:1;overflow:auto;background:var(--panel);border:1px solid var(--line);border-radius:16px}
.detail{width:0;overflow:auto;background:var(--panel);transition:width .15s;border-radius:16px}
.detail.open{width:46%;min-width:340px;border:1px solid var(--line)}
.row{display:flex;gap:10px;align-items:center;padding:11px 14px;border-bottom:1px solid var(--line);cursor:pointer;border-left:3px solid transparent}
.row:hover{background:var(--tint)}
.row.ana{background:var(--tint)}
.row.sel{background:#8b7dff22}
.time{font-family:ui-monospace,Menlo,monospace;color:var(--muted);font-size:12px;white-space:nowrap}
.pill{padding:2px 8px;border-radius:6px;font-size:12px;font-weight:600;white-space:nowrap}
.grow{flex:1;min-width:0}
.title{font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.sub{color:var(--muted);font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.right{text-align:right}
.hgroup{display:flex;align-items:center;gap:8px;padding:8px 14px;background:var(--tint);font-weight:600;border-bottom:1px solid var(--line);font-size:13px;cursor:pointer}
.hlabel{margin-left:auto;color:var(--muted);font-weight:400;font-size:12px}
.jk{color:var(--jk)}.js{color:var(--js)}.jn{color:var(--jn)}.jb{color:var(--jb)}
.dwrap{padding:16px}
.dtitle{font-weight:700;font-size:16px;margin-bottom:2px;word-break:break-all}
.section{margin-top:18px;text-transform:uppercase;font-size:11px;letter-spacing:.4px;color:var(--muted);font-weight:600}
.kv{display:flex;gap:12px;padding:4px 0;border-bottom:1px solid var(--line)}
.kv .k{flex:.42;color:var(--muted)}
.kv .v{flex:.58;font-family:ui-monospace,Menlo,monospace;word-break:break-all}
.redact{display:inline-block;width:64px;height:14px;background:var(--fg);border-radius:3px;vertical-align:middle}
pre{background:var(--bg);border:1px solid var(--line);border-radius:12px;padding:12px;overflow:auto;font-family:ui-monospace,Menlo,monospace;font-size:12px;white-space:pre-wrap;word-break:break-word}
.seg{display:inline-flex;background:var(--tint);border-radius:999px;padding:2px;margin-left:8px;vertical-align:middle}
.seg span{padding:3px 10px;border-radius:999px;cursor:pointer;font-size:12px;font-weight:600;color:var(--muted);text-transform:none;letter-spacing:0}
.seg span.on{background:var(--fg);color:var(--bg)}
.close{float:right;cursor:pointer;color:var(--muted);font-size:20px;border:none;background:none}
.empty{padding:60px 20px;text-align:center;color:var(--muted)}
.badge{display:inline-block;padding:2px 8px;border-radius:6px;font-size:12px;font-weight:600;margin-right:6px}
.corr{padding:12px;border-radius:10px;background:#8b7dff22;cursor:pointer;margin-top:6px}
</style>
</head>
<body>
<header>
  <span class='brand'>Snoop</span>
  <span id='dot' class='dot'></span>
  <span id='count' class='count'>0</span>
  <input id='q' placeholder='Search path, host, event…'>
  <div class='chips' id='typechips'></div>
  <div class='chips' id='chanchips'></div>
  <button class='btn' id='pause'>⏸ Pause</button>
</header>
<main>
  <div class='list' id='list'></div>
  <div class='detail' id='detail'></div>
</main>
<script>
var entries=[],latest=null,paused=false,typeFilter='all',channelFilter=null,search='',selectedId=null,collapsedGroups={};
var PALETTE=['#3B82F6','#10B981','#F59E0B','#EF4444','#8B5CF6','#EC4899','#14B8A6'];
function esc(s){s=(s==null)?'':(''+s);return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
function pal(ch){var h=0;ch=ch||'';for(var i=0;i<ch.length;i++){h=(h*31+ch.charCodeAt(i))|0;}return PALETTE[((h%PALETTE.length)+PALETTE.length)%PALETTE.length];}
function methodColor(m){m=(m||'').toUpperCase();if(m==='GET')return '#2563EB';if(m==='POST')return '#16A34A';if(m==='PUT'||m==='PATCH')return '#D97706';if(m==='DELETE')return '#DC2626';return '#6B7280';}
function statusColor(e){if(e.state==='failed')return '#DC2626';if(e.state==='pending')return '#6B7280';var s=e.status||0;if(s<300)return '#16A34A';if(s<400)return '#2563EB';if(s<500)return '#D97706';return '#DC2626';}
function pad(n,l){n=''+n;while(n.length<l)n='0'+n;return n;}
function clock(ts){var d=new Date(ts);return pad(d.getHours(),2)+':'+pad(d.getMinutes(),2)+':'+pad(d.getSeconds(),2)+'.'+pad(d.getMilliseconds(),3);}
function dur(ms){if(ms==null)return '';return ms<1000?(ms+' ms'):((ms/1000).toFixed(1)+' s');}
function pill(text,color,filled){var st=filled?('background:'+color+';color:#fff'):('background:'+color+'22;color:'+color);return "<span class='pill' style='"+st+"'>"+esc(text)+"</span>";}
function matches(e){if(typeFilter!=='all'&&e.type!==typeFilter)return false;if(channelFilter&&e.channel!==channelFilter)return false;if(search){var q=search.toLowerCase();var hay=e.type==='network'?((e.path||'')+' '+(e.host||'')+' '+(e.method||'')):((e.name||'')+' '+(e.channel||''));if(hay.toLowerCase().indexOf(q)<0)return false;}return true;}
function statusLabel(e){if(e.state==='failed')return 'ERR';if(e.state==='pending')return '…';return ''+e.status;}
function rowHtml(e){
  if(e.type==='network'){
    return "<div class='row"+(e.id===selectedId?" sel":"")+"' style='border-left-color:"+statusColor(e)+"' onclick='sel(\""+e.id+"\")'>"+
      "<span class='time'>"+clock(e.ts)+"</span>"+pill(e.method,methodColor(e.method))+
      "<div class='grow'><div class='title'>"+esc(e.path||'/')+"</div><div class='sub'>"+esc(e.host)+"</div></div>"+
      "<div class='right'>"+pill(statusLabel(e),statusColor(e))+"<div class='sub'>"+dur(e.durationMillis)+"</div></div></div>";
  }
  var c=pal(e.channel);var ann=(e.annotations||[]).map(function(a){return "· "+esc(a);}).join(' ');
  return "<div class='row ana"+(e.id===selectedId?" sel":"")+"' style='border-left-color:"+c+"' onclick='sel(\""+e.id+"\")'>"+
    "<span class='time'>"+clock(e.ts)+"</span>"+pill(e.channel,c,true)+
    "<div class='grow'><div class='title'>"+esc(e.name)+"</div><div class='sub'>event "+ann+"</div></div>"+
    (e.correlatedCallId?"<span>🔗</span>":"")+"</div>";
}
function render(){
  document.getElementById('count').textContent=entries.length;
  document.getElementById('dot').className='dot'+(paused?' off':'');
  var chans=[];entries.forEach(function(e){if(e.type==='analytics'&&chans.indexOf(e.channel)<0)chans.push(e.channel);});
  var tc=[['all','All'],['network','Network'],['analytics','Analytics']].map(function(t){return "<span class='chip"+(typeFilter===t[0]?" on":"")+"' onclick='setType(\""+t[0]+"\")'>"+t[1]+"</span>";}).join('');
  document.getElementById('typechips').innerHTML=tc;
  document.getElementById('chanchips').innerHTML=chans.map(function(c){var col=pal(c);var st=channelFilter===c?" style='background:"+col+"22;color:"+col+";border-color:transparent'":"";return "<span class='chip'"+st+" onclick='setChan(\""+c+"\")'>"+esc(c)+"</span>";}).join('');
  document.getElementById('list').innerHTML=listHtml()||"<div class='empty'>Nothing to show yet.</div>";
  renderDetail();
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
      html+="<div class='hgroup' onclick='toggleGroup(\""+k+"\")'>"+(col?'▸':'▾')+" 📱 "+esc(g.screen)+"<span class='hlabel'>screen_view</span></div>";
      if(col)return;
    }
    html+=vis.slice().reverse().map(rowHtml).join('');
  });
  return html;
}
function toggleGroup(k){collapsedGroups[k]=!collapsedGroups[k];render();}
function kvHtml(items){return (items||[]).map(function(k){var v=k.redacted?"<span class='redact'></span>":esc(k.value);return "<div class='kv'><div class='k'>"+esc(k.name)+"</div><div class='v'>"+v+"</div></div>";}).join('');}
function pretty(s){if(!s)return '';try{return JSON.stringify(JSON.parse(s),null,2);}catch(e){return s;}}
function compact(s){if(!s)return '';try{return JSON.stringify(JSON.parse(s));}catch(e){return s;}}
function hl(s){return esc(s).replace(/("(?:[^"\\]|\\.)*")(\s*:)?|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|\b(true|false|null)\b/g,function(m,str,colon,num,kw){if(str)return "<span class='"+(colon?'jk':'js')+"'>"+str+"</span>"+(colon||'');if(num)return "<span class='jn'>"+num+"</span>";return "<span class='jb'>"+kw+"</span>";});}
var rawBody=false;
function setRaw(v){rawBody=v;renderDetail();}
function segHtml(){return "<span class='seg'><span class='"+(rawBody?'':'on')+"' onclick='setRaw(false)'>Pretty</span><span class='"+(rawBody?'on':'')+"' onclick='setRaw(true)'>Raw</span></span>";}
function renderDetail(){
  var d=document.getElementById('detail');var e=null;for(var i=0;i<entries.length;i++){if(entries[i].id===selectedId){e=entries[i];break;}}
  if(!e){d.className='detail';d.innerHTML='';return;}
  d.className='detail open';
  var h="<div class='dwrap'><button class='close' onclick='sel(null)'>×</button>";
  if(e.type==='network'){
    h+="<div class='dtitle'>"+esc(e.method)+" "+esc(e.path||'/')+"</div><div class='sub'>"+esc(e.host)+"</div>";
    h+="<div style='margin-top:10px'>"+pill(statusLabel(e),statusColor(e),true)+" "+pill(dur(e.durationMillis)||'—','#6d5efc')+"</div>";
    h+="<div class='section'>URL</div><pre>"+esc(e.url)+"</pre>";
    if(e.error){h+="<div class='section'>Error</div><pre>"+esc(e.error)+"</pre>";}
    h+="<div class='section'>Request headers</div>"+(kvHtml(e.requestHeaders)||'<div class=sub>None</div>');
    if(e.requestBody){h+="<div class='section'>Request body"+segHtml()+"</div><pre>"+(rawBody?esc(compact(e.requestBody)):hl(pretty(e.requestBody)))+"</pre>";}
    h+="<div class='section'>Response headers</div>"+(kvHtml(e.responseHeaders)||'<div class=sub>None</div>');
    if(e.responseBody){h+="<div class='section'>Response body"+segHtml()+"</div><pre>"+(rawBody?esc(compact(e.responseBody)):hl(pretty(e.responseBody)))+"</pre>";}
  }else{
    var c=pal(e.channel);
    h+="<div class='dtitle'>"+esc(e.name)+"</div><div style='margin-top:8px'>"+pill(e.channel,c,true);
    (e.annotations||[]).forEach(function(a){h+=" "+pill(a,c);});h+="</div>";
    if(e.correlatedCallId){var call=null;for(var j=0;j<entries.length;j++){if(entries[j].id===e.correlatedCallId){call=entries[j];break;}}if(call){h+="<div class='section'>Sent over</div><div class='corr' onclick='sel(\""+call.id+"\")'>"+esc(call.method)+" "+esc(call.path)+" → "+esc(call.status||'…')+"</div>";}}
    h+="<div class='section'>Parameters"+segHtml()+"</div>";
    if(!(e.properties||[]).length){h+="<div class=sub>None</div>";}
    else if(rawBody){var obj={};e.properties.forEach(function(k){obj[k.name]=k.value;});h+="<pre>"+esc(JSON.stringify(obj,null,2))+"</pre>";}
    else{h+=kvHtml(e.properties);}
  }
  d.innerHTML=h+"</div>";
}
function sel(id){selectedId=id;render();}
function setType(t){typeFilter=t;if(t==='network')channelFilter=null;render();}
function setChan(c){if(channelFilter===c){channelFilter=null;}else{channelFilter=c;typeFilter='analytics';}render();}
document.getElementById('q').addEventListener('input',function(e){search=e.target.value;render();});
document.getElementById('pause').addEventListener('click',function(){paused=!paused;this.textContent=paused?'▶ Resume':'⏸ Pause';this.className=paused?'btn paused':'btn';if(!paused&&latest){entries=latest;}render();});
var es=new EventSource('/api/stream');
es.onmessage=function(ev){latest=JSON.parse(ev.data);if(!paused){entries=latest;render();}};
fetch('/api/entries').then(function(r){return r.json();}).then(function(j){if(!latest){latest=j;entries=j;render();}});
render();
</script>
</body>
</html>
"""
