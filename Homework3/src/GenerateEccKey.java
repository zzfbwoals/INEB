import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.ECGenParameterSpec;

public class GenerateEccKey {
    public static void main(String[] args) throws Exception {
        // 키 쌍 생성
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        keyPairGenerator.initialize(ecSpec);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        keySave("eccKey/eccpublic.key", publicKey.getEncoded());
        keySave("eccKey/eccprivate.key", privateKey.getEncoded());
    }
    // .key 저장 메서드 (https://data-make.tistory.com/758#google_vignette 참고)
    private static void keySave(String fileName, byte[] key) throws Exception {
        try (FileOutputStream fileOutputStream = new FileOutputStream(fileName)) {
            fileOutputStream.write(key);
        } catch (IOException e) {
            throw new IOException("파일을 저장하지 못했습니다.\n", e);
        }
    }
}
