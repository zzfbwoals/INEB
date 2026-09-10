package com.ineb.kms.dashboard.dto;

import java.util.List;

/**
 * 키 사용 추이 — 오늘(KST)을 포함한 최근 days 일, 빈 날은 0 으로 채워 오름차순.
 *
 * @param op ALL(전체) · ENC(ENCRYPT+DECRYPT) · SIGN(SIGN+VERIFY)
 */
public record UsageTrend(int days, String op, List<Point> points) {

    /** date 는 KST "yyyy-MM-dd" */
    public record Point(String date, long ok, long fail) {
    }
}
