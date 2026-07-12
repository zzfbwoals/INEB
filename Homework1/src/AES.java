import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Scanner;

public class AES {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        SecureRandom random = new SecureRandom();

        while(true) {
            try {
                System.out.println("==================================================");
                System.out.println("               [AES CLI 암/복호화 도구]   ");
                System.out.println("==================================================");

                // 1. 작업 선택
                System.out.print("작업을 선택하세요 (1: 암호화, 2: 복호화, 0: 종료): ");
                int mode = Integer.parseInt(scanner.nextLine().trim());

                if (mode == 0) {
                    System.out.println("프로그램을 종료합니다.");
                    scanner.close();
                    return;
                }

                // 2. 운용 모드 선택
                System.out.print("운용 모드를 선택하세요 (ECB, CBC, CTR, GCM): ");
                String opMode = scanner.nextLine().trim().toUpperCase();

                // 변환 문자열(Transformation) 및 IV 길이 설정
                String transformation = "";
                int ivLength = 16; // 기본 16바이트

                switch (opMode) {
                    case "ECB":
                        transformation = "AES/ECB/PKCS5Padding";
                        break;
                    case "CBC":
                        transformation = "AES/CBC/PKCS5Padding";
                        break;
                    case "CTR":
                        transformation = "AES/CTR/NoPadding";
                        break;
                    case "GCM":
                        transformation = "AES/GCM/NoPadding";
                        ivLength = 12; // GCM은 12바이트(96비트) IV가 표준 권장 사항
                        break;
                    default:
                        System.out.println("지원하지 않는 모드입니다.");
                        continue;
                }

                // 3. 암호화 / 복호화 로직 수행
                if (mode == 1) {
                    // --- 암호화 모드 ---
                    System.out.print("암호화할 평문을 입력하세요: ");
                    String plainText = scanner.nextLine();

                    // 256비트(32바이트) 비밀키 자동 생성
                    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                    keyGen.init(256);
                    SecretKey secretKey = keyGen.generateKey();
                    String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());

                    // IV 생성 (ECB 제외)
                    byte[] iv = new byte[ivLength];
                    String encodedIv = "";
                    if (!opMode.equals("ECB")) {
                        random.nextBytes(iv);
                        encodedIv = Base64.getEncoder().encodeToString(iv);
                    }

                    // 암호화 수행
                    Cipher cipher = Cipher.getInstance(transformation);
                    if (opMode.equals("ECB")) {
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                    } else if (opMode.equals("GCM")) {
                        // GCM은 인증 태그 길이(기본 128비트) 지정 필요
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
                    } else {
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
                    }

                    byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
                    String encryptedText = Base64.getEncoder().encodeToString(encryptedBytes);

                    // 결과 출력
                    System.out.println("\n--- 암호화 결과 ---");
                    System.out.println("[Secret Key (Base64)]: " + encodedKey);
                    if (!opMode.equals("ECB")) {
                        System.out.println("[IV (Base64)]: " + encodedIv);
                    }
                    System.out.println("[Encrypted Text (Base64)]: " + encryptedText);

                } else if (mode == 2) {
                    // --- 복호화 모드 ---
                    System.out.print("Secret Key (Base64)를 입력하세요: ");
                    String encodedKey = scanner.nextLine().trim();
                    byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
                    SecretKey secretKey = new SecretKeySpec(decodedKey, "AES");

                    byte[] iv = null;
                    if (!opMode.equals("ECB")) {
                        System.out.print("IV (Base64)를 입력하세요: ");
                        String encodedIv = scanner.nextLine().trim();
                        iv = Base64.getDecoder().decode(encodedIv);
                    }

                    System.out.print("복호화할 암호문 (Base64)을 입력하세요: ");
                    String encryptedText = scanner.nextLine().trim();
                    byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);

                    // 복호화 수행
                    Cipher cipher = Cipher.getInstance(transformation);
                    if (opMode.equals("ECB")) {
                        cipher.init(Cipher.DECRYPT_MODE, secretKey);
                    } else if (opMode.equals("GCM")) {
                        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
                    } else {
                        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
                    }

                    byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
                    String decryptedText = new String(decryptedBytes, StandardCharsets.UTF_8);

                    // 결과 출력
                    System.out.println("\n--- 복호화 결과 ---");
                    System.out.println("[Decrypted Plain Text]: " + decryptedText);
                } else {
                    System.out.println("올바른 작업 번호를 선택하세요.");
                }

            } catch (Exception e) {
                System.out.println("\n[오류 발생] 입력한 정보가 올바르지 않거나 복호화에 실패했습니다.");
            }
            System.out.println("\n==================================================");
            System.out.println("                       [종료]                   ");
            System.out.println("==================================================");
            System.out.println("\n\n");
        }
    }
}