package com.ineb.kms.notice.dto;

/** 복호화된 첨부 — 응답에 쓴 뒤 호출자가 data 를 zeroize 한다 */
public record NoticeFileContent(String originalName, byte[] data) {
}
