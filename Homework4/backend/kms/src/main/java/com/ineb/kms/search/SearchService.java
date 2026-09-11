package com.ineb.kms.search;

import com.ineb.kms.audit.AuditLogService;
import com.ineb.kms.audit.dto.AuditLogItem;
import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.key.KeyService;
import com.ineb.kms.key.dto.KeySummary;
import com.ineb.kms.notice.NoticeService;
import com.ineb.kms.notice.dto.NoticeSummary;
import com.ineb.kms.search.dto.SearchResponse;
import com.ineb.kms.user.UserService;
import com.ineb.kms.user.dto.UserSummary;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 검색 — 키·사용자·공지·감사 로그를 각 서비스의 검색 경로로 조회해 합친다 (조회 전용, 감사 기록 없음).
 * 사용자는 UserService.list 의 서버 복호화 부분검색을, 감사 로그는 최신 500건을 복호화해 detail 까지 비교한다.
 */
@Service
public class SearchService {

    public enum Type { ALL, KEY, USER, NOTICE, AUDIT }

    static final int ALL_LIMIT = 5;
    static final int TYPE_LIMIT = 100;

    private final KeyService keyService;
    private final UserService userService;
    private final NoticeService noticeService;
    private final AuditLogService auditLogService;

    public SearchService(KeyService keyService, UserService userService, NoticeService noticeService,
                         AuditLogService auditLogService) {
        this.keyService = keyService;
        this.userService = userService;
        this.noticeService = noticeService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String q, String type) {
        if (q == null || q.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Type t = parse(type);
        int limit = t == Type.ALL ? ALL_LIMIT : TYPE_LIMIT;
        String keyword = q.trim();
        PageResponse<KeySummary> keys = t == Type.ALL || t == Type.KEY ? keyService.search(keyword, limit) : empty(limit);
        PageResponse<UserSummary> users = t == Type.ALL || t == Type.USER
                ? userService.list(keyword, null, 0, limit, null, null) : empty(limit);
        PageResponse<NoticeSummary> notices = t == Type.ALL || t == Type.NOTICE ? noticeService.search(keyword, limit) : empty(limit);
        PageResponse<AuditLogItem> audits = t == Type.ALL || t == Type.AUDIT ? auditLogService.search(keyword, limit) : empty(limit);
        return new SearchResponse(keys.content(), users.content(), notices.content(), audits.content(),
                new SearchResponse.Counts(keys.totalElements(), users.totalElements(),
                        notices.totalElements(), audits.totalElements()));
    }

    private static Type parse(String type) {
        if (type == null || type.isBlank()) {
            return Type.ALL;
        }
        try {
            return Type.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static <T> PageResponse<T> empty(int limit) {
        return new PageResponse<>(List.of(), 0, limit, 0, 0);
    }
}
