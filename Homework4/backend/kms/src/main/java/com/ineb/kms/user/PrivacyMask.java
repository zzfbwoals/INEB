package com.ineb.kms.user;

/** 목록·상세 응답용 개인정보 마스킹. 원문은 ADMIN 의 원문 조회 API 로만 나간다. */
public final class PrivacyMask {

    private PrivacyMask() {
    }

    /** 앞 3자리(010)만 남기고 전부 마스킹, 하이픈 등 구분자는 유지: "010-1234-5678" → "010-****-****" */
    public static String phone(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(plain.length());
        int digitIndex = 0;
        for (char c : plain.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(digitIndex < 3 ? c : '*');
                digitIndex++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** '@' 와 '.' 만 남기고 전부 마스킹: "user@ineb.co.kr" → "****@****.**.**" */
    public static String email(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(plain.length());
        for (char c : plain.toCharArray()) {
            sb.append(c == '@' || c == '.' ? c : '*');
        }
        return sb.toString();
    }
}
