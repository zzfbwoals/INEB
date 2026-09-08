package com.ineb.kms.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 등록·수정 공용 multipart 폼 — 파트 이름 title / content / pinned / files(같은 이름 반복, 0~5개).
 * 첨부 개수·크기 정책은 서비스에서 검증한다 (수정은 기존 첨부 수와 합산).
 */
public record NoticeForm(
        @NotBlank(message = "제목을 입력해주세요.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,
        @NotBlank(message = "본문을 입력해주세요.")
        String content,
        Boolean pinned,
        List<MultipartFile> files) {

    public boolean pinnedOrDefault() {
        return pinned != null && pinned;
    }

    /** 파일 파트가 없으면 빈 목록. 브라우저가 빈 파일 파트를 보내는 경우(선택 없음)는 isEmpty 로 걸러 준다 */
    public List<MultipartFile> filesOrEmpty() {
        if (files == null) {
            return List.of();
        }
        return files.stream().filter(f -> f != null && !(f.isEmpty() && (f.getOriginalFilename() == null
                || f.getOriginalFilename().isBlank()))).toList();
    }
}
