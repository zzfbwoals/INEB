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
            // [1단계] X.509 인증서 체인 검증 (자동 수행)
            System.out.println("\n[STEP 1] X.509 인증서 체인 검증을 시작합니다.");
            verifyChain(ROOT_CRT, SUB_CRT, USER_CRT);
            System.out.println("-> [STEP 1 성공] 신뢰할 수 있는 인증서 체인입니다.");

            // [2단계] 사용자 입력 데이터 서명 생성
            System.out.println("\n[STEP 2] 사용자 개인키로 데이터 서명을 생성합니다.");
            System.out.print("  > 서명할 원본 데이터를 입력하세요: ");
            String plainText = scanner.nextLine();

            String base64Signature = signData(USER_KEY, plainText);
            System.out.println("-> [STEP 2 성공] 디지털 서명이 성공적으로 생성되었습니다.");

            // [3단계] 검증 데이터 및 서명 입력받아 검증
            System.out.println("\n[STEP 3] 사용자 인증서(공개키)로 서명 무결성을 검증합니다.");

            System.out.print("  > 검증할 대상 데이터를 입력하세요 (방금 입력한 원문): ");
            String verifyText = scanner.nextLine();

            System.out.print("  > 검증할 Base64 서명 값을 입력하세요: ");
            String inputSignature = scanner.nextLine().trim();

            verifySignature(USER_CRT, verifyText, inputSignature);

        } catch (FileNotFoundException e) {
            System.err.println("\n[오류] 인증서 및 키 파일을 찾을 수 없습니다.");
            System.err.println("확인 필요 파일: " + ROOT_CRT + ", " + SUB_CRT + ", " + USER_CRT + ", " + USER_KEY);
            System.err.println("상세 메시지: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("\n[오류] 프로세스 실행 중 암호학적 예외가 발생했습니다.");
            System.err.println("상세 메시지: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }

        System.out.println("\n==================================================");
        System.out.println("                       [종료]        ");
        System.out.println("==================================================");
    }

    // 1. X.509 인증서 체인 검증 로직
    private static void verifyChain(String rootPath, String subPath, String userPath) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        X509Certificate rootCert = (X509Certificate) cf.generateCertificate(new FileInputStream(rootPath));
        X509Certificate subCert = (X509Certificate) cf.generateCertificate(new FileInputStream(subPath));
        X509Certificate userCert = (X509Certificate) cf.generateCertificate(new FileInputStream(userPath));

        System.out.println("  > [1-1] 루트 CA 자체 서명 검증 중...");
        rootCert.verify(rootCert.getPublicKey());
        System.out.println("    - 루트 CA 검증 완료 (Self-signed OK)");

        System.out.println("  > [1-2] 하위 CA 인증서를 루트 CA 공개키로 검증 중...");
        subCert.verify(rootCert.getPublicKey());
        System.out.println("    - 하위 CA 인증서 검증 완료 (상위 상속 OK)");

        System.out.println("  > [1-3] 사용자 인증서를 하위 CA 공개키로 검증 중...");
        userCert.verify(subCert.getPublicKey());
        System.out.println("    - 사용자 인증서 체인 검증 최종 완료!");
    }

    // 2. RSA 개인키로 서명 구현
    private static String signData(String keyPath, String plainText) throws Exception {
        PrivateKey privateKey = loadPrivateKey(keyPath);

        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(privateKey);
        privateSignature.update(plainText.getBytes("UTF-8"));

        byte[] signature = privateSignature.sign();
        String base64Signature = Base64.getEncoder().encodeToString(signature);

        System.out.println("\n  [발급된 서명 결과]");
        System.out.println("  > 입력된 데이터: " + plainText);
        System.out.println("  > 생성된 서명(Base64): " + base64Signature);
        System.out.println("  --------------------------------------------------");
        return base64Signature;
    }

    // 3. X.509 인증서 내의 공개키로 서명 검증 구현
    private static void verifySignature(String certPath, String plainText, String base64Signature) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate userCert = (X509Certificate) cf.generateCertificate(new FileInputStream(certPath));

        PublicKey publicKey = userCert.getPublicKey();

        Signature publicSignature = Signature.getInstance("SHA256withRSA");
        publicSignature.initVerify(publicKey);
        publicSignature.update(plainText.getBytes("UTF-8"));

        try {
            byte[] signatureBytes = Base64.getDecoder().decode(base64Signature);
            boolean isCorrect = publicSignature.verify(signatureBytes);

            System.out.println("\n  [검증 수행 결과]");
            System.out.println("  > 검증 대상 데이터: " + plainText);
            if (isCorrect) {
                System.out.println("  > [결과] SUCCESS: 서명이 올바르며 위·변조가 없습니다!");
            } else {
                System.out.println("  > [결과] FAIL: 서명이 일치하지 않거나 데이터가 위조되었습니다.");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("\n  > [결과] FAIL: 입력된 서명이 올바른 Base64 형식이 아닙니다.");
        }
    }

    // 개인키 로드 함수
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