# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**모든 응답은 한국어로 작성한다.**

## 프로젝트 개요

아이넵 통합키관리 솔루션(D'GuardKMS)을 학습용으로 축소한 **암호키 관리 어드민 웹** 과제(Homework4).
KMS 관리 키의 생명주기 관리 + 암복호화 테스트를 본체로 하고, 사용자 관리와 게시판(공지·첨부파일 암호화)을 얹는다.
수행자: 류재민 (개별 과제, 개발 서버 192.168.200.52). 구현 기간 2026-08-17 ~ 09-11, 최종 발표 09-14.

참고 문서 (요구사항·설계의 원본, 코드보다 우선):
- 과제 안내서: `"D:\회사\아이넵\과제\과제4\C2_DGuardKMS_어드민웹_과제안내서.html"`
- 설계 문서: `"D:\회사\아이넵\과제\과제4\류재민_아이넵 솔루션 어드민 웹 개발 과제 설계.docx"`
- UI 목업(정적 HTML, 화면 설계 참고용): `D:\회사\아이넵\과제\과제4\ineb-kms-mockup\`

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
- 기동 필수 환경변수: `KMS_MASTER_PASSPHRASE`, `JWT_SECRET`(32바이트 이상). 선택: `KMS_ADMIN_INIT_PASSWORD`
- 로컬 개발 DB(localhost:5432)의 crypto_config에 salt/KCV가 이미 확정돼 있음 — 틀린 패스프레이즈로 기동하면 정상적으로 기동 실패함

미구현: KMS 키 관리·암복호화 테스트(2주차), 사용자 관리·감사로그·무결성(3주차), 게시판·대시보드(4주차)

## 핵심 아키텍처 (설계 문서 기준)

### 키 계층 — 두 가지 키의 역할 구분 (과제의 핵심)

1. **마스터키** (AES-256): 어드민이 자기 DB에 저장하는 데이터를 보호. ① 사용자 개인정보(phone_enc/email_enc) 암호화 ② 게시판 첨부파일 암호화 ③ KMS 관리 키 래핑. DB·파일에 저장하지 않고 기동 시 메모리에서만 유도.
2. **KMS 관리 키**: 외부 시스템 연동용 키 자산. 어드민의 역할은 생명주기 관리(등록·상태 전이·이력·사용 로그)와 암복호화 테스트 제공뿐. 어드민 자체 데이터에는 절대 사용하지 않는다.

### 마스터키 유도·검증 (구현 1주차, 발표 시연 항목)

- 기동 시 1회: `PBKDF2-HMAC-SHA256(env.KMS_MASTER_PASSPHRASE, crypto_config의 salt, 10,000회, 32바이트)` — 설계 문서가 반복 횟수를 10,000회로 확정함 (안내서 예시는 210,000회였음)
- **KCV 검증 fail-fast**: 유도 직후 고정 문자열 `"KMS-KCV-V1"`을 암호화해 `crypto_config`의 kcv와 대조, 불일치 시 예외를 던져 기동 중단. "경고만 남기고 진행" 금지 — 틀린 키로 신규 데이터가 암호화되는 사고 방지가 목적
- 메모리 취급: 마스터키는 `byte[]`로만 보관, 사용 직후 `Arrays.fill(key, (byte)0)` zeroize. **`String` 사용 금지** (힙덤프 노출)
- 운영 주입: `/home/dguard/kms.env` (권한 600) → Docker `--env-file`. `KMS_MASTER_PASSPHRASE`와 `INTEGRITY_HMAC_KEY`는 별개의 값

### KMS 관리 키 생명주기 (구현 2주차)

상태 7종: CREATED / ACTIVE / DISTRIBUTED / RENEWED / EXPIRED / INACTIVE / DESTROYED
(설계 문서가 안내서의 5종에 DISTRIBUTED·RENEWED를 추가함)

- 허용 전이: CREATED→ACTIVE · ACTIVE↔DISTRIBUTED · ACTIVE/EXPIRED→RENEWED · RENEWED→ACTIVE · ACTIVE→EXPIRED/INACTIVE · EXPIRED→INACTIVE · EXPIRED/INACTIVE→ACTIVE(재활성, 사유 필수) · INACTIVE/EXPIRED→DESTROYED
- 금지 전이: DESTROYED에서 나가는 모든 전이(종단 상태), CREATED→DESTROYED 직행, 정의 외 임의 역행. 위반 시 HTTP 400/409
- 상태 변경(PATCH `/api/keys/{id}/status`)은 사유(reason) 필수 + `key_status_history` 자동 기록
- **암복호화 테스트는 ACTIVE(·DISTRIBUTED·RENEWED) 상태에서만 허용**, 호출은 `key_usage_log`에 기록
- 키 값은 API로 절대 반환하지 않는다. 래핑: SecureRandom으로 생성 → 마스터키 AES-256-GCM 래핑 → Base64 → `key_material.wrapped_key` (+iv, wrap_algo). 언래핑 후 사용 즉시 zeroize

### 데이터 보호 3방식 (혼동 금지)

| 데이터 | 방식 | 컬럼 |
|---|---|---|
| 비밀번호 (admin_user·app_user) | BCrypt (적응형 단방향 해시, salt는 해시 문자열에 내장 — 별도 컬럼 없음) | password_hash |
| 연락처·이메일·첨부파일 | AES-256-GCM(마스터키) + Base64 저장 | phone_enc, email_enc, iv, enc_ver |
| 행 무결성 | HMAC-SHA256 (`INTEGRITY_HMAC_KEY`) | integrity_hash / prev_hash·row_hash |

- 암호화 컬럼은 평문 LIKE 불가 → HMAC 기반 `phone_hash`/`email_hash`로 **정확검색만** 지원
- 목록/상세 응답은 개인정보 마스킹(예: 010-****-1234). 원문 조회 `GET /api/users/{id}/plain`은 ADMIN 권한 한정 + 감사로그 필수 기록
- `enc_ver`(기본 1): 어느 세대 마스터키로 암호화했는지 표시. 패스프레이즈 변경(전체 재암호화)은 과제 범위 외이며 제약으로만 문서화

### 무결성·감사로그 (구현 3주차)

- 무결성 대상: `crypto_key`, `app_user`, `audit_log`. 등록/수정 시 해시 계산, 조회 시 재계산 대조 → 불일치 시 `integrityValid: false` 플래그 응답 및 대시보드 위반 건수 집계
- **정규화 규칙 (흔들리면 전체 검증 실패하므로 엄수)**: 구분자 파이프(`|`), null은 빈 문자열, 날짜는 KST `yyyy-MM-dd HH:mm:ss`
  - crypto_key: `key_uid|key_name|algorithm|key_size|purpose|status|version|expire_at`
  - app_user: `name|password_hash|status|enc_ver`
- `audit_log`는 **append-only** (UPDATE/DELETE 금지), 해시 체인: `row_hash = H(prev_hash + 현재 행 핵심 데이터)`, 최초 행의 prev_hash는 `"EMPTY"`. 검증 API가 행 변조와 중간 삭제/삽입을 구간으로 반환
- 모든 관리자 행위(로그인, 키 생성/상태변경, 원문 조회, 수정 등)를 감사로그에 자동 기록 — AOP나 공통 계층으로 처리 권장

### DB 테이블 (10개)

`crypto_config`(salt·kcv, 비밀 아님) · `admin_user` · `crypto_key`(키 메타, key_uid=UUID) · `key_material`(래핑 키 값) · `key_status_history` · `key_usage_log` · `app_user` · `notice` · `notice_file` · `audit_log`

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

- 안내서와 설계 문서가 다른 부분은 **설계 문서(류재민 설계)가 확정안**이다: Java 25/Boot 4/React 19/TS, PBKDF2 10,000회, 생명주기 7종, 암호문 Base64 문자열 저장
- 비밀번호는 **BCrypt** (2026-08-20 사용자 지시로 SHA-256+Salt에서 변경, password_salt 컬럼 없음 — 설계 문서 docx에는 아직 미반영일 수 있음)
- 실제 고객 데이터·운영 키 사용 금지, 샘플 데이터만 사용
- 범위 밖 기능 추가는 멘토 승인 후에만 진행 (심화: 코드·정책 관리 화면, `/api/keys/{id}/rotate`)
