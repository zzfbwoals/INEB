/* 더미 데이터 — 추후 React 개발 시 API 응답 스펙 참고용
   키 상태 모델 (2026-08-28 개정, 네이버 KMS 방식 반영)
   - 버전 상태 4종: PRE_ACTIVE / ACTIVE / DEACTIVATED / DESTROYED. 키 status는 버전에서 파생
   - 암호화(서명)는 항상 최신 ACTIVE 버전(current_version)으로만. 구 ACTIVE 버전은 복호화(검증) 전용
   - 회전(ROTATE) 후 구 버전은 ACTIVE 유지(복호화 가능). DEACTIVATE 하면 암·복호화 모두 차단
   - 최신 버전은 단독 비활성화 불가(네이버) → 키 전체 DEACTIVATE 또는 회전 후 비활성화
   - 무결성(integrity_hash) 위반이 감지된 버전은 서버가 자동 DEACTIVATED (trigger=INTEGRITY)
   - INTEGRITY 로 정지된 버전만 REACTIVATE 가능(ADMIN·사유·언래핑 성공 확인 후 해시 재계산). 관리자 수동 정지는 재활성화 불가
   - 회전 주기(rotation_period_days 1~730, 기본 90) 도래 시 자동 ROTATE (trigger=SCHEDULE)
   - algo/expire/dday 는 목록·대시보드 표시용 파생값 */
const KEYS=[
 {id:1,name:'PAY-GW-AES256',uid:'550e8400-e29b-41d4-a716-446655440021',algoName:'AES',size:256,mode:'GCM',algo:'AES-256',purpose:'ENC_DEC',
  desc:'결제 게이트웨이 카드번호 암호화',status:'ACTIVE',currentVersion:3,integrity:true,autoRotate:true,rotationPeriodDays:180,nextRotationAt:'2027-01-28 00:00:00',
  expire:'2027-01-28',dday:153,created:'2025-08-01 14:03:11',
  versions:[
   {v:3,state:'ACTIVE',activationDate:'2026-08-01 00:00:00',integrity:true,lastUsedAt:'2026-08-27 14:02:18',usageCount:142},
   {v:2,state:'ACTIVE',activationDate:'2026-02-01 00:00:00',integrity:true,lastUsedAt:'2026-08-20 09:11:40',usageCount:1893},
   {v:1,state:'DESTROYED',activationDate:'2025-08-01 14:03:11',destroyedAt:'2026-03-02 10:00:05',integrity:true,lastUsedAt:'2026-02-27 17:45:02',usageCount:2210}],
  history:[
   {v:3,from:'PRE_ACTIVE',to:'ACTIVE',trigger:'DATE_REACHED',reason:'활성일(2026-08-01 00:00) 도래 — 현행 버전 v3으로 교체',by:'SYSTEM',at:'2026-08-01 00:00:03'},
   {v:3,from:null,to:'PRE_ACTIVE',trigger:'ROTATE',reason:'반기 정기 회전 — 08/01 자정 예약',by:'류재민',at:'2026-07-28 11:20:44'},
   {v:1,from:'DEACTIVATED',to:'DESTROYED',trigger:'OPERATION',reason:'v1 암호문 전량 v2로 재암호화 완료 확인 후 폐기',by:'김보안',at:'2026-03-02 10:00:05'},
   {v:1,from:'ACTIVE',to:'DEACTIVATED',trigger:'OPERATION',reason:'재암호화 완료, v1 복호화 차단',by:'김보안',at:'2026-02-20 09:00:00'},
   {v:2,from:null,to:'ACTIVE',trigger:'SCHEDULE',reason:'회전 주기(180일) 도래 자동 회전',by:'SYSTEM',at:'2026-02-01 00:00:00'},
   {v:1,from:null,to:'ACTIVE',trigger:'OPERATION',reason:'키 등록 (활성일 미지정 → 즉시 활성)',by:'김보안',at:'2025-08-01 14:03:11'}]},
 {id:2,name:'CRM-DB-ARIA256',uid:'6ba7b810-9dad-11d1-80b4-00c04fd430c8',algoName:'ARIA',size:256,mode:'CBC',algo:'ARIA-256',purpose:'ENC_DEC',
  desc:'CRM 고객 DB 컬럼 암호화',status:'ACTIVE',currentVersion:1,integrity:true,autoRotate:true,rotationPeriodDays:90,nextRotationAt:'2026-10-13 00:00:00',
  expire:'2026-10-13',dday:46,created:'2026-01-15 09:30:00',
  versions:[
   {v:1,state:'ACTIVE',activationDate:'2026-01-15 09:30:00',integrity:true,lastUsedAt:'2026-08-28 08:40:12',usageCount:5120}],
  history:[
   {v:1,from:null,to:'ACTIVE',trigger:'OPERATION',reason:'키 등록',by:'류재민',at:'2026-01-15 09:30:00'}]},
 {id:3,name:'EDI-SIGN-ECDSA',uid:'7c9e6679-7425-40de-944b-e07fc19b1fd0',algoName:'ECDSA',size:256,mode:null,algo:'ECDSA-P256',purpose:'SIGN_VERIFY',
  desc:'EDI 전문 전자서명',status:'ACTIVE',currentVersion:2,integrity:true,autoRotate:true,rotationPeriodDays:365,nextRotationAt:'2027-03-11 00:00:00',
  expire:'2027-03-11',dday:195,created:'2025-09-11 10:00:00',
  versions:[
   {v:2,state:'ACTIVE',activationDate:'2026-03-11 00:00:00',integrity:true,lastUsedAt:'2026-08-28 07:12:00',usageCount:880},
   {v:1,state:'ACTIVE',activationDate:'2025-09-11 10:00:00',integrity:true,lastUsedAt:'2026-08-02 13:20:11',usageCount:1460}],
  history:[
   {v:2,from:null,to:'ACTIVE',trigger:'SCHEDULE',reason:'회전 주기(365일) 도래 자동 회전',by:'SYSTEM',at:'2026-03-11 00:00:00'},
   {v:1,from:null,to:'ACTIVE',trigger:'OPERATION',reason:'키 등록',by:'김보안',at:'2025-09-11 10:00:00'}]},
 {id:4,name:'HR-FILE-RSA2048',uid:'f47ac10b-58cc-4372-a567-0e02b2c3d479',algoName:'RSA',size:2048,mode:null,algo:'RSA-2048',purpose:'ENC_DEC_SIGN_VERIFY',
  desc:'인사 문서 파일 키 봉인 + 서명 (회전 예약 중)',status:'ACTIVE',currentVersion:2,integrity:true,autoRotate:false,rotationPeriodDays:null,nextRotationAt:null,
  expire:'—',dday:9999,created:'2025-12-01 09:00:00',
  versions:[
   {v:3,state:'PRE_ACTIVE',activationDate:'2026-09-01 00:00:00',integrity:true,lastUsedAt:null,usageCount:0},
   {v:2,state:'ACTIVE',activationDate:'2026-06-01 00:00:00',integrity:true,lastUsedAt:'2026-08-27 18:30:00',usageCount:312},
   {v:1,state:'DEACTIVATED',deactivationTrigger:'OPERATION',activationDate:'2025-12-01 09:00:00',integrity:true,lastUsedAt:'2026-06-15 10:00:00',usageCount:990}],
  history:[
   {v:3,from:null,to:'PRE_ACTIVE',trigger:'ROTATE',reason:'9월 조직개편 대비 사전 회전 예약',by:'류재민',at:'2026-08-20 16:05:30'},
   {v:1,from:'ACTIVE',to:'DEACTIVATED',trigger:'OPERATION',reason:'v1 봉인 파일 전량 v2로 재봉인 완료',by:'류재민',at:'2026-07-01 10:00:00'},
   {v:2,from:null,to:'ACTIVE',trigger:'ROTATE',reason:'수동 회전',by:'류재민',at:'2026-06-01 00:00:00'},
   {v:1,from:null,to:'ACTIVE',trigger:'OPERATION',reason:'키 등록',by:'김보안',at:'2025-12-01 09:00:00'}]},
 {id:5,name:'BATCH-WRAP-LEA256',uid:'9b2f5c1a-3e77-4aa0-92cd-114455aa0277',algoName:'LEA',size:256,mode:'CTR',algo:'LEA-256',purpose:'ENC_DEC',
  desc:'배치 전송 데이터키 래핑',status:'PRE_ACTIVE',currentVersion:1,integrity:true,autoRotate:true,rotationPeriodDays:90,nextRotationAt:'2026-12-14 00:00:00',
  expire:'2026-12-14',dday:108,created:'2026-08-22 15:10:00',
  versions:[
   {v:1,state:'PRE_ACTIVE',activationDate:'2026-09-15 00:00:00',integrity:true,lastUsedAt:null,usageCount:0}],
  history:[
   {v:1,from:null,to:'PRE_ACTIVE',trigger:'OPERATION',reason:'키 등록 (활성일 2026-09-15 지정)',by:'류재민',at:'2026-08-22 15:10:00'}]},
 {id:6,name:'TEMP-TEST-SEED128',uid:'1c4d8e2f-30bb-4e91-8d20-66aa02cc9130',algoName:'SEED',size:128,mode:'CBC',algo:'SEED-128',purpose:'ENC_DEC',
  desc:'연동 테스트용 임시 키',status:'DEACTIVATED',currentVersion:1,integrity:true,autoRotate:false,rotationPeriodDays:null,nextRotationAt:null,
  expire:'—',dday:9999,created:'2026-05-18 11:00:00',
  versions:[
   {v:1,state:'DEACTIVATED',deactivationTrigger:'OPERATION',activationDate:'2026-05-18 11:00:00',integrity:true,lastUsedAt:'2026-08-24 09:00:00',usageCount:48}],
  history:[
   {v:1,from:'ACTIVE',to:'DEACTIVATED',trigger:'OPERATION',reason:'연동 테스트 종료 — 키 전체 정지',by:'류재민',at:'2026-08-25 18:00:02'},
   {v:1,from:null,to:'ACTIVE',trigger:'OPERATION',reason:'키 등록',by:'류재민',at:'2026-05-18 11:00:00'}]},
 {id:7,name:'LEGACY-AES128',uid:'d290f1ee-6c54-4b01-90e6-d701748f0851',algoName:'AES',size:128,mode:'CBC',algo:'AES-128',purpose:'ENC_DEC',
  desc:'구 레거시 시스템 연동 키 (무결성 위반 감지)',status:'ACTIVE',currentVersion:2,integrity:false,autoRotate:true,rotationPeriodDays:365,nextRotationAt:'2026-06-30 00:00:00',
  expire:'2026-06-30',dday:-59,created:'2024-06-30 10:00:00',
  versions:[
   {v:2,state:'ACTIVE',activationDate:'2025-06-30 00:00:00',integrity:true,lastUsedAt:'2026-08-27 08:00:00',usageCount:120},
   {v:1,state:'DEACTIVATED',deactivationTrigger:'INTEGRITY',activationDate:'2024-06-30 10:00:00',integrity:false,lastUsedAt:'2026-08-10 12:00:00',usageCount:640}],
  history:[
   {v:1,from:'ACTIVE',to:'DEACTIVATED',trigger:'INTEGRITY',reason:'key_material v1 integrity_hash 불일치 감지 — 자동 비활성화',by:'SYSTEM',at:'2026-08-26 03:10:44'},
   {v:2,from:null,to:'ACTIVE',trigger:'SCHEDULE',reason:'회전 주기(365일) 도래 자동 회전',by:'SYSTEM',at:'2025-06-30 00:00:00'},
   {v:1,from:null,to:'ACTIVE',trigger:'OPERATION',reason:'키 등록',by:'김보안',at:'2024-06-30 10:00:00'}]},
 {id:8,name:'RETIRED-2024-AES128',uid:'e58ed763-928d-4a1c-8f10-0d21aacc7755',algoName:'AES',size:128,mode:'CBC',algo:'AES-128',purpose:'ENC_DEC',
  desc:'2024 폐기 완료 키',status:'DESTROYED',currentVersion:1,integrity:true,autoRotate:false,rotationPeriodDays:null,nextRotationAt:null,
  expire:'—',dday:9999,created:'2024-01-01 10:00:00',
  versions:[
   {v:1,state:'DESTROYED',activationDate:'2024-01-01 10:00:00',destroyedAt:'2026-01-15 10:00:00',integrity:true,lastUsedAt:'2025-12-20 10:00:00',usageCount:3300}],
  history:[
   {v:1,from:'DEACTIVATED',to:'DESTROYED',trigger:'OPERATION',reason:'서비스 종료, 암호문 전량 파기 확인',by:'admin',at:'2026-01-15 10:00:00'},
   {v:1,from:'ACTIVE',to:'DEACTIVATED',trigger:'OPERATION',reason:'서비스 종료 — 키 전체 정지',by:'admin',at:'2025-12-31 00:00:01'},
   {v:1,from:null,to:'ACTIVE',trigger:'OPERATION',reason:'키 등록',by:'admin',at:'2024-01-01 10:00:00'}]},
];
/* 마스킹 규칙(2026-09-02 확정): 연락처는 앞 3자리만, 이메일은 @ 와 . 만 남기되 별표는 고정 개수(글자 수 비노출) */
const USERS=[
 {id:101,name:'김민준',phone:'010-****-****',email:'****@****.**',plainPhone:'010-2847-3421',plainEmail:'mjkim88@naver.com',status:'ACTIVE',joined:'2026-03-11'},
 {id:102,name:'이서연',phone:'010-****-****',email:'****@****.**',plainPhone:'010-9034-7788',plainEmail:'sylee0412@gmail.com',status:'ACTIVE',joined:'2026-04-02'},
 {id:103,name:'박지훈',phone:'010-****-****',email:'****@****.**.**',plainPhone:'010-5511-1204',plainEmail:'jhpark@ineb.co.kr',status:'ACTIVE',joined:'2026-05-19'},
 {id:104,name:'최유나',phone:'010-****-****',email:'****@****.**',plainPhone:'010-7723-6650',plainEmail:'ynchoi_c@daum.net',status:'SUSPENDED',joined:'2026-06-07'},
 {id:105,name:'정다은',phone:'010-****-****',email:'****@****.**',plainPhone:'010-3308-9917',plainEmail:'dejung95@kakao.com',status:'ACTIVE',joined:'2026-07-28'},
];
/* key_usage_log — 키 상세 '사용 이력' 탭 (GET /api/keys/{id}/usage) */
const USAGE=[
 {id:4231,keyId:2,version:1,operation:'ENCRYPT',result:'SUCCESS',failReason:null,at:'2026-08-28 08:40:12'},
 {id:4230,keyId:4,version:2,operation:'SIGN',result:'SUCCESS',failReason:null,at:'2026-08-27 18:30:00'},
 {id:4229,keyId:1,version:3,operation:'ENCRYPT',result:'SUCCESS',failReason:null,at:'2026-08-27 14:02:18'},
 {id:4228,keyId:7,version:1,operation:'DECRYPT',result:'FAIL',failReason:'DEACTIVATED (무결성 위반 자동 정지)',at:'2026-08-27 08:00:00'},
 {id:4227,keyId:7,version:2,operation:'ENCRYPT',result:'SUCCESS',failReason:null,at:'2026-08-27 07:58:41'},
 {id:4226,keyId:1,version:2,operation:'DECRYPT',result:'SUCCESS',failReason:null,at:'2026-08-20 09:11:40',note:'구 버전'},
 {id:4225,keyId:3,version:1,operation:'VERIFY',result:'SUCCESS',failReason:null,at:'2026-08-02 13:20:11',note:'구 버전'},
 {id:4224,keyId:1,version:3,operation:'DECRYPT',result:'FAIL',failReason:'GCM 태그 불일치 (암호문 손상)',at:'2026-08-01 10:05:00'},
 {id:4223,keyId:4,version:1,operation:'DECRYPT',result:'FAIL',failReason:'DEACTIVATED',at:'2026-07-02 09:00:00'},
 {id:4222,keyId:4,version:1,operation:'DECRYPT',result:'SUCCESS',failReason:null,at:'2026-06-15 10:00:00',note:'구 버전'},
];
/* audit_log — 감사 로그 페이지. target 은 KEY#{uid}/USER#{id}/AUTH#{loginId} 형식, detail 은 key=value 문자열 (백엔드와 동일 규격) */
const AUDITS=[
 {id:1291,at:'2026-08-28 08:40:12',actor:'admin',action:'KEY_TEST_ENCRYPT',target:'KEY#2',keyId:2,detail:'version=1'},
 {id:1290,at:'2026-08-27 18:30:00',actor:'admin',action:'KEY_TEST_SIGN',target:'KEY#4',keyId:4,detail:'version=2'},
 {id:1289,at:'2026-08-27 14:02:18',actor:'admin',action:'KEY_TEST_ENCRYPT',target:'KEY#1',keyId:1,detail:'version=3'},
 {id:1288,at:'2026-08-27 08:00:00',actor:'admin',action:'KEY_TEST_DECRYPT',target:'KEY#7',keyId:7,detail:'version=1, result=fail (DEACTIVATED)'},
 {id:1287,at:'2026-08-26 03:10:44',actor:'SYSTEM',action:'KEY_INTEGRITY_VIOLATION',target:'KEY#7',keyId:7,detail:'version=1, autoDeactivated=true'},
 {id:1286,at:'2026-08-25 18:00:02',actor:'admin',action:'KEY_STATUS_CHANGED',target:'KEY#6',keyId:6,detail:'action=DEACTIVATE, scope=KEY, reason=연동 테스트 종료'},
 {id:1285,at:'2026-08-20 16:05:30',actor:'admin',action:'KEY_ROTATED',target:'KEY#4',keyId:4,detail:'newVersion=3, activationDate=2026-09-01 00:00:00'},
 {id:1284,at:'2026-08-20 09:11:40',actor:'admin',action:'KEY_TEST_DECRYPT',target:'KEY#1',keyId:1,detail:'version=2, oldVersion=true'},
 {id:1283,at:'2026-08-19 09:38:12',actor:'admin',action:'LOGIN_SUCCESS',target:'AUTH#admin',keyId:null,detail:'role=ADMIN'},
 {id:1282,at:'2026-08-18 16:02:51',actor:'admin',action:'USER_PLAIN_VIEWED',target:'USER#103',keyId:null,detail:'reason=CS 본인확인'},
 {id:1281,at:'2026-08-18 15:20:33',actor:'admin',action:'KEY_UPDATED',target:'KEY#1',keyId:1,detail:'autoRotate=true, period=180'},
 {id:1280,at:'2026-08-18 11:07:19',actor:'admin',action:'NOTICE_CREATED',target:'NOTICE#12',keyId:null,detail:'files=2, encrypted=true'},
 {id:1279,at:'2026-08-17 18:31:44',actor:'admin',action:'USER_UPDATED',target:'USER#104',keyId:null,detail:'fields=status:ACTIVE→SUSPENDED'},
 {id:1278,at:'2026-08-01 00:00:03',actor:'SYSTEM',action:'KEY_STATUS_CHANGED',target:'KEY#1',keyId:1,detail:'action=ACTIVATE, version=3, trigger=DATE_REACHED'},
 {id:1277,at:'2026-07-28 11:20:44',actor:'admin',action:'KEY_ROTATED',target:'KEY#1',keyId:1,detail:'newVersion=3, activationDate=2026-08-01 00:00:00'},
 {id:1276,at:'2026-07-01 10:00:00',actor:'admin',action:'KEY_STATUS_CHANGED',target:'KEY#4',keyId:4,detail:'action=DEACTIVATE, version=1, reason=재봉인 완료'},
];
const NOTICES=[
 {id:12,no:1524,important:true,title:'[점검] 8월 정기 시스템 점검 안내 (08/24 02:00~04:00)',files:2,views:129,by:'류재민',at:'2026-08-17'},
 {id:11,no:1523,important:true,title:'[보안] 마스터키 순환(enc_ver) 정책 변경 사전 공지',files:1,views:86,by:'김보안',at:'2026-08-05'},
 {id:10,no:1522,important:false,title:'KMS 관리 키 명명 규칙 가이드 v2 배포',files:1,views:214,by:'류재민',at:'2026-07-22'},
 {id:9,no:1521,important:false,title:'감사로그 CSV 정규화 규칙 안내 (구분자·Null·KST 포맷)',files:0,views:57,by:'김보안',at:'2026-07-10'},
];
/* 상태 배지 클래스 / 한글 라벨 */
const BADGE={PRE_ACTIVE:'b-pre',ACTIVE:'b-active',DEACTIVATED:'b-deact',DESTROYED:'b-destroyed'};
const STATE_KO={PRE_ACTIVE:'준비',ACTIVE:'운영',DEACTIVATED:'정지',DESTROYED:'폐기'};
const TRIGGER_KO={OPERATION:'관리자',REACTIVATE:'재활성화',DATE_REACHED:'SYSTEM 활성일',SCHEDULE:'SYSTEM 회전주기',INTEGRITY:'SYSTEM 무결성',ROTATE:'회전'};
/* 용도 3종 (네이버 KMS 기준) */
const PURPOSE={ENC_DEC:'암/복호화',ENC_DEC_SIGN_VERIFY:'암/복호화 및 서명/검증',SIGN_VERIFY:'서명/검증'};
const canEnc=p=>p!=='SIGN_VERIFY', canSign=p=>p!=='ENC_DEC';
/* 알고리즘 카탈로그 — D'GuardKMS 등록 화면 + 네이버(RSA·ECDSA) */
const ALGOS={
 AES:  {sizes:[128,192,256],modes:['CBC','GCM','CTR','ECB'],purpose:'ENC_DEC'},
 ARIA: {sizes:[128,192,256],modes:['CBC','GCM','CTR','ECB'],purpose:'ENC_DEC'},
 LEA:  {sizes:[128,192,256],modes:['CBC','GCM','CTR','ECB'],purpose:'ENC_DEC'},
 SEED: {sizes:[128],modes:['CBC','ECB'],purpose:'ENC_DEC'},
 RSA:  {sizes:[2048,3072,4096],modes:[],purpose:'ENC_DEC_SIGN_VERIFY',asym:true},
 ECDSA:{sizes:[256,384],modes:[],purpose:'SIGN_VERIFY',asym:true,sizeLabel:s=>'P-'+s},
 SHA256:{sizes:[256],modes:[],purpose:'SIGN_VERIFY',hmac:true},
 SHA512:{sizes:[512],modes:[],purpose:'SIGN_VERIFY',hmac:true},
};
const MAX_VERSIONS=100, ROT_MIN=1, ROT_MAX=730, ROT_DEFAULT=90;
function stateBadge(s){return `<span class="badge ${BADGE[s]}">${s} · ${STATE_KO[s]}</span>`;}
