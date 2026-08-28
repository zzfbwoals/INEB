package com.ineb.kms.key.dto;

import java.util.List;

/** 키 상세. 키 값(재료)은 절대 포함하지 않는다. 공개키는 비대칭키에만 PEM 으로 제공. */
public record KeyDetail(
        String keyUid,
        String keyName,
        String algorithm,
        int keySize,
        String mode,
        String purpose,
        String status,
        int currentVersion,
        int versionCount,
        int maxVersions,
        boolean autoRotate,
        Integer rotationPeriodDays,
        String nextRotationAt,
        String description,
        String createdAt,
        String wrapAlgo,
        String publicKeyPem,
        String integrityHashShort,
        boolean integrityValid,
        List<VersionInfo> versions,
        UsageStats usageStats) {
}
