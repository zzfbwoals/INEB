/* 아이콘: lucide UMD(assets/lucide.min.js). 정적 마크업·innerHTML 렌더 모두 <i data-lucide="name" width=.. height=..> 로 쓰면
   아래 옵저버가 svg 로 치환한다(속성·class 는 svg 로 복사됨). React 앱의 lucide-react 와 같은 아이콘 세트 */
(function(){
  if(typeof lucide==='undefined')return;
  const has=n=>n.nodeType===1&&(n.matches('i[data-lucide]')||n.querySelector('i[data-lucide]'));
  new MutationObserver(ms=>{for(const m of ms)for(const n of m.addedNodes)if(has(n)){lucide.createIcons();return}})
    .observe(document.documentElement,{childList:true,subtree:true});
  document.addEventListener('DOMContentLoaded',()=>lucide.createIcons());
})();
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
 dash:'<i data-lucide="layout-dashboard" width="16" height="16" stroke-width="2"></i>',
 key:'<i data-lucide="key-round" width="16" height="16" stroke-width="2"></i>',
 test:'<i data-lucide="flask-conical" width="16" height="16" stroke-width="2"></i>',
 user:'<i data-lucide="users" width="16" height="16" stroke-width="2"></i>',
 notice:'<i data-lucide="message-square-text" width="16" height="16" stroke-width="2"></i>',
 audit:'<i data-lucide="shield-check" width="16" height="16" stroke-width="2"></i>',
 sun:'<i data-lucide="sun" width="14" height="14" stroke-width="2"></i>',
 moon:'<i data-lucide="moon" width="14" height="14" stroke-width="2"></i>',
 sys:'<i data-lucide="monitor" width="14" height="14" stroke-width="2"></i>',
 out:'<i data-lucide="log-out" width="14" height="14" stroke-width="2"></i>',
 chk:'<i data-lucide="check" width="13" height="13" class="chk" stroke-width="2.6"></i>',
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

function renderShell(active){
  const nav=NAV.map(g=>{
    const sec=g.sec?`<div class="nav-sec">${g.sec}</div>`:'';
    const its=g.items.map(i=>`<a class="nav-it ${i.key===active?'on':''}" href="${i.href}" data-label="${i.label}">${IC[i.ic]}<span>${i.label}</span></a>`).join('');
    return sec+its;
  }).join('');
  document.getElementById('shell').innerHTML=`
  <aside class="side">
    <div class="brand">${LOGO_FULL}<button class="tgl side-tgl" onclick="toggleSide()" title="사이드바 열기/닫기" aria-label="사이드바 열기/닫기"><i data-lucide="panel-left" width="17" height="17" stroke-width="2"></i></button></div>
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
        <i data-lucide="chevron-up" width="13" height="13" class="sp-chev" stroke-width="2.2"></i>
      </button>
    </div>
  </aside>
  <div class="main">
    <div class="content" id="content"></div>
  </div>`;
  document.getElementById('content').append(document.getElementById('page-body').content);
  const t=document.createElement('div');t.id='toast';t.innerHTML='<span class="dot"></span><span id="toastMsg"></span>';
  document.body.append(t);
  renderThemeChecks();
  enableColResize();
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

/* ---- 복사·다운로드 — uid·공개키·암호문 등 원클릭 (상용 KMS 콘솔 공통 관례) ---- */
function copyText(text){
  // navigator.clipboard 는 HTTPS·localhost 에서만 존재 → HTTP 배포(192.168.200.52)는 execCommand 폴백
  const fallback=()=>{const ta=document.createElement('textarea');ta.value=text;ta.setAttribute('readonly','');
    ta.style.cssText='position:fixed;top:0;left:0;width:1px;height:1px;opacity:0';document.body.appendChild(ta);ta.select();
    let ok=false;try{ok=document.execCommand('copy');}finally{document.body.removeChild(ta);}return ok?Promise.resolve():Promise.reject();};
  (window.isSecureContext&&navigator.clipboard?navigator.clipboard.writeText(text).catch(fallback):fallback())
    .then(()=>toast('복사되었습니다')).catch(()=>toast('복사에 실패했습니다'));
}
function copyEl(id){const el=document.getElementById(id);copyText((el.innerText||el.textContent).trim());}
function downloadText(name,content,type='text/plain'){
  const a=document.createElement('a');
  a.href=URL.createObjectURL(new Blob([content],{type}));
  a.download=name;a.click();URL.revokeObjectURL(a.href);
}
/* 상대 시간 — "3일 전" (정확 시각은 title 로) */
function relTime(s){
  if(!s)return '—';
  const diff=Date.now()-new Date(s.replace(' ','T')).getTime();
  const m=Math.floor(diff/60000);
  if(m<1)return '방금 전';
  if(m<60)return m+'분 전';
  const h=Math.floor(m/60);if(h<24)return h+'시간 전';
  const d=Math.floor(h/24);if(d<30)return d+'일 전';
  const mo=Math.floor(d/30);if(mo<12)return mo+'개월 전';
  return Math.floor(mo/12)+'년 전';
}

/* ---- 목록 자동 페이징 — 행이 화면을 벗어나지 않게 남은 높이로 페이지 크기 계산 (React useAutoPageSize/Pager 와 동일 규칙)
   행 높이는 화면별 고정 상수(감사 46 / 키 50 / 사용자 58) — 실측하면 데이터 전후로 값이 출렁여 결정적으로 만든다 ---- */
function autoPageSize(wrapEl,rowH,min=3){
  const body=wrapEl.querySelector('tbody');
  const top=(body||wrapEl).getBoundingClientRect().top+window.scrollY;
  const avail=window.innerHeight-top-56-60; // pager 높이 + content 하단 여백
  return Math.max(min,Math.floor(avail/rowH));
}
/* 5페이지 고정 블록 — 현재 페이지가 속한 블록(1~5, 6~10, …)의 번호만 표시 */
function pageBlock(page,totalPages){
  const last=Math.max(totalPages,1)-1;
  const start=Math.floor(page/5)*5,end=Math.min(start+4,last);
  return Array.from({length:end-start+1},(_,i)=>start+i);
}
function renderPager(el,total,page,totalPages,goFn,unit='건'){
  totalPages=Math.max(totalPages,1);
  el.innerHTML=`<span class="pinfo">총 ${total}${unit} · ${page+1}/${totalPages} 페이지</span>`
    +`<button ${page===0?'disabled':''} onclick="${goFn}(0)">«</button>`
    +`<button ${page===0?'disabled':''} onclick="${goFn}(${page-1})">‹</button>`
    +pageBlock(page,totalPages).map(p=>`<button class="${p===page?'on':''}" onclick="${goFn}(${p})">${p+1}</button>`).join('')
    +`<button ${page>=totalPages-1?'disabled':''} onclick="${goFn}(${page+1})">›</button>`
    +`<button ${page>=totalPages-1?'disabled':''} onclick="${goFn}(${totalPages-1})">»</button>`;
}

/* 열 너비 조절 — 각 표의 th 에 드래그 핸들을 붙인다(마지막 열 제외). 드래그한 열과 오른쪽 열의 폭을 맞바꿔 합계를 유지하고,
   더블클릭하면 처음 폭으로 복원한다. 프론트 lib/useColumnResize 와 같은 동작. */
function enableColResize(){
  document.querySelectorAll('table.tbl-fixed, table.bd-table').forEach(table=>{
    const ths=[...table.querySelectorAll('thead th')];
    if(ths.length<2)return;
    table.style.tableLayout='fixed';
    const total=()=>table.getBoundingClientRect().width||1;
    const init=ths.map(th=>th.getBoundingClientRect().width/total()*100);
    const apply=w=>ths.forEach((th,i)=>th.style.width=w[i]+'%');
    let widths=[...init];apply(widths);
    ths.slice(0,-1).forEach((th,i)=>{
      const h=document.createElement('span');h.className='col-resizer';h.setAttribute('aria-hidden','true');
      h.addEventListener('click',e=>e.stopPropagation());
      h.addEventListener('dblclick',e=>{e.stopPropagation();widths=[...init];apply(widths);});
      h.addEventListener('mousedown',e=>{
        e.preventDefault();e.stopPropagation();h.classList.add('active');
        const startX=e.clientX,start=[...widths],pair=start[i]+start[i+1],tw=total();
        const move=ev=>{const d=(ev.clientX-startX)/tw*100;const a=Math.min(Math.max(start[i]+d,4),pair-4);widths=[...start];widths[i]=a;widths[i+1]=pair-a;apply(widths);};
        const up=()=>{window.removeEventListener('mousemove',move);window.removeEventListener('mouseup',up);document.body.classList.remove('col-resizing');h.classList.remove('active');};
        document.body.classList.add('col-resizing');window.addEventListener('mousemove',move);window.addEventListener('mouseup',up);
      });
      th.appendChild(h);
    });
  });
}

/* 정렬 표시 — 현재 정렬 열의 th 에 .on 과 방향 화살표(.sort-mark)를 붙인다. 프론트 components/ui/sort-mark 와 동일 */
function markSort(field,dir){
  document.querySelectorAll('thead th.sortable').forEach(th=>{
    th.classList.remove('on');th.querySelector('.sort-mark')?.remove();
    const m=(th.getAttribute('onclick')||'').match(/\('(\w+)'\)/);
    if(m&&m[1]===field){th.classList.add('on');
      const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('class','sort-mark');svg.setAttribute('width','11');svg.setAttribute('height','11');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('fill','none');svg.setAttribute('stroke','currentColor');svg.setAttribute('stroke-width','2.5');
      const path=document.createElementNS('http://www.w3.org/2000/svg','path');path.setAttribute('d',dir>0?'m6 14 6-6 6 6':'m6 10 6 6 6-6');svg.appendChild(path);
      const h=th.querySelector('.col-resizer');h?th.insertBefore(svg,h):th.appendChild(svg);}
  });
}
