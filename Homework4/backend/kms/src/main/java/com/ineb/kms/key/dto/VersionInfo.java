package com.ineb.kms.key.dto;

/** 키 상세의 버전 행. canEncrypt(서명 포함)는 최신 ACTIVE 버전만, canDecrypt(검증 포함)는 ACTIVE 버전 모두. */
public record VersionInfo(
        int version,
        String state,
        String deactivationTrigger,
        String activationDate,
        String destroyedAt,
        String lastUsedAt,
        long usageCount,
        boolean integrityValid,
        boolean canEncrypt,
        boolean canDecrypt) {
}
