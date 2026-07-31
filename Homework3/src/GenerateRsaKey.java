import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

public class GenerateRsaKey {
    public static void main(String[] args) throws Exception {
        for(int i=0; i<10; i++) {
            // 공개키|개인키 쌍 생성
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();

            // 키 저장
            String publicKeyName = "PublicKey/public" + (i+1) + ".key";
            String privateKeyName = "PrivateKey/private" + (i+1) + ".key";
            KeySave(publicKeyName, publicKey.getEncoded());
            KeySave(privateKeyName, privateKey.getEncoded());
        }
    }
    // .key 저장 메서드 (https://data-make.tistory.com/758#google_vignette 참고)
    private static void KeySave(String fileName, byte[] key) throws Exception {
        try (FileOutputStream fileOutputStream = new FileOutputStream(fileName)) {
            fileOutputStream.write(key);
        } catch (IOException e) { throw new IOException("파일을 저장하지 못했습니다.\n", e); }
    }
}
