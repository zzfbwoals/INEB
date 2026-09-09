package com.ineb.kms.audit;

import com.ineb.kms.audit.dto.AuditForensicsResponse;
import com.ineb.kms.audit.dto.AuditLogItem;
import com.ineb.kms.audit.dto.AuditVerifyResponse;
import com.ineb.kms.common.ApiResponse;
import com.ineb.kms.common.KstTime;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.security.AuthPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /** target 은 KEY#{keyUid} / USER#{id} / AUTH#{loginId} 형식의 정확 일치 */
    @GetMapping
    public ApiResponse<PageResponse<AuditLogItem>> list(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return ApiResponse.ok(auditLogService.list(actor, action, target, from, to, page, size, sort, direction));
    }

    /** CSV 내려받기 — 목록과 같은 필터. 엑셀 한글 호환을 위해 UTF-8 BOM 을 붙인다. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @AuthenticationPrincipal AuthPrincipal principal) {
        String csv = auditLogService.exportCsv(actor, action, target, from, to, principal.loginId());
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        String filename = "audit_log_" + LocalDate.now(KstTime.ZONE).format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(withBom);
    }

    /** 체인 상태 조회 — 원본 체인·섀도 비교 요약·보호 트리거 상태. 검증만 수행하고 감사 기록은 남기지 않는다 (화면 진입·SSE 시 자동 호출) */
    @GetMapping("/chain-status")
    public ApiResponse<AuditVerifyResponse> chainStatus() {
        return ApiResponse.ok(auditLogService.status());
    }

    /** 섀도 비교 상세 — 지워진·끼어든·바뀐 행의 원본 내용 (화면의 "위반 상세"). 읽기 전용 */
    @GetMapping("/forensics")
    public ApiResponse<AuditForensicsResponse> forensics() {
        return ApiResponse.ok(auditLogService.forensics());
    }

    /** 전체 해시 체인 재검증 — 검증 실행도 감사 기록(AUDIT_CHAIN_VERIFIED)되므로 POST */
    @PostMapping("/verify")
    public ApiResponse<AuditVerifyResponse> verify(@AuthenticationPrincipal AuthPrincipal principal) {
        AuditVerifyResponse result = auditLogService.verify(principal.loginId());
        return ApiResponse.ok(result, result.healthy()
                ? "해시 체인 검증을 통과했습니다."
                : "감사 로그 위반이 감지되었습니다.");
    }
}
