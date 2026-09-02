/* 공통 셸(사이드바·상단바·프로필 메뉴) — 추후 React Layout 컴포넌트에 해당 */
const LOGO_FULL=`<svg class="full" width="72" height="22" viewBox="0 0 172 52" fill="none" aria-label="iNEB">
  <rect x="2" y="20" width="9" height="30" rx="1.5" fill="#3B9EFF"/><circle cx="6.5" cy="10" r="6.5" fill="#7CC0FF"/>
  <rect x="3.5" y="12" width="6" height="16" rx="1" fill="#7CC0FF"/>
  <path d="M24 50V2h9l22 34V2h9v48h-9L33 16v34h-9z" fill="#3B9EFF"/>
  <rect x="74" y="2" width="34" height="9" rx="1" fill="#3B9EFF"/><rect x="74" y="21.5" width="34" height="9" rx="1" fill="#3B9EFF"/>
  <rect x="74" y="41" width="34" height="9" rx="1" fill="#3B9EFF"/>
  <path d="M118 2h26c9 0 15 5 15 12.5 0 5-2.6 8.6-6.6 10.5 5 1.8 8.6 6 8.6 12C161 45 154.6 50 145 50h-27V2zm9 19.5h16c4 0 6.5-2.2 6.5-5.5s-2.5-5.5-6.5-5.5h-16v11zm0 20h17c4.4 0 7-2.4 7-6s-2.6-6-7-6h-17v12z" fill="#3B9EFF"/></svg>
<svg class="mark" width="15" height="26" viewBox="0 0 13 52" fill="none"><rect x="2" y="20" width="9" height="30" rx="1.5" fill="#3B9EFF"/><circle cx="6.5" cy="10" r="6.5" fill="#7CC0FF"/><rect x="3.5" y="12" width="6" height="16" rx="1" fill="#7CC0FF"/></svg>`;

const IC={
 dash:'<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="8" height="8" rx="2"/><rect x="13" y="3" width="8" height="5" rx="2"/><rect x="13" y="10" width="8" height="11" rx="2"/><rect x="3" y="13" width="8" height="8" rx="2"/></svg>',
 key:'<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="7.5" cy="15.5" r="5"/><path d="m11 12 9.6-9.6"/><path d="m15.2 7.8 3 3L22 7l-3-3"/></svg>',
 test:'<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 3h6M10 3v6L4.5 18a2.4 2.4 0 0 0 2.1 3.5h10.8a2.4 2.4 0 0 0 2.1-3.5L14 9V3"/></svg>',
 user:'<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6"/><circle cx="17" cy="9" r="2.6"/><path d="M16.5 14.2c3 .3 5 2.5 5 5.3"/></svg>',
 notice:'<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 5h16v12H8l-4 4V5z"/><path d="M8 9h8M8 12.5h5"/></svg>',
 audit:'<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3 4.5 6v5c0 5 3.2 8.6 7.5 10 4.3-1.4 7.5-5 7.5-10V6L12 3z"/><path d="m9 11.5 2.2 2.2L15.5 9"/></svg>',
 sun:'<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>',
 moon:'<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.5 14.5A8.5 8.5 0 1 1 9.5 3.5a7 7 0 0 0 11 11z"/></svg>',
 sys:'<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M9 21h6m-3-4v4"/></svg>',
 out:'<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 4h-8v16h8M10 12h11m0 0-3.5-3.5M21 12l-3.5 3.5"/></svg>',
 chk:'<svg class="chk" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6"><path d="m4.5 12.5 5 5 10-11"/></svg>',
};

const NAV=[
 {sec:null,items:[{href:'dashboard.html',key:'dashboard',ic:'dash',label:'대시보드'}]},
 {sec:'키 관리',items:[
   {href:'keys.html',key:'keys',ic:'key',label:'키 목록'},
   {href:'test.html',key:'test',ic:'test',label:'동작 테스트'}]},
 {sec:'운영 관리',items:[
   {href:'users.html',key:'users',ic:'user',label:'사용자 관리'},
   {href:'notices.html',key:'notices',ic:'notice',label:'공지사항'},
   {href:'audit.html',key:'audit',ic:'audit',label:'감사 로그'}]},
];

function renderShell(active,crumb){
  const nav=NAV.map(g=>{
    const sec=g.sec?`<div class="nav-sec">${g.sec}</div>`:'';
    const its=g.items.map(i=>`<a class="nav-it ${i.key===active?'on':''}" href="${i.href}" data-label="${i.label}">${IC[i.ic]}<span>${i.label}</span></a>`).join('');
    return sec+its;
  }).join('');
  document.getElementById('shell').innerHTML=`
  <aside class="side">
    <div class="brand">${LOGO_FULL}<button class="tgl side-tgl" onclick="toggleSide()" title="사이드바 열기/닫기" aria-label="사이드바 열기/닫기"><svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="2.5"/><path d="M9.5 4v16"/></svg></button></div>
    ${nav}
    <div class="side-profile">
      <div class="me-menu" id="meMenu" role="menu">
        <div class="mm-head"><b>류재민</b><span>ADMIN · admin@ineb.co.kr</span></div>
        <div class="mm-sec">테마 설정</div>
        <button class="mm-it" data-mode="light" onclick="setThemeMode('light')">${IC.sun}라이트 모드${IC.chk}</button>
        <button class="mm-it" data-mode="dark" onclick="setThemeMode('dark')">${IC.moon}다크 모드${IC.chk}</button>
        <button class="mm-it" data-mode="system" onclick="setThemeMode('system')">${IC.sys}시스템 설정${IC.chk}</button>
        <div class="mm-div"></div>
        <button class="mm-it danger" onclick="doLogout()">${IC.out}로그아웃</button>
      </div>
      <button class="sp-btn" id="avatarBtn" onclick="toggleMeMenu(event)" aria-haspopup="menu" aria-label="프로필 메뉴">
        <span class="avatar-btn">류</span>
        <span class="sp-who"><b>류재민</b><span>ADMIN</span></span>
        <svg class="sp-chev" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="m7 14.5 5-5 5 5"/></svg>
      </button>
    </div>
  </aside>
  <div class="main">
    <header class="topbar">
      <div class="crumb">홈 / ${crumb}</div>
      <div class="sp"></div>
    </header>
    <div class="content" id="content"></div>
  </div>`;
  document.getElementById('content').append(document.getElementById('page-body').content);
  const t=document.createElement('div');t.id='toast';t.innerHTML='<span class="dot"></span><span id="toastMsg"></span>';
  document.body.append(t);
  renderThemeChecks();
  document.addEventListener('click',e=>{
    const menu=document.getElementById('meMenu');
    if(!menu.classList.contains('open'))return;
    if(!e.target.closest('.side-profile')){menu.classList.remove('open');document.getElementById('avatarBtn').classList.remove('open');}
  });
}
function renderThemeChecks(){
  const m=getThemeMode();
  document.querySelectorAll('.mm-it[data-mode]').forEach(b=>b.classList.toggle('on',b.dataset.mode===m));
}
function toggleMeMenu(e){
  e.stopPropagation();
  document.getElementById('meMenu').classList.toggle('open');
  document.getElementById('avatarBtn').classList.toggle('open');
}
function toggleSide(){
  const c=document.documentElement.classList.toggle('collapsed');
  try{localStorage.setItem('kms.side',c?'collapsed':'open');}catch(e){}
}
function doLogout(){location.href='login.html';}

let _tt;
function toast(msg){
  document.getElementById('toastMsg').textContent=msg;
  const t=document.getElementById('toast');t.classList.add('show');
  clearTimeout(_tt);_tt=setTimeout(()=>t.classList.remove('show'),2600);
}
function openModal(id){document.getElementById(id).classList.add('open')}
function closeModal(id){document.getElementById(id).classList.remove('open')}
function bkClose(e,el){if(e.target===el)el.classList.remove('open')}
function qs(name){return new URLSearchParams(location.search).get(name)}

/* ---- 목록 자동 페이징 — 행이 화면을 벗어나지 않게 남은 높이로 페이지 크기 계산 (React useAutoPageSize/Pager 와 동일 규칙)
   행 높이는 화면별 고정 상수(감사 46 / 키 50 / 사용자 58) — 실측하면 데이터 전후로 값이 출렁여 결정적으로 만든다 ---- */
function autoPageSize(wrapEl,rowH,min=3){
  const body=wrapEl.querySelector('tbody');
  const top=(body||wrapEl).getBoundingClientRect().top+window.scrollY;
  const avail=window.innerHeight-top-56-60; // pager 높이 + content 하단 여백
  return Math.max(min,Math.floor(avail/rowH));
}
function pageWindow(page,totalPages){
  const last=Math.max(totalPages,1)-1;
  if(last<=6)return Array.from({length:last+1},(_,i)=>i);
  const ps=[...new Set([0,page-1,page,page+1,last].filter(p=>p>=0&&p<=last))].sort((a,b)=>a-b);
  const out=[];let prev=-1;
  for(const p of ps){if(prev>=0&&p-prev>1)out.push('…');out.push(p);prev=p;}
  return out;
}
function renderPager(el,total,page,totalPages,goFn,unit='건'){
  totalPages=Math.max(totalPages,1);
  el.innerHTML=`<span class="pinfo">총 ${total}${unit} · ${page+1}/${totalPages} 페이지</span>`
    +`<button ${page===0?'disabled':''} onclick="${goFn}(${page-1})">‹</button>`
    +pageWindow(page,totalPages).map(p=>p==='…'?'<button disabled>…</button>':`<button class="${p===page?'on':''}" onclick="${goFn}(${p})">${p+1}</button>`).join('')
    +`<button ${page>=totalPages-1?'disabled':''} onclick="${goFn}(${page+1})">›</button>`;
}
