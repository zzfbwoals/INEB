package com.ineb.kms.user;

/** 목록·상세 응답용 개인정보 마스킹. 원문은 ADMIN 의 원문 조회 API 로만 나간다. */
public final class PrivacyMask {

    private PrivacyMask() {
    }

    /** "010-1234-5678" → "010-****-5678". 하이픈 3분할 형식이 아니면 뒤 4자리만 남긴다. */
    public static String phone(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }
        String[] parts = plain.split("-");
        if (parts.length == 3) {
            return parts[0] + "-****-" + parts[2];
        }
        String digits = plain.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "****" + digits.substring(digits.length() - 4);
    }

    /** "user@ineb.co.kr" → "us****@ineb.co.kr" (로컬 2자 이하면 1자만 노출) */
    public static String email(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }
        int at = plain.indexOf('@');
        if (at <= 0) {
            return "****";
        }
        String local = plain.substring(0, at);
        String domain = plain.substring(at);
        int keep = local.length() > 2 ? 2 : 1;
        return local.substring(0, keep) + "****" + domain;
    }
}
