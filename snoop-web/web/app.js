var entries=[],byId=Object.create(null),latest=null,paused=false,typeFilter='all',channelFilter=null,search='',selectedId=null,collapsedGroups={},openHeaders={},detailTab='overview',rawBody=false,rawParams=false,toastTimer=null;

// Detail rendering looks entries up by id on every SSE frame; index once per frame instead of
// scanning the list per lookup. Null-prototype map so a captured id can't reach Object.prototype.
function setEntries(list){
  entries=list||[];
  byId=Object.create(null);
  for(var i=0;i<entries.length;i++)byId[entries[i].id]=entries[i];
}
function entryById(id){return (id==null)?null:(byId[id]||null);}

var TOKEN=new URLSearchParams(location.search).get('t')||'';
function apiUrl(p){return TOKEN?p+(p.indexOf('?')<0?'?':'&')+'t='+encodeURIComponent(TOKEN):p;}

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

function esc(s){s=(s==null)?'':(''+s);return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');}
function pad(n,l){n=''+n;while(n.length<l)n='0'+n;return n;}
function clock(ts){var d=new Date(ts);return pad(d.getHours(),2)+':'+pad(d.getMinutes(),2)+':'+pad(d.getSeconds(),2)+'.'+pad(d.getMilliseconds(),3);}
function dur(ms){if(ms==null)return '';return ms<1000?(ms+' ms'):((ms/1000).toFixed(1)+' s');}

function pal(ch){var h=0;ch=ch||'';for(var i=0;i<ch.length;i++){h=(h*31+ch.charCodeAt(i))|0;}var idx=((h%channelPalette.length)+channelPalette.length)%channelPalette.length;return channelPalette[idx];}
function chanStyle(pair){return 'background:'+pair[0]+';color:'+pair[1];}
function methodTint(m){m=(m||'').toUpperCase();if(m==='GET')return 'info';if(m==='POST')return 'ok';if(m==='PUT'||m==='PATCH')return 'warn';if(m==='DELETE')return 'err';return 'neu';}
function statusTint(e){if(e.state==='failed')return 'err';if(e.state==='pending')return 'neu';var s=e.status||0;if(s<200)return 'info';if(s<300)return 'ok';if(s<400)return 'info';if(s<500)return 'warn';return 'err';}
function tintStyle(key){return 'background:var(--'+key+'-bg);color:var(--'+key+'-fg)';}
function statusLabel(e){if(e.state==='failed')return 'ERR';if(e.state==='pending')return '...';return ''+e.status;}
function pill(text,style,mono){return "<span class='pill"+(mono?' mono':'')+"' style='"+style+"'>"+esc(text)+"</span>";}

function matches(e){
  if(typeFilter!=='all'&&e.type!==typeFilter)return false;
  if(channelFilter&&e.channel!==channelFilter)return false;
  if(search){
    var q=search.toLowerCase();
    var hay=e.type==='network'?((e.url||'')+' '+(e.host||'')+' '+(e.method||'')):((e.name||'')+' '+(e.channel||''));
    if(hay.toLowerCase().indexOf(q)<0)return false;
  }
  return true;
}

function rowHtml(e){
  if(e.type==='network'){
    return "<div class='row"+(e.id===selectedId?' sel':'')+"' data-id='"+esc(e.id)+"'>"+
      "<span class='time mono'>"+clock(e.ts)+"</span>"+
      pill(e.method,tintStyle(methodTint(e.method)),true)+
      "<div class='grow'><div class='title'>"+esc(e.path||'/')+"</div><div class='sub'>"+esc(e.host)+"</div></div>"+
      "<div class='right'>"+pill(statusLabel(e),tintStyle(statusTint(e)),true)+"<div class='sub mono'>"+esc(dur(e.durationMillis))+"</div></div>"+
      "</div>";
  }
  var pair=pal(e.channel);
  var n=(e.properties||[]).length;
  var sub=n+(n===1?' param':' params');
  return "<div class='row"+(e.id===selectedId?' sel':'')+"' data-id='"+esc(e.id)+"'>"+
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
      html+="<div class='hgroup' data-group='"+esc(k)+"'>"+
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
// Tokenize the raw text and escape each piece as it is emitted. Matching against pre-escaped text
// would never see a quote (esc turns it into &quot;) and would highlight the digits inside &#39;.
// Every gap between matches has to be escaped too — replace() hands the callback only the matches.
function hl(s){
  s=(s==null)?'':(''+s);
  var re=/("(?:[^"\\]|\\.)*")(\s*:)?|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|\b(true|false|null)\b|([{}\[\],])/g;
  var out='',last=0,m;
  while((m=re.exec(s))!==null){
    out+=esc(s.slice(last,m.index));
    if(m[1])out+="<span class='"+(m[2]?'jk':'js')+"'>"+esc(m[1])+"</span>"+(m[2]?("<span class='jp'>"+esc(m[2])+"</span>"):'');
    else if(m[3])out+="<span class='jn'>"+esc(m[3])+"</span>";
    else if(m[4])out+="<span class='jb'>"+esc(m[4])+"</span>";
    else if(m[5])out+="<span class='jp'>"+esc(m[5])+"</span>";
    else out+=esc(m[0]);
    last=re.lastIndex;
  }
  return out+esc(s.slice(last));
}
function toggleHeaders(k){openHeaders[k]=!openHeaders[k];renderDetail();}
function headersSection(label,items,key){
  var open=!!openHeaders[key];
  var h="<div class='shead' data-headers='"+esc(key)+"'><span class='chev"+(open?' open':'')+"'>"+icon('chevron',12)+"</span>"+esc(label)+" ("+(items||[]).length+")</div>";
  if(open)h+=(kvHtml(items,false)||"<div class='sub'>None</div>");
  return h;
}
function segHtml(active,fn){
  return "<span class='seg'><span class='"+(active?'':'on')+"' onclick='"+fn+"(false)'>Pretty</span><span class='"+(active?'on':'')+"' onclick='"+fn+"(true)'>Raw</span></span>";
}
function setRaw(v){rawBody=v;renderDetail();}
function setRawParams(v){rawParams=v;renderDetail();}

function shq(s){return (''+s).replace(/'/g,"'\\''");}
function curlCmd(e){
  var parts=['curl'];
  if((e.method||'GET').toUpperCase()!=='GET')parts.push('-X '+shq(e.method.toUpperCase()));
  parts.push("'"+shq(e.url)+"'");
  (e.requestHeaders||[]).forEach(function(k){parts.push("-H '"+shq(k.name)+": "+shq(k.value)+"'");});
  if(e.requestBody)parts.push("--data '"+shq(e.requestBody)+"'");
  return parts.join(' \\\n  ');
}
function fallbackCopy(t){var ta=document.createElement('textarea');ta.value=t;document.body.appendChild(ta);ta.select();try{document.execCommand('copy');}catch(err){}document.body.removeChild(ta);}
function copyText(text,message){
  var done=function(){toast(message);};
  if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(text).then(done,function(){fallbackCopy(text);done();});}
  else{fallbackCopy(text);done();}
}
function copyCurl(id){
  var e=entryById(id);
  if(!e)return;
  copyText(curlCmd(e),'Copied as cURL');
}
function copyPayload(id){
  var e=entryById(id);
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
  fetch(apiUrl('/api/clear'),{method:'POST',headers:{'X-Snoop':'1'}}).then(function(r){
    if(!r.ok)throw new Error('clear rejected: '+r.status);
    return fetch(apiUrl('/api/entries'));
  }).then(function(r){return r.json();}).then(function(j){
    latest=j;setEntries(j);selectedId=null;toast('Cleared');render();
  }).catch(function(){toast('Clear failed');});
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
  if(!paused&&latest){setEntries(latest);}
  render();
}
function setDetailTab(t){detailTab=t;renderDetail();}

function requestResponseHtml(e,side){
  var key=e.id+':'+side,label='Body';
  var headers=side==='req'?e.requestHeaders:e.responseHeaders;
  var body=side==='req'?e.requestBody:e.responseBody;
  var truncated=side==='req'?e.requestBodyTruncated:e.responseBodyTruncated;
  var byteCount=side==='req'?e.requestByteCount:e.responseByteCount;
  var h="<div class='card'>"+headersSection('Headers',headers,key)+"</div>";
  h+="<div class='card'>";
  h+="<div class='shrow' style='margin-top:0'><div class='section'>"+esc(label)+"</div>"+(body?segHtml(rawBody,'setRaw'):'')+"</div>";
  if(!body){
    h+="<div class='sub'>Empty</div>";
  }else{
    if(truncated)h+="<div class='sub'>Showing first "+body.length+" of "+byteCount+" bytes</div>";
    h+="<div class='code mono' style='margin-top:8px'>"+(rawBody?esc(compact(body)):hl(pretty(body)))+"</div>";
  }
  h+="</div>";
  return h;
}
function networkDetailHtml(e){
  var h="<div class='dwrap'>";
  h+="<div class='dheadrow'><div><div class='dtitle'>"+esc(e.method)+" "+esc(e.path||'/')+"</div><div class='dsub'>"+esc(e.host)+"</div></div>"+
    "<button class='copybtn' data-curl='"+esc(e.id)+"'>"+icon('copy',14)+" Copy as cURL</button></div>";
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
    if(e.protocol)h+="<div class='section'>Protocol</div><div class='mono'>"+esc(e.protocol)+"</div>";
    if(e.requestContentType)h+="<div class='section'>Request type</div><div class='mono'>"+esc(e.requestContentType)+"</div>";
    if(e.responseContentType)h+="<div class='section'>Response type</div><div class='mono'>"+esc(e.responseContentType)+"</div>";
    if(e.error)h+="<div class='section'>Error</div><div style='color:var(--err-fg)'>"+esc(e.error)+"</div>";
    h+="</div>";
  }else if(detailTab==='request'){
    h+=requestResponseHtml(e,'req');
  }else{
    h+=requestResponseHtml(e,'res');
  }
  h+="</div>";
  return h;
}
function analyticsDetailHtml(e){
  var pair=pal(e.channel);
  var h="<div class='dwrap'>";
  h+="<div class='dheadrow'><div><div class='dtitle'>"+esc(e.name)+"</div><div class='dsub'>event · "+esc(e.channel)+"</div></div>"+
    "<button class='copybtn' data-payload='"+esc(e.id)+"'>"+icon('copy',14)+" Copy payload</button></div>";
  h+="<div class='pillrow'>"+pill(e.channel,chanStyle(pair),false);
  (e.annotations||[]).forEach(function(a){
    var style=a==='ecommerce'?tintStyle('ecom'):tintStyle('neu');
    h+=pill(a,style,false);
  });
  h+=pill(clock(e.ts),tintStyle('neu'),true);
  h+="</div>";
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
  var e=entryById(selectedId);
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
    return "<span class='chip"+(on?' on':'')+"' style='"+style+"' data-chan='"+esc(c)+"'><span class='chipdot' style='background:"+pair[1]+"'></span>"+esc(c)+"</span>";
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
// Delegated clicks: entry data (ids, channels, header keys) never lands in inline handlers,
// so a crafted value can't execute as script even if it survives escaping.
document.getElementById('chanchips').addEventListener('click',function(ev){
  var t=ev.target.closest('[data-chan]');
  if(t)setChan(t.getAttribute('data-chan'));
});
document.getElementById('list').addEventListener('click',function(ev){
  var g=ev.target.closest('[data-group]');
  if(g){toggleGroup(g.getAttribute('data-group'));return;}
  var r=ev.target.closest('[data-id]');
  if(r)sel(r.getAttribute('data-id'));
});
document.getElementById('detail').addEventListener('click',function(ev){
  var c=ev.target.closest('[data-curl]');
  if(c){copyCurl(c.getAttribute('data-curl'));return;}
  var p=ev.target.closest('[data-payload]');
  if(p){copyPayload(p.getAttribute('data-payload'));return;}
  var hd=ev.target.closest('[data-headers]');
  if(hd){toggleHeaders(hd.getAttribute('data-headers'));return;}
  var r=ev.target.closest('[data-id]');
  if(r)sel(r.getAttribute('data-id'));
});
var es=new EventSource(apiUrl('/api/stream'));
es.onmessage=function(ev){latest=JSON.parse(ev.data);if(!paused){setEntries(latest);render();}};
fetch(apiUrl('/api/entries')).then(function(r){return r.json();}).then(function(j){if(!latest){latest=j;setEntries(j);render();}});
render();
