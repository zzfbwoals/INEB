package com.ineb.kms.dashboard.dto;

import java.util.List;
import java.util.Map;

/**
 * 대시보드 요약 — 한 번의 호출로 요약 카드 4종 + 보안 신호 + 연산 실패 + 알고리즘 분포를 돌려준다.
 * 조회 전용이며 감사 기록을 남기지 않는다. 시각은 KST "yyyy-MM-dd HH:mm:ss".
 */
public record DashboardSummary(
        Keys keys,
        Users users,
        Notices notices,
        Integrity integrity,
        List<Signal> signals,
        List<Failure> failures,
        List<AlgoCount> algorithms) {

    /**
     * @param byStatus       crypto_key.status 별 수 (PRE_ACTIVE · ACTIVE · DEACTIVATED · DESTROYED 순, 0 포함)
     * @param versions       key_material 전체 행 수
     * @param decryptOnly    ACTIVE 이지만 current_version 이 아닌 구 버전 수 (복호화·검증 전용)
     * @param scheduled      PRE_ACTIVE 버전 수 (예약 활성)
     * @param destroyPending DEACTIVATED 버전 수 (폐기 대기)
     */
    public record Keys(long total, Map<String, Long> byStatus, long versions, long decryptOnly,
                       long scheduled, long destroyPending) {
    }

    public record Users(long total, long active, long suspended, long joined30d) {
    }

    /** thisMonth 는 KST 이번 달 1일 00:00 이후 등록, files 는 notice_file 전체 수 */
    public record Notices(long total, long pinned, long thisMonth, long files) {
    }

    /** firstKeyUid/firstKeyName 은 메타 또는 버전 위반이 있는 첫 키 — 위반이 없으면 null */
    public record Integrity(long keyMeta, long keyVersion, long user, long auditChain, long total,
                            String firstKeyUid, String firstKeyName) {
    }

    /** level: warn(주의) · bad(위험). value 가 0 이어도 항목은 항상 4개 고정 순서로 내려간다 */
    public record Signal(String key, String label, long value, String sub, String level) {
    }

    public record Failure(String keyUid, String keyName, int version, String operation, String failReason,
                          String usedAt) {
    }

    public record AlgoCount(String algorithm, long count) {
    }
}
