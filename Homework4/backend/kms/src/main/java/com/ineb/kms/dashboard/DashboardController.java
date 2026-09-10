package com.ineb.kms.dashboard;

import com.ineb.kms.common.ApiResponse;
import com.ineb.kms.dashboard.dto.DashboardSummary;
import com.ineb.kms.dashboard.dto.ExpiringItem;
import com.ineb.kms.dashboard.dto.UsageTrend;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 대시보드 집계 API — 조회 전용, 감사 기록 없음. 화면은 SSE 이벤트마다 재조회한다 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 요약 카드 4종 + 보안 신호(24h) + 연산 실패(30일) + 알고리즘 분포 */
    @GetMapping("/summary")
    public ApiResponse<DashboardSummary> summary() {
        return ApiResponse.ok(dashboardService.summary());
    }

    /** days 7|30, op ALL|ENC|SIGN — 그 외 400 */
    @GetMapping("/usage-trend")
    public ApiResponse<UsageTrend> usageTrend(@RequestParam(defaultValue = "30") int days,
                                              @RequestParam(defaultValue = "ALL") String op) {
        return ApiResponse.ok(dashboardService.usageTrend(days, op));
    }

    /** 자동 갱신 키의 다음 갱신일 + PRE_ACTIVE 버전의 활성 예정일이 days 일 이내인 항목 */
    @GetMapping("/expiring")
    public ApiResponse<List<ExpiringItem>> expiring(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(dashboardService.expiring(days));
    }
}
