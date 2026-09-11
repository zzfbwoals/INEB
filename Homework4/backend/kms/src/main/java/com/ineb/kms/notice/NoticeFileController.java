package com.ineb.kms.notice;

import com.ineb.kms.common.ApiResponse;
import com.ineb.kms.notice.dto.NoticeFileContent;
import com.ineb.kms.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 첨부파일 — 복호화 다운로드·삭제. 업로드는 공지 등록/수정(multipart)에 포함된다 */
@RestController
@RequestMapping("/api/files")
public class NoticeFileController {

    private final NoticeService noticeService;

    public NoticeFileController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /**
     * 복호화 다운로드. 평문을 응답에 쓴 직후 지워야 하므로 ResponseEntity&lt;byte[]&gt; 대신 응답 스트림에 직접 쓴다.
     * 파일명은 RFC 5987(filename*=UTF-8'') 로 내려 한글명이 깨지지 않는다.
     */
    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal,
                         HttpServletResponse response) throws IOException {
        NoticeFileContent file = noticeService.download(id, principal.loginId());
        byte[] data = file.data();
        try {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setContentLengthLong(data.length);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(file.originalName(), StandardCharsets.UTF_8).build().toString());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.getOutputStream().write(data);
            response.flushBuffer();
        } finally {
            Arrays.fill(data, (byte) 0);
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        noticeService.deleteFile(id, principal.loginId());
        return ApiResponse.ok(null, "첨부파일이 삭제되었습니다.");
    }
}
