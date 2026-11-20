'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const icon = id => `<svg aria-hidden="true"><use href="#${id}"/></svg>`;
  // Fixtures only. Android integration must supply selected packages and their
  // actual PackageManager icons asynchronously; no fixed app list in the APK.
  const examples = [
    { id:'youtube', name:'YouTube', image:'youtube.svg' },
    { id:'spotify', name:'Spotify', image:'spotify.svg' },
    { id:'local', name:'Yerel medya', symbol:'folder' },
    { id:'vlc', name:'VLC', image:'vlcmediaplayer.svg' },
    { id:'tv', name:'Güneş TV', letter:'G' },
    { id:'firefox', name:'Firefox', image:'firefoxbrowser.svg' },
    { id:'radio', name:'Radyo', symbol:'radio', color:'#966142' },
    { id:'long', name:'Çok uzun isimli örnek medya uygulaması', letter:'M',color:'#4b6775' },
    { id:'offline', name:'Podcast', symbol:'play',color:'#735986' }
  ];
  const state = { page:0, filter:'all', recent:[], active:null, fit:'fit', theme:'system', beforeStop:'launcher', menuFocus:null };
  const systemTheme = matchMedia('(prefers-color-scheme: light)');
  const compact = matchMedia('(max-height: 560px) and (min-width: 651px)');
  const short = matchMedia('(max-height: 460px) and (min-width: 651px)');
  const narrow = matchMedia('(max-width: 650px)');
  function pageSize(){ return short.matches ? 3 : narrow.matches ? 4 : 6; }
  function iconMarkup(app) {
    const inside=app.image ? `<img src="assets/${app.image}" alt="" width="48" height="48">` : app.symbol ? icon(app.symbol) : app.letter;
    return `<span class="app-icon ${app.id}${app.letter?' letter':''}"${app.color?` style="--icon-color:${app.color}"`:''}>${inside}</span>`;
  }
  function theme(value) {
    state.theme=value;
    document.documentElement.dataset.theme=value==='system'?(systemTheme.matches?'light':'dark'):value;
    document.querySelectorAll('[data-theme-choice]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.themeChoice===value)));
    try{localStorage.setItem('car-app-hub-preview-theme',value);}catch{}
  }
  function list(){ return state.filter==='recent' ? state.recent.map(id=>examples.find(a=>a.id===id)).filter(Boolean) : examples; }
  function renderApps(focusId) {
    const apps=list(), count=Math.ceil(apps.length/pageSize());
    state.page=Math.min(state.page,Math.max(0,count-1));
    $('app-grid').hidden=!apps.length; $('empty').hidden=!!apps.length;
    $('pagination').hidden=count<2;
    $('all-apps').setAttribute('aria-pressed',String(state.filter==='all'));
    $('recent-apps').setAttribute('aria-pressed',String(state.filter==='recent'));
    $('app-grid').replaceChildren();
    for(const app of apps.slice(state.page*pageSize(),(state.page+1)*pageSize())) {
      const button=document.createElement('button');button.className='app-tile';button.dataset.app=app.id;
      button.innerHTML=iconMarkup(app)+`<span class="app-name">${app.name}</span>`;
      button.addEventListener('click',()=>openApp(app));$('app-grid').append(button);
    }
    $('page-label').textContent=`${state.page+1} / ${Math.max(1,count)}`;
    $('previous').disabled=state.page===0; $('next').disabled=state.page>=count-1;
    if(focusId){document.querySelector(`[data-app="${focusId}"]`)?.focus();}
  }
  function screen(id){['launcher','content','ended'].forEach(name=>$(name).hidden=name!==id);}
  function openApp(app) {
    state.menuFocus=app.id;state.active=app;state.recent=[app.id,...state.recent.filter(id=>id!==app.id)].slice(0,9);
    $('sample-title').textContent=app.name;$('selected-title').textContent=app.name;$('sample-icon').innerHTML=iconMarkup(app);
    screen('content');$('open-menu').focus();
  }
  function menu(){screen('launcher');renderApps(state.menuFocus);state.active=null;}
  function closeDialog(button){button.closest('dialog').close();}
  function contentSize(){
    const area=$('content-area'), w=area.clientWidth,h=area.clientHeight;
    if(!w||!h)return;
    const scale=state.fit==='fill'?Math.max(w/1600,h/900):Math.min(w/1600,h/900);
    $('sample-content').style.width=`${1600*scale}px`;
    $('sample-content').style.height=`${900*scale}px`;
  }
  function fit(value){state.fit=value;$('content-area').dataset.fit=value;contentSize();document.querySelectorAll('[data-fit]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.fit===value)));}
  $('all-apps').onclick=()=>{state.filter='all';state.page=0;renderApps();};
  $('recent-apps').onclick=()=>{state.filter='recent';state.page=0;renderApps();};
  $('show-all').onclick=()=>{state.filter='all';state.page=0;renderApps();$('app-grid').querySelector('button')?.focus();};
  function page(delta){state.page+=delta;renderApps();$('app-grid').querySelector('button')?.focus();}
  $('previous').onclick=()=>page(-1);$('next').onclick=()=>page(1);
  $('appearance').onclick=()=>$('appearance-dialog').showModal();
  $('fit-control').onclick=()=>$('fit-dialog').showModal();
  document.querySelectorAll('.close-dialog').forEach(b=>b.onclick=()=>closeDialog(b));
  document.querySelectorAll('[data-theme-choice]').forEach(b=>b.onclick=()=>theme(b.dataset.themeChoice));
  document.querySelectorAll('[data-fit]').forEach(b=>b.onclick=()=>{fit(b.dataset.fit);$('fit-dialog').close();});
  $('open-menu').onclick=menu;$('back').onclick=menu;
  document.querySelectorAll('.end-sharing').forEach(b=>b.onclick=()=>{state.beforeStop=$('content').hidden?'launcher':'content';$('stop-dialog').showModal();});
  $('cancel-stop').onclick=()=>$('stop-dialog').close();
  $('confirm-stop').onclick=()=>{$('stop-dialog').close();state.active=null;screen('ended');$('restart').focus();};
  $('restart').onclick=()=>{screen('launcher');renderApps();$('app-grid').querySelector('button')?.focus();};
  document.addEventListener('keydown',event=>{
    if(event.key==='Escape'&&!document.querySelector('dialog[open]')&&!$('content').hidden){event.preventDefault();menu();return;}
    const directions={ArrowRight:[1,0],ArrowLeft:[-1,0],ArrowDown:[0,1],ArrowUp:[0,-1]};
    const direction=directions[event.key];if(!direction)return;
    const scope=document.querySelector('dialog[open]')||document.querySelector('.screen:not([hidden])');
    const buttons=[...scope.querySelectorAll('button:not(:disabled)')].filter(b=>b.getClientRects().length&&b.getBoundingClientRect().width>0);
    const current=document.activeElement;
    if(!buttons.includes(current)){buttons[0]?.focus();event.preventDefault();return;}
    const rect=current.getBoundingClientRect(), x=rect.x+rect.width/2,y=rect.y+rect.height/2;
    const candidates=buttons.filter(b=>b!==current).map(b=>{const r=b.getBoundingClientRect();const dx=r.x+r.width/2-x,dy=r.y+r.height/2-y;return{b,dx,dy};}).filter(({dx,dy})=>dx*direction[0]+dy*direction[1]>8);
    candidates.sort((a,b)=>{
      const cost=c=>Math.hypot(c.dx,c.dy)+Math.abs(c.dx*direction[1]-c.dy*direction[0])*2;
      return cost(a)-cost(b);
    });
    if(candidates[0]){candidates[0].b.focus();event.preventDefault();}
  });
  const resize=()=>{state.page=0;renderApps();};compact.addEventListener('change',resize);short.addEventListener('change',resize);narrow.addEventListener('change',resize);
  systemTheme.addEventListener('change',()=>{if(state.theme==='system')theme('system');});
  new ResizeObserver(contentSize).observe($('content-area'));
  let saved;try{saved=localStorage.getItem('car-app-hub-preview-theme');}catch{}
  theme(['dark','light','system'].includes(saved)?saved:'dark');fit('fit');renderApps();
})();
