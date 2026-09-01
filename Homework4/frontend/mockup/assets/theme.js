/* 테마 초기화 — 각 페이지 <head>에서 로드 (다크/라이트/시스템) */
(function(){
  window.getThemeMode=function(){
    try{return localStorage.getItem('kms.theme')||'system';}catch(e){return 'system';}
  };
  window.applyTheme=function(){
    const m=getThemeMode();
    const dark=m==='dark'||(m==='system'&&window.matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.dataset.theme=dark?'dark':'light';
  };
  window.setThemeMode=function(m){
    try{localStorage.setItem('kms.theme',m);}catch(e){}
    applyTheme();
    if(typeof renderThemeChecks==='function')renderThemeChecks();
  };
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change',()=>{
    if(getThemeMode()==='system')applyTheme();
  });
  applyTheme();
  // 사이드바 축소 상태도 첫 페인트 전에 적용 (페이지 이동 시 폭 변화 애니메이션 방지)
  try{if(localStorage.getItem('kms.side')==='collapsed')document.documentElement.classList.add('collapsed');}catch(e){}
})();
