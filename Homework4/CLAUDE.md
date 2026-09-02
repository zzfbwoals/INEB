# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**모든 응답은 한국어로 작성한다.**

## 프로젝트 개요

아이넵 통합키관리 솔루션(D'GuardKMS)을 학습용으로 축소한 **암호키 관리 어드민 웹** 과제(Homework4).
KMS 관리 키의 생명주기 관리 + 암복호화 테스트를 본체로 하고, 사용자 관리와 게시판(공지·첨부파일 암호화)을 얹는다.
수행자: 류재민 (개별 과제, 개발 서버 192.168.200.52). 구현 기간 2026-08-17 ~ 09-11, 최종 발표 09-14.

참고 문서 (요구사항·설계의 원본, 코드보다 우선):
- 과제 안내서: `"D:\회사\아이넵\과제\과제4\C2_DGuardKMS_어드민웹_과제안내서.html"`
- 설계 문서: `"D:\회사\아이넵\과제\과제4\류재민_아이넵 솔루션 어드민 웹 개발 과제 설계.pdf"`, `"https://docs.google.com/document/d/1QLSmHKCBQWlZIiI42AtCuEhpV1J9WJqAn16G1g0N7hs/edit?usp=sharing"`
- UI 목업(정적 HTML, 화면 설계 참고용): `Homework4/frontend/mockup/` (2026-09-02 저장소 내로 이동, 구 경로 `D:\회사\아이넵\과제\과제4\ineb-kms-mockup\`)

## 저장소 구조

Git 루트는 `INEB/` (계정 zzfbwoals). Homework1~3은 별개의 완료된 암호학 과제이므로 건드리지 않는다.

- `Homework4/backend/kms/` — Spring Boot 백엔드 (Gradle 프로젝트 루트)
- `Homework4/frontend/kms/` — React 프론트엔드 (npm 프로젝트 루트)
- `.github/workflows/deploy.yml` — 저장소 루트에 위치한 CI/CD (main 푸시 시 자동 배포)

커밋 메시지 규칙: `[feat] ...` / `[fix] ...` 형태의 한글 메시지 (git log 참고).

## 명령어

백엔드 (`Homework4/backend/kms/`에서 실행, Windows는 `gradlew.bat`):
```
./gradlew build                # 빌드 + 테스트
./gradlew bootJar              # 실행 JAR 생성 (CI가 사용)
./gradlew test                 # 전체 테스트
./gradlew test --tests "com.ineb.kms.SomeTest"                 # 단일 클래스
./gradlew test --tests "com.ineb.kms.SomeTest.methodName"      # 단일 메서드
./gradlew bootRun              # 로컬 기동 (DB는 192.168.200.52 원격 PostgreSQL)
```

프론트엔드 (`Homework4/frontend/kms/`에서 실행):
```
npm run dev        # Vite 개발 서버
npm run build      # tsc -b && vite build
npm run lint       # oxlint (ESLint 아님)
```

## 기술 스택

- **백엔드**: Java 25, Spring Boot **4.0.7** (스타터 아티팩트명이 Boot 3와 다름: `spring-boot-starter-webmvc`, 테스트도 `spring-boot-starter-webmvc-test`/`data-jpa-test`/`security-test`로 분리), Spring Data JPA, Spring Security + JWT(jjwt 0.13, HS256), springdoc-openapi 3.x, PostgreSQL 17
- **프론트엔드**: React 19 + TypeScript, Vite 8(rolldown 기반), React Compiler(babel preset), oxlint, Tailwind CSS v4(@tailwindcss/vite), Shadcn/ui(CLI 대신 `src/components/ui/`에 직접 벤더링, 목업 디자인 시스템으로 커스터마이징), react-router v7, Axios, Recharts 도입 예정. 경로 별칭 `@/` → `src/`
- **DB**: 원격 개발 서버 `192.168.200.52:5432/INEB`, 계정 dguard, `ddl-auto: update` (스키마는 JPA 엔티티가 생성)
- Swagger UI: `/swagger-ui`, `/v3/api-docs` (SecurityConfig에서 permitAll, Nginx도 프록시)

## 현재 구현 상태

구현 완료 (1주차):
- 백엔드: 마스터키 유도·KCV 검증(`crypto/`, 기동 fail-fast), JWT 로그인/me/logout(`auth/`·`security/`, BCrypt), admin 계정 시드(`config/AdminUserSeeder` — admin_user 비어 있을 때만), 공통 응답·예외(`common/`), CORS(localhost:5173)
- 프론트: 로그인 화면(목업 이식), 앱 셸(사이드바·상단바·프로필 메뉴·테마 라이트/다크/시스템 전환), 임시 홈. 사이드바 탭 전환은 미구현
- 보안 환경변수는 `KMS_MASTER_PASSPHRASE` **하나뿐** (2026-08-25 상사 지시로 통합, `KMS_ADMIN_INIT_PASSWORD`도 제거됨)
  - DB 비밀번호: `application.yml`의 `spring.datasource.password`에 `ENC(base64(salt|iv|ct+tag))` 암호문으로 저장 → `config/DataSourceConfig`가 패스프레이즈로 복호화(`crypto/ConfigSecretCodec`, salt를 암호문에 동봉하므로 DB 접속 전 복호화 가능). 암호문 생성: `"평문" | ./gradlew.bat -q encryptSecret` (환경변수 `KMS_MASTER_PASSPHRASE` 필요, **운영은 서버의 kms.env 패스프레이즈로 만든 값이어야 함**)
  - admin 초기 비밀번호: `kms.admin.init-password`에 같은 `ENC(...)` 방식으로 저장(평문이면 기동 실패). `config/AdminUserSeeder`가 admin_user 비어 있을 때만 복호화 → BCrypt 저장
  - JWT 서명 키·무결성 HMAC 키: 환경변수 없음. 최초 기동 시 SecureRandom 32바이트로 생성해 마스터키로 래핑하여 `crypto_config`(`jwt_key`, `integrity_key`)에 저장, 이후 언래핑(`crypto/WrappedSecretStore`). DB 초기화 시 함께 사라짐
  - 틀린 패스프레이즈로 기동하면 KCV보다 앞서 DataSource 생성 단계(ENC 복호화 GCM 태그 불일치)에서 실패함
- 로컬 개발 DB(localhost:5432)의 crypto_config에 salt/KCV가 이미 확정돼 있음 — 틀린 패스프레이즈로 기동하면 정상적으로 기동 실패함

구현 완료 (2주차, 2026-08-28, develop 브랜치):
- 백엔드 `key/` 패키지: 도메인(`CryptoKey`·`KeyMaterial`·`KeyStatusHistory`·`KeyUsageLog` + enum 9종), `KeyStateMachine`(전이의 유일한 통로), `KeyIntegrityHasher/Guard`(HMAC 정규화·위반 자동 정지), `KeyMaterialFactory`(재료 생성·GCM 래핑), `KeyService`(목록·상세·등록·수정), `KeyOperationService`(ACTIVATE/REACTIVATE/DEACTIVATE/ROTATE/DESTROY), `KeyTestService`+`KeyCipherSupport`(AES/ARIA/SEED JCE, LEA는 BC 경량 API, RSA-OAEP, ECDSA, HMAC), `CipherTextFormat`, `KeyLifecycleScheduler/Worker`(활성일·갱신 주기·선택적 무결성 배치). API 16개(키값 조회 포함), 단위 테스트 113개. 3주차 연결점: `AuditHook`(현재 로그 구현체)
- 프론트: `/keys`(목록·등록), `/keys/:keyUid`(상세·모달 6종), `/keys/test`(암복호화·서명검증). `api/keys.ts`, `lib/keyRules.ts`, `ui/dialog.tsx`(Radix)·`ui/toast.tsx`. 사이드바 NAV가 NavLink로 전환됨
- 로컬 확인 완료: 4개 테이블 자동 생성, API 스모크(전 알고리즘·규칙 위반 응답), 브라우저 목록·상세·갱신 모달·암복호화 라운드트립. 로컬 기동: `set KMS_MASTER_PASSPHRASE=<로컬 패스프레이즈>` 후 `gradlew.bat bootRun --args="--spring.profiles.active=local"`, 프론트 `npm run dev`
- 주의: 잘못된 JSON·enum 값은 `GlobalExceptionHandler`가 400(INVALID_INPUT)으로 응답. 프론트 테스트 페이지는 접두 버전을 클라이언트에서 선판정하지만 최종 판정은 서버

구현 완료 (3주차, 2026-09-01, develop 브랜치):
- 백엔드 `audit/` 패키지: `AuditLog`(@Immutable append-only) + `AuditHasher`(정규화 `prev_hash|actor|action|target|detail|created_at(KST)`, 무결성 HMAC 키 재사용) + `AuditChainService`(**pg_advisory_xact_lock 으로 기록 직렬화** — 동시 기록 시 체인 분기 방지; `append`는 호출자 트랜잭션 참여, 로그인 실패 등 예외 경로는 `appendDetached` REQUIRES_NEW) + `AuditChainVerifier`(TAMPERED/CHAIN_BROKEN 구간 반환, 저장 row_hash 기준으로 이어가 위반이 번지지 않음) + `DbAuditHook`(2주차 `LoggingAuditHook` 대체·삭제됨). **`AuditHook` 인터페이스는 `key/` → `audit/` 패키지로 이동**, target 형식 확정: `KEY#{keyUid}` / `USER#{id}` / `AUTH#{loginId}` / `AUDIT` (헬퍼 `AuditHook.keyTarget()` 등으로만 생성, 기존 키 호출부 8곳 일괄 적용). 로그인 성공/실패/로그아웃도 기록(`AuthService`). API: `GET /api/audit-logs`(actor·action·target·from·to 필터), `POST /api/audit-logs/verify`(부수효과 있어 GET→POST로 설계 개정), `GET /api/audit-logs/export`(CSV, UTF-8 BOM, AUDIT_EXPORTED 기록)
- 백엔드 `user/` 패키지: `AppUser`(**iv 컬럼 없음** — `phone_enc`/`email_enc`가 `base64(iv|ct+tag)` iv 동봉 방식, 2026-09-01 확정; 필드마다 새 랜덤 IV) + `crypto/PersonalDataCodec`(암복호화 + 검색 HMAC — **무결성 키(integrityKey) 재사용 확정**, 정규화: 연락처 숫자만·이메일 소문자) + `PrivacyMask`(010-****-**** / ****@****.**.**) + `UserIntegrityHasher`(`name|password_hash|status|enc_ver`, 위반 시 자동 정지 없이 integrityValid 플래그만). API: 목록(이름 LIKE + 연락처/이메일 해시 정확검색)·등록·상세·수정(**PUT 하나로 상태 변경·비밀번호 재설정 포함** — 설계의 PATCH /password·/status 별도 API를 목업 모달에 맞춰 통합)·`POST /api/users/{id}/plain`(**GET→POST 개정**, 사유 필수, ADMIN 한정 `@PreAuthorize` — `SecurityConfig`에 `@EnableMethodSecurity` 추가). 비밀번호 정책: 8자 이상+특수문자(서비스 검증). `UserStatus` ACTIVE/SUSPENDED
- 프론트: `/users`(마스킹 목록·등록/수정 모달(비밀번호 재설정 토글)·원문 보기 모달(사유 필수, ADMIN에게만 버튼)), `/audit`(필터·체인 재검증 밴드·CSV 내려받기·`?target=` 쿼리 초기 필터 — 키 상세에서 "감사 로그" 버튼으로 연결). `api/users.ts`·`api/audit.ts`, 사이드바 사용자 관리·감사 로그 NavLink 활성화, index.css에 3주차 스타일(.verify-band 등) 추가
- **체인 배치 검증 (2026-09-02)**: DB 직접 변조는 앱 이벤트가 없어 SSE 로 잡히지 않으므로 `AuditChainScheduler`(60초, `kms.scheduler.audit-chain-check` 기본 true)가 전체 체인을 재검증 — **상태 전이 시에만** `AUDIT_CHAIN_VIOLATION`/`AUDIT_CHAIN_RESTORED`(actor SYSTEM) 감사 기록 → append 가 SSE 브로드캐스트로 이어져 열린 화면의 배지가 최대 60초 내 자동 갱신(지속 위반 스팸 없음)
- **실시간 화면 갱신 (SSE, 2026-09-02)**: 모든 감사 기록이 `AuditChainService`→`AuditEventStream`으로 커밋 후 `{action, target}` 브로드캐스트 (`GET /api/events`, text/event-stream). EventSource는 헤더 불가라 **이 경로만 `?token=` 쿼리 JWT 허용**(`JwtAuthenticationFilter.resolveToken`). 프론트 `lib/events.ts`(`subscribeUiEvents`) — 키 상세(`KEY#uid` 일치 시 refetch)·키/사용자 목록(접두 매칭)·감사 로그(전체 이벤트 → 목록+체인 상태 refetch) 구독. 테스트 페이지는 입력 초기화 방지를 위해 미구독. Nginx `/api/events`는 `proxy_buffering off` 별도 location. 하트비트 25초(@Scheduled)
- 신규 단위 테스트: AuditHasher·AuditChainVerifier(변조/삭제/삽입/구간 병합)·PersonalDataCodec·UserIntegrityHasher·PrivacyMask·UserService + AuthService 감사 기록 검증
- 주의: `KmsApplicationTests`(@SpringBootTest)는 `KMS_MASTER_PASSPHRASE`·DB 필요 — 환경 없으면 실패(코드 문제 아님). audit_log의 `detail`은 설계의 jsonb 대신 varchar(500) 문자열(초과 시 잘림), 검증 응답은 최초 오염 id 대신 위반 구간 목록 `[{fromId,toId,type}]`. audit_log 스키마는 설계의 target_type/target_id 대신 단일 `target` 컬럼

미구현: 게시판·대시보드(4주차). 2주차 산출물 설계 원고: `D:\회사\아이넵\과제\과제4주차_구현설계_KMS키관리.md`

## 핵심 아키텍처 (설계 문서 기준)

### 키 계층 — 두 가지 키의 역할 구분 (과제의 핵심)

1. **마스터키** (AES-256): 어드민이 자기 DB에 저장하는 데이터를 보호. ① 사용자 개인정보(phone_enc/email_enc) 암호화 ② 게시판 첨부파일 암호화 ③ KMS 관리 키 래핑. DB·파일에 저장하지 않고 기동 시 메모리에서만 유도.
2. **KMS 관리 키**: 외부 시스템 연동용 키 자산. 어드민의 역할은 생명주기 관리(등록·상태 전이·이력·사용 로그)와 암복호화 테스트 제공뿐. 어드민 자체 데이터에는 절대 사용하지 않는다.

### 마스터키 유도·검증 (구현 1주차, 발표 시연 항목)

- 기동 시 1회: `PBKDF2-HMAC-SHA256(env.KMS_MASTER_PASSPHRASE, crypto_config의 salt, 10,000회, 32바이트)` — 설계 문서가 반복 횟수를 10,000회로 확정함 (안내서 예시는 210,000회였음)
- **KCV 검증 fail-fast**: 유도 직후 고정 문자열 `"KMS-KCV-V1"`을 암호화해 `crypto_config`의 kcv와 대조, 불일치 시 예외를 던져 기동 중단. "경고만 남기고 진행" 금지 — 틀린 키로 신규 데이터가 암호화되는 사고 방지가 목적
- 메모리 취급: 마스터키는 `byte[]`로만 보관, 사용 직후 `Arrays.fill(key, (byte)0)` zeroize. **`String` 사용 금지** (힙덤프 노출)
- 운영 주입: `/home/dguard/kms.env` (권한 600) → Docker `--env-file`. 파일에는 `KMS_MASTER_PASSPHRASE`만 있으면 됨 (`INTEGRITY_HMAC_KEY`·`JWT_SECRET`·`DB_PASSWORD`·`KMS_ADMIN_INIT_PASSWORD`는 더 이상 읽지 않음)

### KMS 관리 키 생명주기 (구현 2주차)

**2026-08-28 재확정: KMIP 4종 상태 + 네이버 클라우드 KMS 방식의 버전 운영.** (08-26 안에서 "회전 시 구 버전 자동 DEACTIVATED"·"만료 예정일"을 폐기하고 네이버식으로 변경.) 설계 문서·목업(`frontend/mockup`)이 이 절과 다르면 이 절이 우선한다.

**상태 주체는 버전(`key_material` 1행).** 키(`crypto_key`)는 논리 키(신원·메타·회전 정책), 버전은 실제 키 재료(서로 다른 난수). 키 상태는 버전에서 파생해 저장(서버만 갱신).

버전 상태 4종과 허용 연산:

| 상태 | 의미 | 암호화·서명 | 복호화·검증 |
|---|---|---|---|
| PRE_ACTIVE | 재료 생성·래핑됐으나 `activation_date` 미도래 | ✗ | ✗ |
| ACTIVE | 정상. **암호화·서명은 최신 ACTIVE 버전(`current_version`)만**, 구 ACTIVE 버전은 복호화·검증 전용 | 최신만 ✓ | ✓ |
| DEACTIVATED | **암·복호화, 서명·검증 전부 차단**(네이버 "버전 비활성화"). 재료는 보존 | ✗ | ✗ |
| DESTROYED | `wrapped_key` NULL 물리 삭제, 메타·이력 보존 | ✗ | ✗ |

키 상태(파생): ACTIVE 버전 있음→ACTIVE / 없고 PRE_ACTIVE 있음→PRE_ACTIVE / 모두 DEACTIVATED·DESTROYED→DEACTIVATED / 모두 DESTROYED→DESTROYED

- 허용 전이: PRE_ACTIVE→ACTIVE(활성일 도래·ACTIVATE) · PRE_ACTIVE→DESTROYED · ACTIVE→DEACTIVATED(DEACTIVATE / **무결성 위반 자동**) · DEACTIVATED→DESTROYED · **DEACTIVATED→ACTIVE는 `deactivation_trigger=INTEGRITY`인 버전만 REACTIVATE 허용**(재암호화를 위해 복호화가 필요하므로)
- 금지(400): 관리자가 수동 정지(`deactivation_trigger=OPERATION`)한 버전의 재활성화(다시 쓰려면 ROTATE) · DESTROYED 탈출 · ACTIVE→DESTROYED 직행 · PRE_ACTIVE→DEACTIVATED · **최신(current) 버전 단독 DEACTIVATE 불가**(네이버: 최신 버전은 항상 활성) — 키 전체 정지 또는 회전 후 정지. 상태 충돌 409
- **회전(ROTATE)은 연산, 상태 아님.** 새 재료 생성·래핑 → `key_material(version=max+1)` → 새 버전 ACTIVE + `current_version` 갱신. **구 버전은 ACTIVE로 남아 복호화·검증에 계속 사용**(암호화는 최신만이므로 자동으로 복호화 전용이 됨). `activationDate`가 미래면 새 버전 PRE_ACTIVE로 예약, 도래 시 current 교체. 버전 상한 100(초과 400). 재암호화 완료된 구 버전은 관리자가 DEACTIVATE → DESTROY
- **회전 주기(자동 회전)**: `crypto_key.auto_rotate`, `rotation_period_days`(1~730, 기본 90), `next_rotation_at`. 스케줄러가 도래 시 ROTATE(`changed_by=SYSTEM`, `trigger=SCHEDULE`), `next_rotation_at = 회전 시각 + 주기`. 수동 ROTATE는 스케줄에 영향 없음. 키 정지 시 자동 회전 중단. `PUT /api/keys/{id}`로 주기 변경 가능(감사로그)
- **무결성 위반 자동 정지**: `key_material.integrity_hash`(HMAC, 정규화 `key_id|version|state|wrapped_key|iv|wrap_algo|activation_date`)를 언래핑·조회 시 재검증. 불일치 → 해당 버전 즉시 DEACTIVATED(`trigger=INTEGRITY`, actor SYSTEM) + `audit_log KEY_INTEGRITY_VIOLATION` + 상세·대시보드 경고. `crypto_key.integrity_hash` 위반 시 키의 모든 ACTIVE 버전 정지. 위반 버전이 current이면 암호화·서명도 차단됨. **복구(REACTIVATE)**: ADMIN + 사유 필수 → 서버가 ① 마스터키 언래핑 성공(GCM 태그 = 재료 무손상) 확인 ② 현재 메타로 `integrity_hash` 재계산·저장 ③ ACTIVE 전이(구 버전이면 복호화 전용, 최신이면 current 복귀). 언래핑 실패 시 409(재료 손상 → ROTATE만 가능). `key_material.deactivation_trigger(OPERATION|INTEGRITY)` 컬럼으로 구분, 이력 `trigger=REACTIVATE`, `audit_log KEY_REACTIVATED`
- 활성일: 등록·ROTATE에 `activationDate?`(없거나 과거 → 즉시 ACTIVE, 미래 → PRE_ACTIVE). PRE_ACTIVE일 때 `PUT`으로 수정 가능. `deactivation_date`(만료 예정일)는 **폐기** — 수명 관리는 회전 주기로
- 상태 변경 API `PATCH /api/keys/{id}/status`: `{ action: ACTIVATE|REACTIVATE|DEACTIVATE|ROTATE|DESTROY, reason(필수), activationDate?(ACTIVATE·ROTATE), version?(REACTIVATE 필수 · DEACTIVATE·DESTROY — 생략 시 키 전체) }`. 목표 상태 직접 지정 불가. DEACTIVATE 키 전체 = 모든 ACTIVE 버전 정지 + 자동 회전 중단. DESTROY 키 전체는 ACTIVE 존재 시 409. UI는 현재 상태에서 가능한 액션 버튼만 노출
- 스케줄러(`@Scheduled` 60초): ① `activation_date<=now & PRE_ACTIVE`→ACTIVE(current 교체, `trigger=DATE_REACHED`) ② `next_rotation_at<=now & auto_rotate & 키 ACTIVE`→ROTATE(`trigger=SCHEDULE`) ③ 무결성 배치 검증(선택)
- `key_status_history`: `key_id, version, from_state, to_state, reason, trigger(OPERATION|DATE_REACHED|SCHEDULE|INTEGRITY|REACTIVATE|ROTATE), changed_by, changed_at`
- 알고리즘·용도: `algorithm` AES/ARIA/LEA/SEED(대칭, `mode` CBC/GCM/CTR/ECB) · RSA 2048/3072/4096 · ECDSA P-256/P-384 · SHA256/SHA512(HMAC). `purpose` 3종 **ENC_DEC / ENC_DEC_SIGN_VERIFY / SIGN_VERIFY** — 알고리즘이 결정(대칭→ENC_DEC, RSA→ENC_DEC_SIGN_VERIFY, ECDSA·HMAC→SIGN_VERIFY). 비대칭키는 개인키만 래핑 저장, 공개키는 상세 조회. `crypto_key`에 `mode` 컬럼 추가
- 테스트 API: `test/encrypt`·`test/decrypt` + **`test/sign`·`test/verify`** (RSA·ECDSA·HMAC). 암호문 `{version}:{base64 iv}:{base64 ct+tag}`, 서명값 `{version}:{base64 sig}`. 암호화·서명은 current ACTIVE 버전, 복호화·검증은 접두 version으로 조회해 ACTIVE면 허용(구 버전이면 usage_log에 표시), PRE_ACTIVE·DEACTIVATED·DESTROYED·형식 오류 400. `key_usage_log(version)` + `audit_log KEY_TEST_ENCRYPT/DECRYPT/SIGN/VERIFY` 기록
- 키 상세 응답: `versions[]`(version, state, deactivationTrigger, activationDate, lastUsedAt, usageCount, integrityValid), 회전 정책. **사용 이력은 기존 `GET /api/keys/{id}/usage`**가 통계 + `key_usage_log` 목록(version·operation·result·failReason·actor·at)을 함께 반환 — 별도 audit 엔드포인트 없음. 관리자 행위는 `key_status_history` 타임라인과 감사 로그 페이지(`GET /api/audit-logs?target=KEY#id`)에서 조회
- 키 값 미반환 원칙 **개정(2026-08-31)**: 유일한 예외로 **버전 키값 조회 API** `POST /api/keys/{id}/versions/{version}/material`(body `{reason}` 필수) 허용 — 언래핑 직전 무결성 검증(위반 시 자동 정지 409), DESTROYED·재료 손상 409, `audit_log KEY_MATERIAL_VIEWED` 기록. 상세 버전 목록의 **행 클릭** → 사유 입력 모달 (버전별 액션 버튼은 stopPropagation). 그 외 응답(목록·상세·테스트)은 여전히 키 값 미포함. 래핑: SecureRandom → 마스터키 AES-256-GCM → Base64 → `key_material.wrapped_key`(+iv, wrap_algo). 언래핑 후 즉시 zeroize
- 즉시 활성 판정(등록·ROTATE·PUT 활성일 수정): 활성일이 없거나 **now+60초(스케줄러 한 주기) 이내면 즉시 ACTIVE** (`KeyService.isImmediate`) — 분 단위 입력·시계 오차로 "현재 시각" 지정이 다음 틱까지 PRE_ACTIVE로 남는 문제 방지 (2026-08-31)
- 2026-08-28 확정 세부: ECB 모드 제공하되 UI "비권장" 표기 · RSA 암복호화 평문 상한(2048=190B/3072=318B/4096=446B) 서버 400 + 테스트 화면 바이트 카운터 · 무결성 자동 정지는 **2026-08-31 개정: 3경로** — ① 상세 조회 시 `KeyIntegrityGuard.enforceOnRead`가 위반 버전 즉시 정지(예외 없이 정지된 상태로 응답, `KeyService.get`이 쓰기 트랜잭션) ② 사용(언래핑) 직전 verifyOrDeactivate 409 ③ 스케줄러 배치 `kms.scheduler.integrity-check=true` 기본 on(60초) — **2026-09-02 개정: 배치가 crypto_key 메타 위반도 검사**(위반 + ACTIVE 존재 시 키 전체 정지, `KeyIntegrityGuard.enforceKey`). created_at 은 정규화 대상 아님(목록 조회는 플래그만). **위반 증거 보존(2026-09-02)**: 자동 정지 전이가 위반 행(key_material·crypto_key)의 해시를 재계산해 변조를 "정상"으로 재봉인하던 문제 수정 — 위반 시점의 저장 해시를 되살려 integrityValid=false 가 유지됨. 복구 시 재계산: 버전은 REACTIVATE(②단계), 키 메타는 PUT 수정 저장 · `key_usage_log`에 호출자(actor) 컬럼 없음(행위자는 3주차 audit_log). 구현 설계 원고: `D:\회사\아이넵\과제\과제4주차_구현설계_KMS키관리.md`
- 구현: `KeyState` enum + `KeyStateMachine`(전이 표·`transition()`이 검증·이력·키 상태 재계산), `KeyRotationService`, `KeyLifecycleScheduler`, `KeyIntegrityGuard`(언래핑 전 검증·자동 정지)
- 제약(문서화): Compromised 미채택(침해 시 DEACTIVATE+ROTATE) · 삭제 유예(72h) 미채택 · Re-encrypt API 없음(구 버전 데이터 재암호화는 외부 시스템 책임, 상세의 lastUsedAt·usageCount로 보조) · 무결성 위반 자동 정지는 메타 해시 불일치 기준이며 재료 자체는 GCM 래핑으로 보호되므로, 언래핑 검증을 통과하면 REACTIVATE로 복구해 재암호화에 사용

### 데이터 보호 3방식 (혼동 금지)

| 데이터 | 방식 | 컬럼 |
|---|---|---|
| 비밀번호 (admin_user·app_user) | BCrypt (적응형 단방향 해시, salt는 해시 문자열에 내장 — 별도 컬럼 없음) | password_hash |
| 연락처·이메일·첨부파일 | AES-256-GCM(마스터키) + Base64 저장 | phone_enc, email_enc, iv, enc_ver |
| 행 무결성 | HMAC-SHA256 (`WrappedSecretStore.integrityKey()` — crypto_config에 래핑 보관) | integrity_hash / prev_hash·row_hash |

- 암호화 컬럼은 평문 LIKE 불가 → HMAC 기반 `phone_hash`/`email_hash`로 **정확검색만** 지원
- 목록/상세 응답은 개인정보 마스킹 — 연락처는 앞 3자리만 남김(`010-****-****`), 이메일은 `@`와 `.`만 남기되 별표는 고정 개수 — 로컬 `****`·도메인 첫 구간 `****`·이후 구간 `**`, 글자 수 비노출 (2026-09-02 확정). 원문 조회는 `POST /api/users/{id}/plain`(사유 필수), ADMIN 권한 한정 + 감사로그 필수 기록
- `enc_ver`(기본 1): 어느 세대 마스터키로 암호화했는지 표시. 패스프레이즈 변경(전체 재암호화)은 과제 범위 외이며 제약으로만 문서화

### 무결성·감사로그 (구현 3주차)

- 무결성 대상: `crypto_key`, `app_user`, `audit_log`. 등록/수정 시 해시 계산, 조회 시 재계산 대조 → 불일치 시 `integrityValid: false` 플래그 응답 및 대시보드 위반 건수 집계
- **정규화 규칙 (흔들리면 전체 검증 실패하므로 엄수)**: 구분자 파이프(`|`), null은 빈 문자열, 날짜는 KST `yyyy-MM-dd HH:mm:ss`
  - crypto_key: `key_uid|key_name|algorithm|key_size|mode|purpose|status|current_version|auto_rotate|rotation_period_days` (2026-08-28 개정)
  - key_material: `key_id|version|state|wrapped_key|iv|wrap_algo|activation_date` — 위반 시 해당 버전 자동 DEACTIVATED
  - app_user: `name|password_hash|status|enc_ver`
- `audit_log`는 **append-only** (UPDATE/DELETE 금지), 해시 체인: `row_hash = H(prev_hash + 현재 행 핵심 데이터)`, 최초 행의 prev_hash는 `"EMPTY"`. 검증 API가 행 변조와 중간 삭제/삽입을 구간으로 반환
- 모든 관리자 행위(로그인, 키 생성/상태변경, 원문 조회, 수정 등)를 감사로그에 자동 기록 — AOP나 공통 계층으로 처리 권장

### DB 테이블 (10개)

`crypto_config`(salt·kcv, 비밀 아님) · `admin_user` · `crypto_key`(논리 키: key_uid=UUID, algorithm·key_size·mode·purpose, `status`(파생)·`current_version`, `auto_rotate`·`rotation_period_days`·`next_rotation_at`, integrity_hash) · `key_material`(버전 = 상태 주체: `key_id, version, state, deactivation_trigger, wrapped_key(NULL=폐기), iv, wrap_algo, public_key(비대칭), activation_date, destroyed_at, integrity_hash`, UNIQUE(key_id, version), 1:N) · `key_status_history`(version·trigger 포함) · `key_usage_log`(version 포함, iv는 암호문에 내장) · `app_user` · `notice` · `notice_file` · `audit_log`

### API 공통 규격

- 응답: `{ success, data, message, errorCode }` 통일 / 페이징: `{ content, page, size, totalElements, totalPages }`
- HTTP: 400 검증·전이규칙 위반 / 401 미인증 / 403 권한없음 / 404 없음 / 409 상태충돌·무결성 위반
- 저장은 UTC(타임존 포함), 응답은 KST `yyyy-MM-dd HH:mm:ss`
- 외부 식별자는 순번 대신 `key_uid`(UUID) 노출
- 엔드포인트 전체 목록(32개)과 주차별 착수 계획은 설계 문서 7장 참조: `/api/auth/*`(1주) → `/api/keys/*`+테스트(2주) → `/api/users/*`+`/api/audit-logs/*`(3주) → `/api/notices/*`+`/api/files/*`+`/api/dashboard/*`(4주)

## 배포 (CI/CD)

main 푸시 → self-hosted runner(개발 서버 내)가 Gradle bootJar 빌드 → 백엔드·프론트엔드 Docker 이미지 빌드 후 DockerHub 푸시 → SSH로 서버에서 pull & 재기동.
컨테이너 구성: `backend`(8080, `--env-file /home/dguard/kms.env`로 비밀 주입)와 `frontend`(Nginx, 80 포트)가 `ineb-net` 네트워크로 연결. Nginx가 `/api`와 `/swagger-ui`·`/v3/api-docs`를 backend로 리버스 프록시.
**Homework4 하위 어느 경로든 main에 푸시하면 배포가 트리거되므로 주의.**

## 주의사항

- 안내서와 설계 문서가 다른 부분은 **설계 문서(류재민 설계)가 확정안**이다: Java 25/Boot 4/React 19/TS, PBKDF2 10,000회, 암호문 Base64 문자열 저장. 단 **키 생명주기는 2026-08-28 KMIP 4종+네이버식 버전 운영으로 재확정**되어 설계 문서(7종)보다 CLAUDE.md의 생명주기 절이 우선 (설계 문서 개정 예정)
- 비밀번호는 **BCrypt** (2026-08-20 사용자 지시로 SHA-256+Salt에서 변경, password_salt 컬럼 없음 — 설계 문서 docx에는 아직 미반영일 수 있음)
- 실제 고객 데이터·운영 키 사용 금지, 샘플 데이터만 사용
- 범위 밖 기능 추가는 멘토 승인 후에만 진행 (심화: 코드·정책 관리 화면, `/api/keys/{id}/rotate`)
