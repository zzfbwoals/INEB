package com.ineb.kms.dashboard.dto;

/**
 * 갱신 임박 · 예약 활성 항목.
 *
 * @param kind ROTATION(자동 갱신 키의 다음 갱신일 도래 예정, version = current) · ACTIVATION(PRE_ACTIVE 버전의 활성 예정일)
 * @param at   예정 시각 KST "yyyy-MM-dd HH:mm:ss"
 * @param dday KST 날짜 기준 남은 일수 (지났으면 음수)
 */
public record ExpiringItem(String keyUid, String keyName, String algorithm, int keySize, int version,
                           String kind, String at, long dday) {
}
