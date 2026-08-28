package com.ineb.kms.key.dto;

/** 키 목록 행. 시각은 KST "yyyy-MM-dd HH:mm:ss". */
public record KeySummary(
        String keyUid,
        String keyName,
        String algorithm,
        int keySize,
        String mode,
        String purpose,
        String status,
        int currentVersion,
        int versionCount,
        Integer scheduledVersion,
        String scheduledAt,
        boolean autoRotate,
        Integer rotationPeriodDays,
        String nextRotationAt,
        boolean integrityValid) {
}
