## Homework1 - 암호학 개론 과제 (이홍일 전무님)
### AES 암호화 CLI 프로그램
- ECB, CBC, CTR, GCM 모드 정보와 데이터를 입력받아 암/복호화하는 CLI 도구 작성
### 서명 및 검증을 수행하는 CLI 프로그램
- Openssl을 사용하여 CA 구축 및 X.509 인증서 발행
- RSA로 서명 및 검증 프로세스 구현
- X.509 인증서 체인 검증 로직 작성
## Homework2 - SHA-256, AES-128, RSA 과제 (대표님)
### SHA-256
- 완전 구현
### AES-128
- S-box, ShiftRows, MixColumns(갈루아 필드 곱), 키 확장까지 전체를 다 할수 있음하고, 아니면 평문 한 블록(16바이트)을 ECB로 암호화하는 것까지만.
### RSA
- 큰 소수·OAEP 패딩·부채널 방어까지 하는 프로덕션 RSA는 금지.
- 작은 숫자(예: p=61, q=53)로 키 생성 → 모듈러 지수승 암복호화
## Homework3 - 패스워드 및 봉투 암복호화 과제 (이홍일 전무님)
### 패스워드를 입력받아 파일을 암호화
- AES-256-GCM + PBKDF2로 패스워드 기반 파일 암·복호화
### 봉투 암호화
- 수신자 공개키로 파일을 암호화해 보내고, 수신자만 수신자 개인키로 복호화할 수 있는 기능
- 랜덤 DEK 생성 → AES-GCM으로 파일 암호화 → DEK는 수신자 RSA/ECIES 공개키로 래핑
- 다중 수신자 지원 (DEK 하나를 N명 공개키로 각각 래핑)
## Homework4 - 아이넵 솔루션 어드민 웹 개발 과제 (강재홍 상무님)
[C2_DGuardKMS_어드민웹_과제안내서.html](https://github.com/user-attachments/files/30968516/C2_DGuardKMS_._.html)

[![Deploy INEB KMS Project](https://github.com/zzfbwoals/INEB/actions/workflows/deploy.yml/badge.svg)](https://github.com/zzfbwoals/INEB/actions/workflows/deploy.yml)
