/*
류재민_코딩 과제 1주차
(AI 코드 요청 일절 X, AI는 이론 설명 시에 사용 [ex.ECIES가 뭐야? 등], 필요한 함수 설명 및 사용법 - 블로그 참조)
(1) 패스워드 입력받아 파일 암/복호화
(2) 봉투 암/복호화
*/

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.InputMismatchException;
import java.util.Scanner;

public class PasswordAndEnvelopeEncryption {
    public static void main(String[] args) throws Exception {

        while (true) {
            Start();
            // (1)패스워드 암|복호화, (2)봉투 암|복호화 선택
            int menu = SelectMenu();
            if (menu == 1) {
                AesWithPassword();
            } else if (menu == 2) {
                AesWithEnvelope();
            } else if (menu == 0) {
                End();
                break;
            }
            End();
        }
    }

    // (https://kingchae.tistory.com/19 참고)
    private static final Scanner scanner = new Scanner(System.in);
    private static void Start() {
        System.out.println("==================================================");
        System.out.println("              [파일 암호화 CLI 프로그램]              ");
        System.out.println("==================================================");
    }
    private static void End() {
        System.out.println("==================================================");
        System.out.println("                    [프로그램 종료]                  ");
        System.out.println("==================================================\n\n");
    }
    private static int SelectMenu() {
        int menu;
        while (true) {
            System.out.print("메뉴를 선택하세요.\n(1)패스워드 암|복호화, (2)봉투 암|복호화, (0)종료: ");
            try {
                menu = scanner.nextInt();
                scanner.nextLine();
                if (menu != 0 && menu != 1 && menu != 2) {
                    System.out.println("올바른 메뉴를 입력해주세요.\n");
                } else {
                    break;
                }
            } catch (InputMismatchException e){
                System.out.println("정수를 입력해주세요.\n");
                scanner.next();
            }
        }
        return menu;
    }
    private static int SelectMode() {
        int mode;
        while (true) {
            System.out.print("모드를 선택하세요.\n(1)암호화, (2)복호화, (0)종료: ");
            try {
                mode = scanner.nextInt();
                scanner.nextLine();
                if (mode != 0 && mode != 1 && mode != 2) {
                    System.out.println("올바른 모드를 입력해주세요.\n");
                } else {
                    break;
                }
            } catch (InputMismatchException e){
                System.out.println("정수를 입력해주세요.\n");
                scanner.next();
            }
        }
        return mode;
    }
    // 16 바이트 Salt 생성 메서드
    private static byte[] CreateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }
    // 12 바이트 (GCM 권장 크기) IV 생성 메서드
    private static byte[] CreateIV() {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
    // "text/plaintext.txt" 파일 읽기 메서드 (https://ittrue.tistory.com/168,https://onlyfor-me-blog.tistory.com/317 참고)
    private static String FileLoad(String fileName) throws Exception {
        StringBuilder plainText = new StringBuilder();
        try (FileReader fileReader = new FileReader(fileName)) {
            int ch;
            while ((ch = fileReader.read()) != -1) {
                plainText.append((char) ch);
            }
        } catch (IOException e) { throw new IOException("파일을 읽지 못했습니다.\n", e); }
        return plainText.toString();
    }
    // "text/ciphertext.txt" 파일 저장 메서드 (https://ittrue.tistory.com/168 참고)
    private static void FileSave(String fileName, String cipherText) throws Exception {
        try (FileWriter fileWriter = new FileWriter(fileName)) {
            fileWriter.write(cipherText);
        } catch (IOException e) { throw new IOException("파일을 저장하지 못했습니다.\n", e); }
    }
    // IV 입력 메서드
    private static byte[] InputIV() {
        System.out.print("IV를 입력하세요: ");
        byte[] iv = Base64.getDecoder().decode(scanner.nextLine());
        return iv;
    }

    //--------------------------(1) 패스워드를 입력받아 파일을 암호화--------------------------

    // 비밀번호 입력 메서드
    private static String InputPassword() {
        System.out.print("패스워드를 입력하세요: ");
        String password = scanner.nextLine();
        return password;
    }
    // Salt 입력 메서드
    private static byte[] InputSalt() {
        System.out.print("Slat를 입력하세요: ");
        byte[] salt = Base64.getDecoder().decode(scanner.nextLine());
        return salt;
    }
    // PBKDF2 활용 256 비트 키 생성 메서드 (https://m.blog.naver.com/renucs/223423882344 참고)
    private static SecretKeySpec CreatePbkdf2Key(String password, byte[] salt) throws Exception {
        // https://learn.microsoft.com/ko-kr/dotnet/api/javax.crypto.spec.pbekeyspec.-ctor?view=net-android-34.0 참고
        PBEKeySpec pbeKeySpec = new PBEKeySpec(password.toCharArray(), salt, 600000, 256);
        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        SecretKey key = secretKeyFactory.generateSecret(pbeKeySpec);
        byte[] bytes = key.getEncoded();
        return new SecretKeySpec(bytes, "AES");
    }
    // (1) 패스워드 입력받아 파일 암|복호화 (https://true-false.tistory.com/131, https://hongdori2.tistory.com/173 참고)
    private static void AesWithPassword() throws Exception {
        int mode = SelectMode();
        if (mode == 0) return;

        if (mode == 1) { // 암호화
            // Password, Salt, Key 생성
            String password = InputPassword();
            byte[] salt = CreateSalt();
            SecretKey key = CreatePbkdf2Key(password, salt);

            // IV 생성
            byte[] iv = CreateIV();
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);

            // 파일 속 평문 읽기
            String plainText = FileLoad("text/plaintext.txt");

            // AES-256-GCM 암호화
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
            byte[] cipherText;
            try{
                cipherText = cipher.doFinal(plainText.getBytes());
            } catch (Exception e) { throw new Exception("암호화 실패했습니다.\n", e); }

            // 암호문 파일 속 저장
            FileSave("text/AesPbkdf2CipherText.txt", Base64.getEncoder().encodeToString(cipherText));

            System.out.println("\n평문: " + plainText);
            System.out.println("SALT: " + Base64.getEncoder().encodeToString(salt));
            System.out.println("IV: " + Base64.getEncoder().encodeToString(iv));
            System.out.println("\n암호화를 시작합니다...");
            System.out.println("암호문: " + Base64.getEncoder().encodeToString(cipherText));
        } else if (mode == 2) { // 복호화
            // Password, Salt, Key 생성
            String password = InputPassword();
            byte[] salt = InputSalt();
            SecretKey key = CreatePbkdf2Key(password, salt);

            // IV 생성
            byte[] iv = InputIV();
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);

            // 파일 속 암호문 읽기
            byte[] cipherText = Base64.getDecoder().decode(FileLoad("text/AesPbkdf2CipherText.txt"));

            // AES-256-GCM 복호화
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
            byte[] plainText;
            try {
                plainText = cipher.doFinal(cipherText);
            } catch (Exception e) {
                FileDelete("text/AesPbkdf2CipherText.txt");
                throw new Exception("복호화에 실패했습니다.\n", e);
            }
            FileDelete("text/AesPbkdf2CipherText.txt");

            System.out.println("\n복호화를 시작합니다...");
            System.out.println("평문: " + new String(plainText));
        }
    }

    //--------------------------(2) 봉투 암호화--------------------------

    private static int SelectAlgorithm() {
        int algorithm;

        while (true) {
            System.out.print("알고리즘을 선택하세요.\n(1)RSA, (2)ECIES, (0)종료: ");
            try {
                algorithm = scanner.nextInt();
                scanner.nextLine();
                if (algorithm != 0 && algorithm != 1 && algorithm != 2) {
                    System.out.println("올바른 알고리즘을 입력해주세요.\n");
                } else {
                    break;
                }
            } catch (InputMismatchException e){
                System.out.println("정수를 입력해주세요.\n");
                scanner.next();
            }

        }

        return algorithm;
    }
    private static int SelectReceiver() {
        int N;

        while (true) {
            System.out.print("다중 수신자 N명의 N을 입력하세요: ");
            try {
                N = scanner.nextInt();
                scanner.nextLine();
                if (N<1 || N>10) {
                    System.out.println("1~10의 숫자를 입력해주세요\n");
                    continue;
                }
                break;
            } catch (InputMismatchException e){
                System.out.println("정수를 입력해주세요.\n");
                scanner.next();
            }
        }

        return N;
    }
    // 파일 삭제 메서드 (https://hianna.tistory.com/591 참고)
    private static void FileDelete(String filename) throws Exception {
        Path filePath = Paths.get(filename);
        try {
            Files.delete(filePath);
        } catch (Exception e) { throw new Exception("파일을 삭제하지 못했습니다.\n", e); }
    }
    // 폴더 내 파일 개수 확인 메서드 (https://bestitem.kr/231 참고)
    private static int FileCount(String folderName) throws Exception {
        int count = 0;
        File file = new File(folderName);
        File[] fileList = file.listFiles();
        for(int i=0; i<fileList.length; i++) count++;
        return count;
    }
    // 256비트 키 생성 메서드
    private static SecretKeySpec CreateRandomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new SecretKeySpec(key, "AES");
    }
    // .key 저장 메서드 (https://data-make.tistory.com/758#google_vignette 참고)
    private static void KeySave(String fileName, byte[] key) throws Exception {
        try (FileOutputStream fileOutputStream = new FileOutputStream(fileName)) {
            fileOutputStream.write(key);
        } catch (IOException e) { throw new IOException("파일을 저장하지 못했습니다.\n", e); }
    }
    // 공개키 읽기 메서드 (https://data-make.tistory.com/758#google_vignette 참고)
    private static PublicKey PublicKeyLoad(String fileName, String algorithm) throws Exception {
        File file = new File(fileName);
        byte[] keyBytes = Files.readAllBytes(file.toPath());

        KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(keyBytes);
        return keyFactory.generatePublic(x509EncodedKeySpec);
    }
    // 개인키 읽기 메서드 (https://data-make.tistory.com/758#google_vignette 참고)
    private static PrivateKey PrivateKeyLoad(String fileName, String algorithm) throws Exception {
        File file = new File(fileName);
        byte[] keyBytes = Files.readAllBytes(file.toPath());

        KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(keyBytes);
        return keyFactory.generatePrivate(pkcs8EncodedKeySpec);
    }
    // RSA 공개키로 키 암호화 메서드 (https://data-make.tistory.com/758#google_vignette 참고)
    private static void RsaEncryption(byte[] key) throws Exception {
        // 다중 수신자 N명 선택
        int N = SelectReceiver();

        try {
            for(int i=0; i<N; i++) {
                // 공개키|개인키 쌍 생성
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
                keyPairGenerator.initialize(2048);
                KeyPair keyPair = keyPairGenerator.generateKeyPair();
                PublicKey publicKey = keyPair.getPublic();
                PrivateKey privateKey = keyPair.getPrivate();

                // 개인키 저장
                String privateKeyName = "PrivateKey/private" + (i+1) + ".key";
                KeySave(privateKeyName, privateKey.getEncoded());

                // 공개키로 키 암호화 및 저장
                Cipher cipher = Cipher.getInstance("RSA");
                cipher.init(Cipher.ENCRYPT_MODE, publicKey);
                String dek = Base64.getEncoder().encodeToString(cipher.doFinal(key));
                String dekName = "DEK/dek" + (i+1) + ".txt";
                FileSave(dekName, dek);
            }
        } catch (Exception e) { throw new Exception("암호화 실패했습니다.\n", e); }
    }
    // RSA 개인키로 키 복호화 메서드 (https://data-make.tistory.com/758#google_vignette 참고)
    private static byte[] RsaDecryption() throws Exception {
        // 다중 수신자 N명 선택
        int N = FileCount("PrivateKey/");

        // 맨 처음 dek 복호화
        System.out.println("\nN명의 키 값이 모두 일치하는지 확인합니다...");
        PrivateKey privateKey = PrivateKeyLoad("PrivateKey/private1.key", "RSA");
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] cipherDek = Base64.getDecoder().decode(FileLoad("DEK/dek1.txt"));
        byte[] plainDek = cipher.doFinal(cipherDek);
        byte[] firstDek = plainDek;

        try {
            // 나머지 N-1개의 개인키로 dek 복호화 및 N-1개의 dek가 맨 처음 dek와 모두 일치하는지 확인
            for (int i = 1; i < N; i++) {
                String privateKeyName = "PrivateKey/private" + (i + 1) + ".key";
                privateKey = PrivateKeyLoad(privateKeyName, "RSA");

                cipher.init(Cipher.DECRYPT_MODE, privateKey);

                String dekName = "DEK/dek" + (i + 1) + ".txt";
                cipherDek = Base64.getDecoder().decode(FileLoad(dekName));
                plainDek = cipher.doFinal(cipherDek);

                // 처음 dek값과 현재 dek값 일치 확인
                byte[] currentDek = plainDek;
                if (!Arrays.equals(currentDek, firstDek)) {
                    System.out.println("키가 일치하지 않습니다.\n");
                    // DEK 및 개인키 삭제
                    for (int j=0; j<N; j++) {
                        FileDelete("DEK/dek" + (j + 1) + ".txt");
                        FileDelete("PrivateKey/private" + (j + 1) + ".key");
                    }
                    FileDelete("text/AesRsaCipherText.txt");
                    currentDek = null;
                    return currentDek;
                }
            }
            System.out.println("N명의 키 값이 모두 일치합니다.\n");
        } catch (Exception e) { throw new Exception("복호화 실패했습니다.\n", e); }

        // DEK 및 개인키 삭제
        for (int i=0; i<N; i++) {
            FileDelete("DEK/dek" + (i + 1) + ".txt");
            FileDelete("PrivateKey/private" + (i + 1) + ".key");
        }

        return plainDek; // N개의 dek 값이 모두 일치
    }
    // ECC 공캐키로 복호화 메서드
    // (https://cryptobook.nakov.com/asymmetric-key-ciphers/ecies-public-key-encryption 참고)
    private static SecretKey[] EccEncryption(int N) throws Exception {
        SecretKey[] secretKey = new SecretKey[N];
        try {
            for (int i=0; i<N; i++) {
                // 임시 공개키|개인키 생성 (https://coding-by-head.tistory.com/entry/ECC#google_vignette 참고)
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
                ECGenParameterSpec ecGenParameterSpec = new ECGenParameterSpec("secp256r1");
                keyPairGenerator.initialize(ecGenParameterSpec);
                KeyPair keyPair = keyPairGenerator.generateKeyPair();
                PublicKey tempPublicKey = keyPair.getPublic();
                PrivateKey tempPrivateKey = keyPair.getPrivate();

                PublicKey eccPublicKey = PublicKeyLoad("eccKey/eccpublic.key", "EC");

                // ECDH 공유 비밀키 생성
                // (https://neilmadden.blog/2016/05/20/ephemeral-elliptic-curve-diffie-hellman-key-agreement-in-java/,
                // https://mojoauth.com/keypair-generation/generate-keypair-using-ecdh-with-java#3-perform-key-agreement,
                // https://blog.naver.com/drods/80015158879
                // https://cryptobook.nakov.com/asymmetric-key-ciphers/ecdh-key-exchange,
                // https://docs.oracle.com/javase/8/docs/api/javax/crypto/KeyAgreement.html 참고)
                KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
                keyAgreement.init(tempPrivateKey);
                keyAgreement.doPhase(eccPublicKey, true);
                byte[] key = keyAgreement.generateSecret();

                // KDF 함수로 최종 비밀키 생성 (https://ride-wind.tistory.com/163,
                // https://docs.oracle.com/en/java/javase/24/docs/api/java.base/javax/crypto/spec/HKDFParameterSpec.html,
                // https://docs.oracle.com/en/java/javase/26/docs/api/java.base/javax/crypto/KDF.html 참고)
                byte[] salt = CreateSalt();
                KDF kdf = KDF.getInstance("HKDF-SHA256");
                HKDFParameterSpec hkdfParameterSpec =
                        HKDFParameterSpec.ofExtract()
                                .addIKM(key)
                                .addSalt(salt)
                                .thenExpand(null,32);
                secretKey[i] = kdf.deriveKey("AES", hkdfParameterSpec);

                // 임시 공유키 및 SALT 저장
                String publicKeyName = "tempPublicKey/temppublic" + (i+1) + ".key";
                String saltName = "salt/salt" + (i+1) + ".txt";
                KeySave(publicKeyName, tempPublicKey.getEncoded());
                FileSave(saltName, Base64.getEncoder().encodeToString(salt));
            }
        } catch (Exception e) { throw new Exception("비밀키 생성 실패했습니다.\n", e); }
        return secretKey;
    }
    // ECC 개인키로 키 복호화 메서드 (https://coding-by-head.tistory.com/entry/ECC#google_vignette 참고)
    private static SecretKey[] EccDecryption(int N) throws Exception {
        SecretKey[] secretKey = new SecretKey[N];
        try {
            for (int i=0; i<N; i++) {

                // 임시 공개키 및 고정 개인키 읽기
                PublicKey tempPublicKey = PublicKeyLoad("tempPublicKey/temppublic" + (i+1) + ".key", "EC");
                PrivateKey eccPrivateKey = PrivateKeyLoad("eccKey/eccprivate.key", "EC");

                // ECDH 공유 비밀키 생성
                // (https://neilmadden.blog/2016/05/20/ephemeral-elliptic-curve-diffie-hellman-key-agreement-in-java/,
                // https://mojoauth.com/keypair-generation/generate-keypair-using-ecdh-with-java#3-perform-key-agreement,
                // https://blog.naver.com/drods/80015158879
                // https://cryptobook.nakov.com/asymmetric-key-ciphers/ecdh-key-exchange,
                // https://docs.oracle.com/javase/8/docs/api/javax/crypto/KeyAgreement.html 참고)
                KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
                keyAgreement.init(eccPrivateKey);
                keyAgreement.doPhase(tempPublicKey, true);
                byte[] key = keyAgreement.generateSecret();

                // KDF 함수로 최종 비밀키 생성 (https://ride-wind.tistory.com/163,
                // https://docs.oracle.com/en/java/javase/24/docs/api/java.base/javax/crypto/spec/HKDFParameterSpec.html,
                // https://docs.oracle.com/en/java/javase/26/docs/api/java.base/javax/crypto/KDF.html 참고)
                byte[] salt = Base64.getDecoder().decode(FileLoad("salt/salt" + (i+1) + ".txt"));
                KDF kdf = KDF.getInstance("HKDF-SHA256");
                HKDFParameterSpec hkdfParameterSpec =
                        HKDFParameterSpec.ofExtract()
                                .addIKM(key)
                                .addSalt(salt)
                                .thenExpand(null,32);
                secretKey[i] = kdf.deriveKey("AES", hkdfParameterSpec);

                FileDelete("salt/salt" + (i+1) + ".txt");
                FileDelete("tempPublicKey/temppublic" + (i+1) + ".key");
            }
        } catch (Exception e) {
            for (int i=0; i<N; i++) {
                FileDelete("salt/salt" + (i+1) + ".txt");
                FileDelete("tempPublicKey/temppublic" + (i+1) + ".key");
            }
            throw new Exception("비밀키 생성 실패했습니다.\n", e);
        }

        return secretKey;
    }
    // (2) 봉투 암|복호화
    private static void AesWithEnvelope() throws Exception {
        int mode = SelectMode();
        if (mode == 0) return;

        if (mode == 1) { // 암호화
            int algorithm = SelectAlgorithm();
            if (algorithm == 1) { // RSA
                // Key 생성
                SecretKey key = CreateRandomKey();

                // IV 생성
                byte[] iv = CreateIV();
                GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);

                // 파일 속 평문 읽기
                String plainText = FileLoad("text/plaintext.txt");

                // AES-256-GCM 암호화
                byte[] cipherText;
                try{
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
                    cipherText = cipher.doFinal(plainText.getBytes());
                } catch (Exception e) { throw new Exception("암호화 실패했습니다.", e); }

                // 암호문 파일 속 저장
                FileSave("text/AesRsaCipherText.txt", Base64.getEncoder().encodeToString(cipherText));

                System.out.println("\n평문: " + plainText);
                System.out.println("IV: " + Base64.getEncoder().encodeToString(iv));
                System.out.println("\n암호화를 시작합니다...");
                System.out.println("암호문: " + Base64.getEncoder().encodeToString(cipherText));
                System.out.println("\nKey를 암호화하여 DEK를 생성합니다...");
                RsaEncryption(key.getEncoded());

            } else if (algorithm == 2) { // ECIES
                // 다중 수신자 N명 선택
                int N = SelectReceiver();

                // ECDH 및 KDF를 통해 AES 비밀키 생성
                SecretKey[] key = EccEncryption(N);

                for (int i=0; i<N; i++) {
                    // IV 생성
                    byte[] iv = CreateIV();
                    GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);

                    // 파일 속 평문 읽기
                    String plainText = FileLoad("text/plaintext.txt");

                    // AES-256-GCM 암호화
                    byte[] cipherText;
                    try{
                        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                        cipher.init(Cipher.ENCRYPT_MODE, key[i], gcmParameterSpec);
                        cipherText = cipher.doFinal(plainText.getBytes());
                    } catch (Exception e) { throw new Exception("암호화 실패했습니다.", e); }

                    // 암호문, IV 파일 속 저장
                    String fileName = "text/AesEciesCipherText" + (i+1) + ".txt";
                    FileSave(fileName, Base64.getEncoder().encodeToString(cipherText));

                    System.out.println("\n-------------" + (i+1) + "번째 수신자 암호화-------------");
                    System.out.println("평문: " + plainText);
                    System.out.println("IV: " + Base64.getEncoder().encodeToString(iv));
                    System.out.println("\n암호화를 시작합니다...");
                    System.out.println("암호문: " + Base64.getEncoder().encodeToString(cipherText));
                    System.out.println("------------------------------------------");
                }
            }
            
        } else if (mode == 2) { // 복호화

            int algorithm = SelectAlgorithm();
            byte[] keyByte = new byte[0];

            if (algorithm == 1) { // RSA
                keyByte = RsaDecryption();
                if (keyByte == null) return;

                SecretKey key = new SecretKeySpec(keyByte, "AES");

                // IV 생성
                byte[] iv = InputIV();
                GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);

                // 파일 속 암호문 읽기
                byte[] cipherText = Base64.getDecoder().decode(FileLoad("text/AesRsaCipherText.txt"));

                // AES-256-GCM 복호화
                byte[] plainText;
                try {
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
                    plainText = cipher.doFinal(cipherText);
                } catch (Exception e) {
                    FileDelete("text/AesRsaCipherText.txt");
                    throw new Exception("복호화 실패했습니다.\n", e);
                }
                FileDelete("text/AesRsaCipherText.txt");

                System.out.println("\n복호화를 시작합니다...");
                System.out.println("평문: " + new String(plainText));

            } else if (algorithm == 2) { // ECIES
                // 다중 수신자 N 확인
                int N = FileCount("salt/");

                // ECDH 및 KDF를 통해 AES 비밀키 생성
                SecretKey[] key = EccDecryption(N);

                for (int i=0; i<N; i++) {
                    // 파일 속 암호문 읽기 및 삭제
                    byte[] cipherText = Base64.getDecoder().decode(FileLoad("text/AesEciesCipherText" + (i+1) + ".txt"));
                    FileDelete("text/AesEciesCipherText" + (i+1) + ".txt");

                    System.out.println("\n-------------" + (i+1) + "번째 수신자 복호화-------------");
                    System.out.println("암호문: " + Base64.getEncoder().encodeToString(cipherText));

                    // IV 생성
                    byte[] iv = InputIV();
                    GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);

                    // AES-256-GCM 복호화
                    byte[] plainText;
                    try {
                        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                        cipher.init(Cipher.DECRYPT_MODE, key[i], gcmParameterSpec);
                        plainText = cipher.doFinal(cipherText);
                    } catch (Exception e) { throw new Exception("복호화 실패했습니다.\n", e); }

                    System.out.println("\n복호화를 시작합니다...");
                    System.out.println("평문: " + new String(plainText));
                    System.out.println("------------------------------------------");
                }
            }
        }
    }
}