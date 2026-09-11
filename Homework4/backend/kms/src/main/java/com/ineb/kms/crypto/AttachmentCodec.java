package com.ineb.kms.crypto;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 게시판 첨부파일 암호화 코덱 — 마스터키 AES-256-GCM, 파일마다 새 랜덤 IV.
 * <p>
 * 봉투는 개인정보(PersonalDataCodec)·키 래핑과 같은 base64(iv|ct+tag) 문자열이며 notice_file.enc_data(text)에 그대로 저장한다
 * (별도 iv 컬럼·파일시스템 없음). 평문이 문자열이 아닌 바이트 배열이라는 점만 다르다.
 * 평문 byte[] 의 zeroize 는 호출자 책임이다 (업로드는 서비스, 다운로드는 응답을 쓴 컨트롤러가 finally 에서 지운다).
 */
@Component
public class AttachmentCodec {

    private static final int IV_LEN = CryptoConstants.GCM_IV_LENGTH_BYTES;
    private static final int TAG_LEN = CryptoConstants.GCM_TAG_LENGTH_BITS / 8;

    private final MasterKeyHolder masterKeyHolder;
    private final SecureRandom random = new SecureRandom();

    public AttachmentCodec(MasterKeyHolder masterKeyHolder) {
        this.masterKeyHolder = masterKeyHolder;
    }

    public String encrypt(byte[] plain) {
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(iv);
        byte[] ct = AesGcmSupport.encrypt(masterKeyHolder.getKey(), iv, plain);
        byte[] blob = new byte[IV_LEN + ct.length];
        System.arraycopy(iv, 0, blob, 0, IV_LEN);
        System.arraycopy(ct, 0, blob, IV_LEN, ct.length);
        return Base64.getEncoder().encodeToString(blob);
    }

    /** Base64 오류·길이 부족·GCM 태그 불일치(변조·손상)는 모두 NOTICE_FILE_CORRUPTED(409) */
    public byte[] decrypt(String encoded) {
        try {
            byte[] blob = Base64.getDecoder().decode(encoded);
            if (blob.length < IV_LEN + TAG_LEN) {
                throw new IllegalArgumentException("암호문 길이 부족");
            }
            byte[] iv = Arrays.copyOfRange(blob, 0, IV_LEN);
            byte[] ct = Arrays.copyOfRange(blob, IV_LEN, blob.length);
            return AesGcmSupport.decrypt(masterKeyHolder.getKey(), iv, ct);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.NOTICE_FILE_CORRUPTED);
        }
    }
}
