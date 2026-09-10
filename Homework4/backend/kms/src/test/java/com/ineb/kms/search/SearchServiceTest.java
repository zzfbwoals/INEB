package com.ineb.kms.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchServiceTest {

    private KeyService keyService;
    private UserService userService;
    private NoticeService noticeService;
    private AuditLogService auditLogService;
    private SearchService service;

    @BeforeEach
    void setUp() {
        keyService = mock(KeyService.class);
        userService = mock(UserService.class);
        noticeService = mock(NoticeService.class);
        auditLogService = mock(AuditLogService.class);
        service = new SearchService(keyService, userService, noticeService, auditLogService);
    }

    private static KeySummary key(int i) {
        return new KeySummary("uid" + i, "KEY-" + i, "AES", 256, "GCM", "ENC_DEC", "ACTIVE", 1, "", 1, null, null,
                false, null, "", true);
    }

    private static <T> PageResponse<T> page(List<T> items, long total) {
        return new PageResponse<>(items, 0, items.size(), total, 1);
    }

    @Test
    @DisplayName("ALL — 네 항목을 각각 5건 상한으로 조회하고 counts 에 전체 일치 수를 담는다")
    void allSearchesEveryTypeWithLimit5() {
        when(keyService.search(eq("aes"), eq(5))).thenReturn(page(IntStream.range(0, 5).mapToObj(SearchServiceTest::key).toList(), 8));
        when(userService.list(eq("aes"), isNull(), eq(0), eq(5), isNull(), isNull()))
                .thenReturn(page(List.of(new UserSummary(1, "김", "010-****-****", "****@****.**", "ACTIVE", 1, true, "", "")), 1));
        when(noticeService.search(eq("aes"), eq(5))).thenReturn(page(List.of(), 0));
        when(auditLogService.search(eq("aes"), eq(5)))
                .thenReturn(page(List.of(new AuditLogItem(1, "admin", "KEY_TEST_ENCRYPT", "KEY#x", "version=1", true, "")), 13));

        SearchResponse r = service.search(" aes ", null);

        assertEquals(5, r.keys().size());
        assertEquals(8, r.counts().keys());
        assertEquals(1, r.users().size());
        assertEquals(0, r.notices().size());
        assertEquals(1, r.audits().size());
        assertEquals(13, r.counts().audits());
    }

    @Test
    @DisplayName("단일 type — 그 항목만 100건 상한으로 조회하고 나머지는 빈 배열")
    void singleTypeOnlyQueriesThatType() {
        when(noticeService.search(eq("점검"), eq(100))).thenReturn(page(List.of(new NoticeSummary(1, "점검", true, "류재민", 3, 0, "")), 1));

        SearchResponse r = service.search("점검", "notice");

        assertEquals(1, r.notices().size());
        assertTrue(r.keys().isEmpty() && r.users().isEmpty() && r.audits().isEmpty());
        assertEquals(1, r.counts().notices());
        assertEquals(0, r.counts().keys());
        verify(keyService, never()).search(anyString(), anyInt());
        verify(userService, never()).list(any(), any(), anyInt(), anyInt(), any(), any());
        verify(auditLogService, never()).search(anyString(), anyInt());
    }

    @Test
    @DisplayName("빈 검색어·알 수 없는 type 은 400 INVALID_INPUT")
    void invalidInput() {
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class, () -> service.search("   ", "ALL")).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class, () -> service.search(null, "ALL")).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class, () -> service.search("x", "FILE")).getErrorCode());
    }
}
