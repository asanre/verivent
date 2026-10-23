var entries=[],byId=Object.create(null),latest=null,paused=false,typeFilter='all',channelFilter=null,breaksFilter=false,search='',selectedId=null,collapsedGroups={},openHeaders={},detailTab='overview',rawBody=false,rawParams=false,rawSections={},openSections={},toastTimer=null,unreachable=false,plan=null,planOpen=false,showAllUnseen=false,shownSummary=null;

// Detail rendering looks entries up by id on every SSE frame; index once per frame instead of
// scanning the list per lookup. Null-prototype map so a captured id can't reach Object.prototype.
function setEntries(list,index){
  entries=list||[];
  if(index){byId=index;return;}
  byId=Object.create(null);
  for(var i=0;i<entries.length;i++)byId[entries[i].id]=entries[i];
}
function entryById(id){return (id==null)?null:(byId[id]||null);}

// The stream sends deltas: every frame carries the full id order but only the entries that changed.
// This map is the client's copy of the store, rebuilt from `ids` each frame so evictions and clears
// prune themselves. `latest` stays a fully materialized list, which is what pause/resume replays.
var streamById=Object.create(null);
function seedStream(list){
  streamById=Object.create(null);
  (list||[]).forEach(function(e){streamById[e.id]=e;});
  latest=list||[];
  return latest;
}
function applyFrame(frame){
  // Never mutate the previous map: `byId` may still point at it while the view is paused.
  var fresh=Object.create(null);
  (frame.entries||[]).forEach(function(e){fresh[e.id]=e;});
  var next=[],kept=Object.create(null);
  (frame.ids||[]).forEach(function(id){
    var e=fresh[id]||streamById[id];
    if(e){next.push(e);kept[id]=e;}
  });
  streamById=kept;
  latest=next;
  return next;
}

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
  copy:{d:'<rect x="8" y="8" width="12" height="12" rx="2"></rect><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"></path>',w:1.6},
  bars:{d:'<path d="M5 20V11"></path><path d="M12 20V4"></path><path d="M19 20v-6"></path>',w:1.6},
  pause:{d:'<rect x="6" y="5" width="4" height="14" rx="1.3"></rect><rect x="14" y="5" width="4" height="14" rx="1.3"></rect>',fill:true},
  play:{d:'<path d="M7 5l12 7-12 7z"></path>',fill:true},
  close:{d:'<path d="M6 6l12 12"></path><path d="M18 6L6 18"></path>',w:1.6},
  back:{d:'<path d="M15 5l-7 7 7 7"></path>',w:1.8},
  check:{d:'<path d="M5 12.5l4.5 4.5L19 7.5"></path>',w:2.2},
  plan:{d:'<path d="M9 4h6l1 2h3v14H5V6h3z"></path><path d="M9 12l2 2 4-4"></path>',w:1.8},
  alert:{d:'<circle cx="12" cy="12" r="9"></circle><path d="M12 8v5"></path><path d="M12 16.5v.5"></path>',w:2},
  upload:{d:'<path d="M12 16V5"></path><path d="M7 10l5-5 5 5"></path><path d="M5 19h14"></path>',w:1.6}
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
  if(breaksFilter&&!breaksPlan(e))return false;
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
    planMark(e)+
    pill(e.channel,chanStyle(pair),false)+
    "</div>";
}

// Only the loaded plan's verdicts are shown: one stamped by a plan since cleared or replaced stays in
// the entry, for the export, but no longer counts.
function checkedBy(e){return !!plan&&e.planVersion===plan.version;}
function breaksPlan(e){return checkedBy(e)&&(e.issues||[]).length>0;}
function problems(n){return n===1?'1 problem':n+' problems';}
var BREAKS_PILL='background:var(--breaks-pill-bg);color:var(--breaks-pill-fg)';
// ✕ n when the loaded plan found problems, ✓ when it found none, nothing when it didn't check the event.
function planMark(e){
  if(!checkedBy(e))return '';
  var n=(e.issues||[]).length;
  if(n)return "<span class='pill' style='"+BREAKS_PILL+"' role='img' aria-label='Breaks the plan: "+problems(n)+"'>✕ "+n+"</span>";
  return "<span class='okmark' role='img' aria-label='Matches the plan'>"+icon('check',16)+"</span>";
}

function emptyStateHtml(title,sub){
  return "<div class='empty'><div class='emptyicon'>"+icon('search',28)+"</div><div class='emptytitle'>"+esc(title)+"</div><div class='emptysub'>"+esc(sub)+"</div></div>";
}
function emptyHtml(){
  if(unreachable)return emptyStateHtml("Can't reach Verivent",'The app may have stopped, or this URL is missing its access token. The page reconnects on its own.');
  if(entries.length===0)return emptyStateHtml('Nothing captured yet','Network calls and analytics events will show up here as they happen.');
  return emptyStateHtml('No matches','Try a different filter or search.');
}
// Goes through render() rather than writing to #list: a later render — a stream frame, a filter
// click — would otherwise paint over the message.
function setUnreachable(v){
  if(unreachable===v)return;
  unreachable=v;
  render();
}

function listHtml(){
  // The capturing side stamps groupLabel; loose != so an omitted key (encodeDefaults=false) counts
  // as "no group" too.
  var groups=[],screen=null,openedBy=null,bucket=[],gkey=null;
  entries.forEach(function(e){
    if(e.type==='analytics'&&e.groupLabel!=null){
      if(bucket.length)groups.push({screen:screen,openedBy:openedBy,key:gkey,entries:bucket});
      screen=e.groupLabel;openedBy=e.name;gkey=e.id;bucket=[e];
    }else{bucket.push(e);}
  });
  if(bucket.length)groups.push({screen:screen,openedBy:openedBy,key:gkey,entries:bucket});
  var html='';
  groups.slice().reverse().forEach(function(g){
    var vis=g.entries.filter(matches);
    if(!vis.length)return;
    var k=g.key||vis[0].id;
    if(g.screen!=null){
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
        label=g.openedBy||'';
      }
      html+="<div class='hgroup' data-group='"+esc(k)+"'>"+
        "<span class='chev"+(col?'':' open')+"'>"+icon('chevron',14)+"</span>"+
        esc(g.screen)+
        "<span class='hlabel'>"+esc(label)+"</span></div>";
      if(col)return;
    }
    html+=vis.slice().reverse().map(rowHtml).join('');
  });
  return html;
}
function toggleGroup(k){collapsedGroups[k]=!collapsedGroups[k];render();}

// The sent property an issue belongs to: its own, or the one a nested path (tracks[*].id,
// item.price) starts from; null when it wasn't sent. Same rule as the app's issueRoot.
function issueRoot(path,sent){
  if(sent[path])return path;
  var best=null;
  Object.keys(sent).forEach(function(n){
    if((path.indexOf(n+'.')===0||path.indexOf(n+'[')===0)&&(best==null||n.length>best.length))best=n;
  });
  return best;
}
// [issues] mark the rows that break the tracking plan and add the required ones that weren't sent.
function kvHtml(items,alignEnd,issues){
  var sent=Object.create(null),byRoot=Object.create(null),missing=[];
  (items||[]).forEach(function(k){sent[k.name]=true;});
  (issues||[]).forEach(function(i){
    var root=issueRoot(i.property,sent);
    if(root==null){missing.push(i);return;}
    (byRoot[root]=byRoot[root]||[]).push(i.property===root?i.message:i.property+' · '+i.message);
  });
  var h=(items||[]).map(function(k){
    var v=k.redacted?"<span class='redact'></span>":esc(k.value);
    var lines=byRoot[k.name];
    if(!lines)return "<div class='kv"+(alignEnd?' end':'')+"'><div class='k mono'>"+esc(k.name)+"</div><div class='v mono'>"+v+"</div></div>";
    return "<div class='kv issue"+(alignEnd?' end':'')+"' title='"+esc(lines.join('\n'))+"'><div class='k mono'><span class='idot'></span>"+esc(k.name)+"</div><div class='v mono'>"+v+"</div></div>";
  }).join('');
  missing.forEach(function(i){
    h+="<div class='kv ghost'><div class='k mono'><span class='idot'></span>"+esc(i.property)+"</div><div class='v'>missing · required</div></div>";
  });
  return h;
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
function setRawSection(i,v){rawSections[i]=v;renderDetail();}
function toggleSection(i){openSections[i]=!openSections[i];renderDetail();}

function shq(s){return (''+s).replace(/'/g,"'\\''");}
function curlCmd(e){
  var parts=['curl'];
  if((e.method||'GET').toUpperCase()!=='GET')parts.push("-X '"+shq(e.method.toUpperCase())+"'");
  parts.push("'"+shq(e.url)+"'");
  (e.requestHeaders||[]).forEach(function(k){parts.push("-H '"+shq(k.name)+": "+shq(k.value)+"'");});
  if(e.requestBody)parts.push("--data '"+shq(e.requestBody)+"'");
  return parts.join(' \\\n  ');
}
function fallbackCopy(t){var ta=document.createElement('textarea');ta.value=t;document.body.appendChild(ta);ta.select();var ok=false;try{ok=document.execCommand('copy');}catch(err){}document.body.removeChild(ta);return ok;}
function copyText(text,message){
  var done=function(ok){toast(ok?message:'Copy failed');};
  if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(text).then(function(){done(true);},function(){done(fallbackCopy(text));});}
  else{done(fallbackCopy(text));}
}
function copyCurl(id){
  var e=entryById(id);
  if(!e)return;
  copyText(curlCmd(e),'Copied as cURL');
}
function copyBody(key){
  var sep=key.lastIndexOf(':');
  var e=entryById(key.slice(0,sep));
  if(!e)return;
  var body=key.slice(sep+1)==='req'?e.requestBody:e.responseBody;
  if(body)copyText(body,'Copied body');
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
// The plan lives on the device; the page only shows its summary and sends what the user provides.
function showPlan(status){
  plan=(status&&status.version)?{version:status.version,events:status.events||[]}:null;
  render();
}
// Every plan request takes a number, and a read only lands if nothing was sent after it: a slow GET
// must not paint the plan a load or clear has just replaced.
var planSeq=0;
function refreshPlan(){
  var seq=++planSeq;
  fetch(apiUrl('/api/tracking-plan')).then(function(r){return r.ok?r.json():null;})
    .then(function(s){if(seq===planSeq)showPlan(s);}).catch(function(){});
}
// Same as the inspector's planCoverage: every event the plan defines, against the captures checked by
// this same version (events stamped by an earlier plan say nothing about this one). Verdict 0 breaks,
// 1 matches, 2 not seen; ordered by verdict, then plan order.
function planCoverage(list,p){
  var checked=Object.create(null);
  list.forEach(function(e){
    if(e.type!=='analytics'||e.planVersion!==p.version)return;
    var key=e.name+'\u0000'+e.channel;
    var c=checked[key]||(checked[key]={captured:0,breaking:0});
    c.captured++;
    if(breaksPlan(e))c.breaking++;
  });
  return p.events.map(function(ev,i){
    var c=checked[ev.name+'\u0000'+ev.channel]||{captured:0,breaking:0};
    return {event:ev,captured:c.captured,breaking:c.breaking,verdict:c.breaking?0:(c.captured?1:2),order:i};
  }).sort(function(a,b){return (a.verdict-b.verdict)||(a.order-b.order);});
}
function countVerdicts(cov){var n=[0,0,0];cov.forEach(function(c){n[c.verdict]++;});return n;}
function planChipHtml(cov){
  if(!plan)return icon('plan',16)+'No plan';
  var breaking=countVerdicts(cov)[0];
  return icon('plan',16)+esc('Plan '+plan.version)+(breaking?"<span class='countpill'>"+breaking+" break it</span>":'');
}
var UNSEEN_PREVIEW=3,VERDICT_CLASS=['breaks','matches','unseen'];
function coverageRowHtml(c){
  var meta=c.verdict===0?(c.breaking+' of '+c.captured):(c.verdict===1?(c.captured+' seen'):'not seen');
  return "<div class='covrow "+VERDICT_CLASS[c.verdict]+"'><span class='cdot "+VERDICT_CLASS[c.verdict]+"'></span><code class='mono'>"+esc(c.event.name)+"</code><span class='cmeta'>"+esc(c.event.channel)+" · "+meta+"</span></div>";
}
function planSummaryHtml(cov){
  if(!plan)return "<div class='pintro'><b>Check events against the plan</b><span>Load the analytics team's tracking plan and every event it defines, captured from then on, is checked for missing or wrong parameters.</span></div>";
  var n=countVerdicts(cov),total=plan.events.length;
  var h="<div class='ploaded'><div class='grow'><div class='title'>Version "+esc(plan.version)+" is loaded</div><div class='sub'>"+total+(total===1?' event':' events')+"</div></div><button class='dangerbtn' onclick='clearPlan()'>Clear</button></div>";
  h+="<div class='pcounts'>"+[[n[0],'break it'],[n[1],'match it'],[n[2],'not seen yet']].map(function(x,i){
    return "<div class='pcount "+VERDICT_CLASS[i]+"'><b>"+x[0]+"</b><span>"+x[1]+"</span></div>";
  }).join('')+"</div>";
  if(total){
    var seen=cov.filter(function(c){return c.verdict<2;}),unseen=cov.filter(function(c){return c.verdict===2;});
    var shown=showAllUnseen?unseen:unseen.slice(0,UNSEEN_PREVIEW);
    h+="<div class='cov'>"+seen.concat(shown).map(coverageRowHtml).join('');
    if(shown.length<unseen.length)h+="<button class='covmore' onclick='showUnseen()'>Show "+(unseen.length-shown.length)+" more</button>";
    h+="</div>";
  }
  return h+"<div class='pnote'>Events the plan doesn't define aren't checked.</div>";
}
function showUnseen(){showAllUnseen=true;render();}
function renderPlan(){
  var cov=plan?planCoverage(entries,plan):[];
  var chip=document.getElementById('planBtn');
  chip.className='planchip'+(countVerdicts(cov)[0]?' breaks':'');
  chip.innerHTML=planChipHtml(cov);
  if(!planOpen)return;
  // Rewritten only when it changes: stream frames re-render several times a second, and replacing
  // the Clear button between mousedown and mouseup would swallow the click.
  var html=planSummaryHtml(cov);
  if(html!==shownSummary)document.getElementById('planSummary').innerHTML=shownSummary=html;
  document.getElementById('planLoadLabel').textContent=plan?'Replace the plan':'Load a plan';
}
// Inline, under the field: the message names what is wrong, and that nothing was lost.
function planError(msg){
  var el=document.getElementById('planError');
  document.getElementById('planText').classList.toggle('invalid',!!msg);
  if(!msg){el.hidden=true;el.innerHTML='';return;}
  msg=msg.replace(/\.$/,'');
  // Sentence case, except when the message opens with a path into the plan such as events[0].name.
  if(/^[a-z]+ /.test(msg))msg=msg.charAt(0).toUpperCase()+msg.slice(1);
  var text=msg+(plan?'. Nothing changed: '+plan.version+' is still loaded.':'.');
  el.innerHTML=icon('alert',16)+'<span>'+esc(text)+'</span>';
  el.hidden=false;
}
function setPlanOpen(open){
  planOpen=open;showAllUnseen=false;
  document.getElementById('planPanel').hidden=!open;
  document.getElementById('planScrim').hidden=!open;
}
function openPlan(){planError('');setPlanOpen(true);refreshPlan();render();}
function closePlan(){planError('');setPlanOpen(false);}
function syncLoadButton(){document.getElementById('planLoad').disabled=!document.getElementById('planText').value.trim();}
function setPlanText(text){document.getElementById('planText').value=text;planError('');syncLoadButton();}
function readPlanFile(file){
  if(!file)return;
  var reader=new FileReader();
  reader.onload=function(){setPlanText(reader.result);};
  reader.readAsText(file);
}
function planRequest(path,body){
  planSeq++;
  return fetch(apiUrl(path),{method:'POST',headers:{'X-Verivent':'1','Content-Type':'application/json'},body:body}).then(function(r){
    return r.text().then(function(t){if(!r.ok)throw new Error(t||('rejected: '+r.status));return JSON.parse(t);});
  });
}
function loadPlan(){
  var text=document.getElementById('planText').value;
  if(!text.trim())return;
  planRequest('/api/tracking-plan',text).then(function(s){
    setPlanText('');showPlan(s);toast('Plan '+s.version+' loaded');
  }).catch(function(err){planError(err.message);});
}
function clearPlan(){
  planRequest('/api/tracking-plan/clear','').then(function(s){showPlan(s);planError('');}).catch(function(err){planError(err.message);});
}
function clearAll(){
  // No local reseed: the post-clear stream frame prunes everything, without racing new captures.
  fetch(apiUrl('/api/clear'),{method:'POST',headers:{'X-Verivent':'1'}}).then(function(r){
    if(!r.ok)throw new Error('clear rejected: '+r.status);
    selectedId=null;toast('Cleared');render();
  }).catch(function(){toast('Clear failed');});
}
// The device writes the file, the same the inspector's Export does: it opens with the session's
// source (platform and versions), which the page doesn't have. It holds every captured event, paused
// or not, so the device's file decides whether there is anything to export, not the page's view.
function exportJson(){
  fetch(apiUrl('/api/export')).then(function(r){
    if(!r.ok)throw new Error('export rejected: '+r.status);
    return r.text();
  }).then(function(data){
    var count=JSON.parse(data).filter(function(e){return e.type==='analytics';}).length;
    if(!count){toast('No analytics events to export');return;}
    var blob=new Blob([data],{type:'application/json'});
    var url=URL.createObjectURL(blob);
    var a=document.createElement('a');
    a.href=url;a.download='verivent-export.json';
    document.body.appendChild(a);a.click();document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast('Exported '+count+' events');
  }).catch(function(){toast('Export failed');});
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
  var tools=body?("<div class='shtools'>"+segHtml(rawBody,'setRaw')+
    "<button class='copybtn' data-copybody='"+esc(key)+"'>"+icon('copy',14)+" Copy</button></div>"):'';
  h+="<div class='shrow' style='margin-top:0'><div class='section'>"+esc(label)+"</div>"+tools+"</div>";
  if(!body){
    h+="<div class='sub'>Empty</div>";
  }else{
    if(truncated)h+="<div class='sub'>Showing first "+body.length+" of "+byteCount+" bytes</div>";
    h+="<div class='code mono' style='margin-top:8px'>"+(rawBody?esc(compact(body)):hl(pretty(body)))+"</div>";
  }
  h+="</div>";
  return h;
}
function detailBackHtml(){return "<button class='dback' onclick='sel(null)' aria-label='Back to events'>"+icon('back',18)+"Events</button>";}
function networkDetailHtml(e){
  var h="<div class='dwrap'>"+detailBackHtml();
  h+="<div class='dheadrow'><div><div class='dtitle'>"+esc(e.method)+" "+esc(e.path||'/')+"</div><div class='dsub'>"+esc(e.host)+"</div></div>"+
    detailActionsHtml("<button class='copybtn' data-curl='"+esc(e.id)+"'>"+icon('copy',14)+" Copy as cURL</button>")+"</div>";
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
function verdictHtml(e){
  if(!checkedBy(e))return '';
  var issues=e.issues||[];
  if(!issues.length)return "<div class='verdict matches'><div class='vtitle'>Matches tracking plan "+esc(e.planVersion)+"</div><div class='vsub'>Every parameter is what the plan expects</div></div>";
  var h="<div class='verdict breaks'><div class='vtitle'>Breaks tracking plan "+esc(e.planVersion)+" · "+problems(issues.length)+"</div>";
  issues.forEach(function(i){h+="<div class='vissue'><code class='mono'>"+esc(i.property)+"</code><span>"+esc(i.message)+"</span></div>";});
  return h+"</div>";
}
function analyticsDetailHtml(e){
  var pair=pal(e.channel);
  var h="<div class='dwrap'>"+detailBackHtml();
  h+="<div class='dheadrow'><div><div class='dtitle'>"+esc(e.name)+"</div><div class='dsub'>event · "+esc(e.channel)+"</div></div>"+
    detailActionsHtml("<button class='copybtn' data-payload='"+esc(e.id)+"'>"+icon('copy',14)+" Copy payload</button>")+"</div>";
  h+="<div class='pillrow'>"+pill(e.channel,chanStyle(pair),false);
  (e.annotations||[]).forEach(function(a){
    var style=a==='ecommerce'?tintStyle('ecom'):tintStyle('neu');
    h+=pill(a,style,false);
  });
  h+=pill(clock(e.ts),tintStyle('neu'),true);
  h+="</div>";
  h+=verdictHtml(e);
  h+=propertiesBlockHtml('Parameters',e.properties,rawParams,'setRawParams',checkedBy(e)?e.issues:null);
  (e.sections||[]).forEach(function(s,i){
    h+=propertiesBlockHtml(s.title,s.properties,!!rawSections[i],'setRawSection.bind(null,'+i+')',null,i);
  });
  h+="</div>";
  return h;
}
function detailActionsHtml(copyBtn){
  return "<div class='dactions'>"+copyBtn+"<button class='iconbtn closebtn' onclick='sel(null)' aria-label='Close' title='Close'>"+icon('close',16)+"</button></div>";
}
// A block with a [section] index starts closed and counts its properties, as in the inspector.
function propertiesBlockHtml(label,list,raw,setter,issues,section){
  var collapsible=section!=null,open=!collapsible||!!openSections[section];
  var head=collapsible?
    ("<div class='section stoggle' data-section='"+section+"'><span class='chev"+(open?' open':'')+"'>"+icon('chevron',12)+"</span>"+esc(label)+" · "+(list||[]).length+"</div>"):
    ("<div class='section'>"+esc(label)+"</div>");
  var h="<div class='pblock'><div class='shrow'>"+head+(open?segHtml(raw,setter):'')+"</div>";
  if(!open)return h+"</div>";
  if(!(list||[]).length&&!(issues||[]).length){
    h+="<div class='sub'>None</div>";
  }else if(raw){
    var obj={};(list||[]).forEach(function(k){obj[k.name]=k.redacted?'•••':k.value;});
    h+="<div class='code mono' style='margin-top:8px'>"+hl(JSON.stringify(obj,null,2))+"</div>";
  }else{
    h+="<div class='card' style='margin-top:8px'>"+kvHtml(list,true,issues)+"</div>";
  }
  return h+"</div>";
}
// The list and the detail are only replaced when their markup changes, like the plan summary: a
// stream frame arrives several times a second, and rewriting an unchanged detail would clear the
// text someone is selecting in it.
var shownList=null,shownDetail=null;
function renderDetail(){
  var d=document.getElementById('detail');
  var e=entryById(selectedId);
  document.querySelector('main').classList.toggle('has-detail',!!e);
  d.className=e?'detail open':'detail';
  var html=e?(e.type==='network'?networkDetailHtml(e):analyticsDetailHtml(e)):'';
  if(html!==shownDetail)d.innerHTML=shownDetail=html;
}
function sel(id){
  if(id!==selectedId){detailTab='overview';rawBody=false;rawParams=false;rawSections={};openSections={};}
  selectedId=id;render();
}
function setType(t){typeFilter=t;if(t==='network'){channelFilter=null;breaksFilter=false;}render();}
function toggleBreaks(){if(breaksFilter){breaksFilter=false;}else{breaksFilter=true;typeFilter='analytics';}render();}
function setChan(c){if(channelFilter===c){channelFilter=null;}else{channelFilter=c;typeFilter='analytics';}render();}

function render(){
  document.getElementById('count').textContent=entries.length;
  document.getElementById('dot').className='dot'+(paused||unreachable?' off':'');
  var chans=[];entries.forEach(function(e){if(e.type==='analytics'&&chans.indexOf(e.channel)<0)chans.push(e.channel);});
  var tc=[['all','All'],['network','Network'],['analytics','Analytics']].map(function(t){
    return "<span class='chip"+(typeFilter===t[0]?' on':'')+"' onclick='setType(\""+t[0]+"\")'>"+t[1]+"</span>";
  }).join('');
  // Offered while there is something to show, so a filter left on never hides everything.
  var breaking=entries.filter(breaksPlan).length;
  if(breaking||breaksFilter)tc+="<span class='chip alert"+(breaksFilter?' on':'')+"' onclick='toggleBreaks()'>Breaks plan · "+breaking+"</span>";
  document.getElementById('typechips').innerHTML=tc;
  document.getElementById('chanchips').innerHTML=chans.map(function(c){
    var pair=pal(c);
    var on=channelFilter===c;
    var style=on?('background:'+pair[0]+';color:'+pair[1]+';border:2px solid '+pair[1]):'';
    return "<span class='chip"+(on?' on':'')+"' style='"+style+"' data-chan='"+esc(c)+"'><span class='chipdot' style='background:"+pair[1]+"'></span>"+esc(c)+"</span>";
  }).join('');
  var list=listHtml()||emptyHtml();
  if(list!==shownList)document.getElementById('list').innerHTML=shownList=list;
  renderDetail();
  renderPlan();
  document.getElementById('pauseIcon').style.display=paused?'none':'inline-flex';
  document.getElementById('playIcon').style.display=paused?'inline-flex':'none';
}

document.getElementById('pauseIcon').innerHTML=icon('pause',18);
document.getElementById('playIcon').innerHTML=icon('play',18);
document.getElementById('clearBtn').innerHTML=icon('clear',18);
document.getElementById('exportBtn').innerHTML=icon('export',18);
document.getElementById('planClose').innerHTML=icon('close',16);
document.getElementById('planDropIcon').innerHTML=icon('upload',24);
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
  var cb=ev.target.closest('[data-copybody]');
  if(cb){copyBody(cb.getAttribute('data-copybody'));return;}
  var hd=ev.target.closest('[data-headers]');
  if(hd){toggleHeaders(hd.getAttribute('data-headers'));return;}
  var sc=ev.target.closest('[data-section]');
  if(sc){toggleSection(sc.getAttribute('data-section'));return;}
  var r=ev.target.closest('[data-id]');
  if(r)sel(r.getAttribute('data-id'));
});
document.getElementById('planFile').addEventListener('change',function(ev){readPlanFile(ev.target.files&&ev.target.files[0]);ev.target.value='';});
var drop=document.getElementById('planDrop');
drop.addEventListener('dragover',function(ev){ev.preventDefault();drop.classList.add('over');});
drop.addEventListener('dragleave',function(){drop.classList.remove('over');});
drop.addEventListener('drop',function(ev){ev.preventDefault();drop.classList.remove('over');readPlanFile(ev.dataTransfer.files&&ev.dataTransfer.files[0]);});
document.getElementById('planText').addEventListener('input',function(){planError('');syncLoadButton();});
document.addEventListener('keydown',function(ev){if(ev.key==='Escape'&&planOpen)closePlan();});
// The plan isn't streamed: re-read it when the page regains focus or an event arrives stamped by
// another version (it was loaded or replaced on the device).
window.addEventListener('focus',refreshPlan);
// Asks once per unknown stamp: events checked by a plan cleared since keep theirs, and a full frame
// (the first one, or after a reconnect) carries them all again.
var stampAsked=null;
function planChanged(frame){
  var stamp=null;
  (frame.entries||[]).forEach(function(e){
    if(e.planVersion!=null&&e.planVersion!==(plan&&plan.version)&&e.planVersion!==stampAsked)stamp=e.planVersion;
  });
  if(stamp!=null)stampAsked=stamp;
  return stamp!=null;
}
refreshPlan();
var es=new EventSource(apiUrl('/api/stream'));
// A frame is proof the server is back, whatever the seeding fetch reported.
es.onmessage=function(ev){
  var frame=JSON.parse(ev.data);
  setUnreachable(false);applyFrame(frame);
  if(planChanged(frame))refreshPlan();
  if(!paused){setEntries(latest,streamById);render();}
};
// The browser retries on its own; until a frame arrives again, the page says it isn't live.
es.onerror=function(){setUnreachable(true);};
fetch(apiUrl('/api/entries')).then(function(r){
  if(!r.ok)throw new Error('entries rejected: '+r.status);
  return r.json();
}).then(function(j){if(!latest){setEntries(seedStream(j));render();}}).catch(function(){
  setUnreachable(true);
});
render();
