package com.ineb.kms.notice;

import com.ineb.kms.common.ApiResponse;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.notice.dto.NoticeDetail;
import com.ineb.kms.notice.dto.NoticeForm;
import com.ineb.kms.notice.dto.NoticeSearchScope;
import com.ineb.kms.notice.dto.NoticeSummary;
import com.ineb.kms.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** keyword 는 scope(제목+내용 / 제목 / 작성자) 부분검색, pinned 는 중요 여부 필터. 중요 공지는 항상 상단 */
    @GetMapping
    public ApiResponse<PageResponse<NoticeSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) NoticeSearchScope scope,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return ApiResponse.ok(noticeService.list(keyword, scope, pinned, page, size, sort, direction));
    }

    /** 상세 — 기본은 조회수 +1. 화면의 실시간 재조회는 countView=false */
    @GetMapping("/{id}")
    public ApiResponse<NoticeDetail> get(@PathVariable Long id,
                                         @RequestParam(defaultValue = "true") boolean countView) {
        return ApiResponse.ok(noticeService.get(id, countView));
    }

    /** multipart — 파트 title / content / pinned / files(반복). 첨부는 마스터키로 암호화해 저장 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<NoticeDetail> create(@Valid @ModelAttribute NoticeForm form,
                                            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(noticeService.create(form, principal.loginId(), principal.name()),
                "공지사항이 등록되었습니다.");
    }

    /** multipart — 제목·본문·중요 여부 갱신 + 새 첨부 추가. 기존 첨부 삭제는 DELETE /api/files/{id} */
    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<NoticeDetail> update(@PathVariable Long id,
                                            @Valid @ModelAttribute NoticeForm form,
                                            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(noticeService.update(id, form, principal.loginId()), "공지사항이 수정되었습니다.");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        noticeService.delete(id, principal.loginId());
        return ApiResponse.ok(null, "공지사항이 삭제되었습니다.");
    }
}
