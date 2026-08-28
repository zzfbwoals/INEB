package com.ineb.kms.common;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 시각 표기 규칙: 저장은 UTC(Instant), 응답·무결성 정규화·요청 파싱은 KST "yyyy-MM-dd HH:mm:ss".
 * 무결성 해시 정규화 문자열에도 이 포맷이 들어가므로 절대 바꾸지 않는다.
 */
public final class KstTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private KstTime() {
    }

    /** null 은 빈 문자열 (정규화 규칙: null → "") */
    public static String format(Instant instant) {
        if (instant == null) {
            return "";
        }
        return FORMAT.format(instant.atZone(ZONE));
    }

    /**
     * "yyyy-MM-dd HH:mm:ss" 또는 "yyyy-MM-dd HH:mm"(초 생략) 을 KST 로 해석해 Instant 로 변환한다.
     * 빈 값은 null. 형식 오류는 INVALID_INPUT.
     */
    public static Instant parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim().replace('T', ' ');
        if (value.length() == 16) {
            value = value + ":00";
        }
        try {
            return LocalDateTime.parse(value, FORMAT).atZone(ZONE).toInstant();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
