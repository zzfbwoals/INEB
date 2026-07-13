import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Scanner;

public class RSA {

    private static final String ROOT_CRT = "rootCA.crt";
    private static final String SUB_CRT = "subCA.crt";
    private static final String USER_CRT = "user.crt"; // fake_user.crt
    private static final String USER_KEY = "user.key";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("==================================================");
        System.out.println("        [서명 및 검증을 수행하는 CLI 프로그램]   ");
        System.out.println("==================================================");

        try {
            System.out.println("\n[1단계: 서명 생성] 사용자가 자신의 개인키로 데이터를 암호화합니다.");
            System.out.print("  > 송신할 원본 데이터를 입력하세요: ");
            String plainText = scanner.nextLine();

            // 사용자 개인키로 SHA-256 해시 기반 RSA 서명 생성
            String base64Signature = signData(USER_KEY, plainText);
            System.out.println("-> [1단계 완료] 사용자가 [데이터 + 전자 서명 + 사용자 인증서]를 전송했습니다.");
            System.out.println("   --------------------------------------------------");

            System.out.println("\n[2단계: 신뢰 사슬 검증] 검증자가 사용자 인증서의 유효성을 역추적합니다.");
            verifyChain(ROOT_CRT, SUB_CRT, USER_CRT);
            System.out.println("-> [2단계 완료] 신뢰 사슬 검증 성공! \"이 사용자 인증서는 진짜입니다.\"");
            System.out.println("   --------------------------------------------------");

            System.out.println("\n[3단계: 최종 서명 검증] 인증서의 공개키로 데이터 위·변조를 확인합니다.");

            System.out.print("  > 수신된 대상 데이터를 입력하세요 (검증용): ");
            String verifyText = scanner.nextLine();

            System.out.print("  > 수신된 Base64 서명 값을 입력하세요 (검증용): ");
            String inputSignature = scanner.nextLine().trim();

            verifySignature(USER_CRT, verifyText, inputSignature);

        } catch (FileNotFoundException e) {
            System.err.println("\n[오류] 필요한 인증서 및 키 파일을 찾을 수 없습니다.");
            System.err.println("확인 필요 파일: " + ROOT_CRT + ", " + SUB_CRT + ", " + USER_CRT + ", " + USER_KEY);
            System.err.println("상세 메시지: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("\n[오류] 암호학적 검증 프로세스 중 예외가 발생하여 중단되었습니다.");
            System.err.println("상세 메시지: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }

        System.out.println("\n==================================================");
        System.out.println("                       [종료]        ");
        System.out.println("==================================================");
    }

    // 1. RSA 개인키로 서명 생성 (SHA-256 해시 자동 결합)
    private static String signData(String keyPath, String plainText) throws Exception {
        PrivateKey privateKey = loadPrivateKey(keyPath);

        // 내부적으로 SHA-256 해시를 수행하고 복잡한 수학 연산인 RSA로 해시값을 암호화
        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(privateKey);
        privateSignature.update(plainText.getBytes("UTF-8"));

        byte[] signature = privateSignature.sign();
        String base64Signature = Base64.getEncoder().encodeToString(signature);

        System.out.println("\n  [발급된 서명 결과]");
        System.out.println("  > 데이터 내용: " + plainText);
        System.out.println("  > 전자 서명(Base64): " + base64Signature);
        return base64Signature;
    }

    // 2. X.509 인증서 체인 신뢰고리 역추적 검증
    private static void verifyChain(String rootPath, String subPath, String userPath) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        // X.509 표준 기술 형식을 준수한 전자 인증서 객체 로드
        X509Certificate rootCert = (X509Certificate) cf.generateCertificate(new FileInputStream(rootPath));
        X509Certificate subCert = (X509Certificate) cf.generateCertificate(new FileInputStream(subPath));
        X509Certificate userCert = (X509Certificate) cf.generateCertificate(new FileInputStream(userPath));

        // 2-1. 사용자 인증서 뒷면의 Sub CA 서명값 검증
        System.out.println("  > [2-1] 사용자 인증서를 상위 기관(Sub CA)의 공개키로 검증 중...");
        userCert.verify(subCert.getPublicKey());
        System.out.println("    - 사용자 인증서 서명 유효성 검증 완료 (Sub CA 인증 OK)");

        // 2-2. Sub CA 인증서 뒷면의 Root CA 서명값 검증[cite: 1]
        System.out.println("  > [2-2] 하위 CA 인증서를 최상위 기관(Root CA)의 공개키로 검증 중...");
        subCert.verify(rootCert.getPublicKey());
        System.out.println("    - 하위 CA 인증서 서명 유효성 검증 완료 (Root CA 인증 OK)");

        // 2-3. Root CA 자체 서명(Self-Signed) 및 시스템 신뢰 확정 검증
        System.out.println("  > [2-3] 루트 CA 자체 서명 유효성 검증 중...");
        rootCert.verify(rootCert.getPublicKey());
        System.out.println("    - 루트 CA 자체 서명 검증 완료 (자체 보증 신뢰 확정 OK)");
    }

    // 3. 검증된 인증서의 공개키를 꺼내 최종 서명 검증 수행
    private static void verifySignature(String certPath, String plainText, String base64Signature) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate userCert = (X509Certificate) cf.generateCertificate(new FileInputStream(certPath));

        // 안전함이 입증된 전자인증서 양식 내부의 [주체 공개 키] 추출[cite: 1]
        PublicKey publicKey = userCert.getPublicKey();

        Signature publicSignature = Signature.getInstance("SHA256withRSA");
        publicSignature.initVerify(publicKey);
        publicSignature.update(plainText.getBytes("UTF-8"));

        try {
            byte[] signatureBytes = Base64.getDecoder().decode(base64Signature);

            // 서명을 공개키로 복호화한 다이제스트와 원문 해시 다이제스트를 최종 비교[cite: 1]
            boolean isCorrect = publicSignature.verify(signatureBytes);

            System.out.println("\n  [최종 무결성 판독 결과]");
            System.out.println("  > 수신 및 판독된 데이터: " + plainText);
            if (isCorrect) {
                System.out.println("  > [결과] SUCCESS: 서명이 올바르며 데이터가 전혀 위·변조되지 않았습니다! (무결성 보장)");
            } else {
                System.out.println("  > [결과] FAIL: 서명이 일치하지 않거나 데이터 내용이 수정되었습니다. (위·변조 탐지)");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("\n  > [결과] FAIL: 입력된 서명이 규격에 맞지 않는 손상된 포맷입니다.");
        }
    }

    // 개인키 파일 로드 로직
    private static PrivateKey loadPrivateKey(String filename) throws Exception {
        String keyPem = new String(Files.readAllBytes(Paths.get(filename)))
                .replaceAll("-----\\s*BEGIN PRIVATE KEY\\s*-----", "")
                .replaceAll("-----\\s*END PRIVATE KEY\\s*-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(keyPem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}