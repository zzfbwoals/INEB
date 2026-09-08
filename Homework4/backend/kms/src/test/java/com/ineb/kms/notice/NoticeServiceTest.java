package com.ineb.kms.notice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.crypto.AttachmentCodec;
import com.ineb.kms.crypto.MasterKeyHolder;
import com.ineb.kms.domain.Notice;
import com.ineb.kms.domain.NoticeFile;
import com.ineb.kms.notice.dto.NoticeDetail;
import com.ineb.kms.notice.dto.NoticeFileContent;
import com.ineb.kms.notice.dto.NoticeFileMeta;
import com.ineb.kms.notice.dto.NoticeForm;
import com.ineb.kms.notice.dto.NoticeSearchScope;
import com.ineb.kms.notice.dto.NoticeSummary;
import com.ineb.kms.repository.NoticeFileRepository;
import com.ineb.kms.repository.NoticeRepository;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class NoticeServiceTest {

    private static final String ACTOR = "admin";

    private final List<String> audits = new ArrayList<>();
    private final List<NoticeFile> savedFiles = new ArrayList<>();
    private NoticeRepository noticeRepository;
    private NoticeFileRepository fileRepository;
    private AttachmentCodec codec;
    private NoticeService service;

    @BeforeEach
    void setUp() {
        MasterKeyHolder holder = mock(MasterKeyHolder.class);
        when(holder.getKey()).thenReturn(new byte[32]);
        codec = new AttachmentCodec(holder);

        noticeRepository = mock(NoticeRepository.class);
        when(noticeRepository.save(any(Notice.class))).thenAnswer(inv -> {
            Notice n = inv.getArgument(0);
            setId(n, 1L);
            return n;
        });
        fileRepository = mock(NoticeFileRepository.class);
        AtomicLong fileSeq = new AtomicLong();
        when(fileRepository.save(any(NoticeFile.class))).thenAnswer(inv -> {
            NoticeFile f = inv.getArgument(0);
            setId(f, fileSeq.incrementAndGet());
            savedFiles.add(f);
            return f;
        });
        when(fileRepository.findMetaByNoticeId(anyLong())).thenReturn(List.of());
        when(fileRepository.countByNoticeIds(any())).thenReturn(List.of());

        AuditHook audit = (actor, action, target, detail) -> audits.add(action + ":" + target + ":" + detail);
        service = new NoticeService(noticeRepository, fileRepository, codec, audit);
    }

    private static void setId(Object entity, long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MultipartFile file(String name, String content) {
        return new MockMultipartFile("files", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private static List<MultipartFile> files(int n) {
        List<MultipartFile> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(file("f" + i + ".txt", "content-" + i));
        }
        return list;
    }

    private Notice existing(boolean pinned) {
        Notice n = new Notice("기존 제목", "기존 본문", pinned, ACTOR, "관리자");
        setId(n, 1L);
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(n));
        return n;
    }

    // ---------------------------------------------------------------- 등록

    @Test
    @DisplayName("등록하면 첨부마다 다른 IV 로 암호화돼 저장되고 NOTICE_CREATED 감사가 남는다")
    void createEncryptsFiles() {
        NoticeDetail detail = service.create(new NoticeForm("점검 안내", "본문", true,
                List.of(file("a.pdf", "AAAA"), file("b.xlsx", "BBBB"))), ACTOR, "관리자");

        assertEquals(1L, detail.id());
        assertTrue(detail.pinned());
        assertEquals(2, savedFiles.size());
        NoticeFile a = savedFiles.get(0);
        assertEquals("a.pdf", a.getOriginalName());
        assertEquals(4, a.getFileSize());
        assertEquals(NoticeFile.ENC_VER_CURRENT, a.getEncVer());
        assertNotEquals("AAAA", a.getEncData());
        assertEquals(12 + 4 + 16, Base64.getDecoder().decode(a.getEncData()).length);
        assertArrayEquals("AAAA".getBytes(StandardCharsets.UTF_8), codec.decrypt(a.getEncData()));
        assertNotEquals(a.getEncData().substring(0, 16), savedFiles.get(1).getEncData().substring(0, 16));
        assertEquals("NOTICE_CREATED:NOTICE#1:title=점검 안내, pinned=true, files=2", audits.getFirst());
    }

    @Test
    @DisplayName("첨부가 5개를 넘으면 NOTICE_FILE_LIMIT 이며 아무것도 저장·기록되지 않는다")
    void createTooManyFiles() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.create(new NoticeForm("t", "c", null, files(6)), ACTOR, "관리자"));
        assertEquals(ErrorCode.NOTICE_FILE_LIMIT, e.getErrorCode());
        verify(noticeRepository, never()).save(any());
        assertTrue(audits.isEmpty());
    }

    @Test
    @DisplayName("빈 파일은 NOTICE_FILE_EMPTY, 20MB 초과는 NOTICE_FILE_TOO_LARGE")
    void createRejectsEmptyAndOversized() {
        MultipartFile empty = new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0]);
        assertEquals(ErrorCode.NOTICE_FILE_EMPTY, assertThrows(BusinessException.class,
                () -> service.create(new NoticeForm("t", "c", null, List.of(empty)), ACTOR, "관리자")).getErrorCode());

        MultipartFile big = mock(MultipartFile.class);
        when(big.isEmpty()).thenReturn(false);
        when(big.getSize()).thenReturn(NoticeService.MAX_FILE_SIZE + 1);
        assertEquals(ErrorCode.NOTICE_FILE_TOO_LARGE, assertThrows(BusinessException.class,
                () -> service.create(new NoticeForm("t", "c", null, List.of(big)), ACTOR, "관리자")).getErrorCode());
    }

    @Test
    @DisplayName("원본 파일명은 경로를 떼고 저장한다 (경로 탐색 방지)")
    void safeNameStripsPath() {
        assertEquals("a.pdf", NoticeService.safeName("C:\\Users\\me\\a.pdf"));
        assertEquals("a.pdf", NoticeService.safeName("../../a.pdf"));
        assertEquals("file", NoticeService.safeName(null));
    }

    // ---------------------------------------------------------------- 수정

    @Test
    @DisplayName("수정 시 기존 첨부 수와 합산해 5개를 넘으면 NOTICE_FILE_LIMIT")
    void updateCountsExistingFiles() {
        existing(false);
        when(fileRepository.countByNotice_Id(1L)).thenReturn(4L);
        assertEquals(ErrorCode.NOTICE_FILE_LIMIT, assertThrows(BusinessException.class,
                () -> service.update(1L, new NoticeForm("t", "c", null, files(2)), ACTOR)).getErrorCode());
        assertTrue(savedFiles.isEmpty());
    }

    @Test
    @DisplayName("수정하면 변경 필드와 추가된 첨부 수가 NOTICE_UPDATED 에 남는다")
    void updateRecordsChangedFields() {
        Notice n = existing(false);
        when(fileRepository.countByNotice_Id(1L)).thenReturn(3L);
        NoticeDetail detail = service.update(1L, new NoticeForm("새 제목", "기존 본문", true, files(2)), ACTOR);

        assertEquals("새 제목", n.getTitle());
        assertTrue(n.isPinned());
        assertEquals(2, savedFiles.size());
        assertEquals("새 제목", detail.title());
        assertEquals("NOTICE_UPDATED:NOTICE#1:fields=title,pinned:false→true,files+2", audits.getFirst());
    }

    @Test
    @DisplayName("바뀐 것이 없으면 fields=none 으로 기록된다")
    void updateNoChange() {
        existing(true);
        when(fileRepository.countByNotice_Id(1L)).thenReturn(0L);
        service.update(1L, new NoticeForm("기존 제목", "기존 본문", true, null), ACTOR);
        assertEquals("NOTICE_UPDATED:NOTICE#1:fields=none", audits.getFirst());
    }

    // ---------------------------------------------------------------- 상세

    @Test
    @DisplayName("상세는 조회수를 먼저 올리고, 없는 공지면 NOTICE_NOT_FOUND. countView=false 면 올리지 않는다")
    void getCountsView() {
        when(noticeRepository.increaseViewCount(9L)).thenReturn(0);
        assertEquals(ErrorCode.NOTICE_NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.get(9L, true)).getErrorCode());

        existing(false);
        when(noticeRepository.increaseViewCount(1L)).thenReturn(1);
        when(fileRepository.findMetaByNoticeId(1L)).thenReturn(List.of(
                new NoticeFileMeta(7L, 1L, "a.pdf", "application/pdf", 412L, 1, Instant.now())));
        NoticeDetail detail = service.get(1L, true);
        assertEquals(1, detail.files().size());
        assertEquals("a.pdf", detail.files().getFirst().originalName());
        assertEquals(412L, detail.files().getFirst().fileSize());
        verify(noticeRepository).increaseViewCount(1L);

        service.get(1L, false);
        verify(noticeRepository).increaseViewCount(1L); // 여전히 1회
        assertTrue(audits.isEmpty());
    }

    // ---------------------------------------------------------------- 다운로드

    @Test
    @DisplayName("다운로드는 복호화 전에 NOTICE_FILE_DOWNLOADED 를 남기고 평문을 돌려준다")
    void downloadDecrypts() {
        Notice n = existing(false);
        NoticeFile f = new NoticeFile(n, "a.pdf", "application/pdf", 4,
                codec.encrypt("AAAA".getBytes(StandardCharsets.UTF_8)));
        setId(f, 7L);
        when(fileRepository.findById(7L)).thenReturn(Optional.of(f));

        NoticeFileContent content = service.download(7L, ACTOR);
        assertEquals("a.pdf", content.originalName());
        assertArrayEquals("AAAA".getBytes(StandardCharsets.UTF_8), content.data());
        assertEquals("NOTICE_FILE_DOWNLOADED:NOTICE#1:fileId=7, name=a.pdf, size=4", audits.getFirst());
    }

    @Test
    @DisplayName("암호문이 손상돼 복호화가 실패해도(409) 다운로드 시도는 감사에 남는다")
    void downloadCorruptedStillAudited() {
        Notice n = existing(false);
        NoticeFile f = new NoticeFile(n, "a.pdf", null, 4, Base64.getEncoder().encodeToString(new byte[40]));
        setId(f, 7L);
        when(fileRepository.findById(7L)).thenReturn(Optional.of(f));

        assertEquals(ErrorCode.NOTICE_FILE_CORRUPTED,
                assertThrows(BusinessException.class, () -> service.download(7L, ACTOR)).getErrorCode());
        assertTrue(audits.getFirst().startsWith("NOTICE_FILE_DOWNLOADED:NOTICE#1:"));
    }

    // ---------------------------------------------------------------- 삭제

    @Test
    @DisplayName("공지 삭제는 첨부(암호문)를 먼저 지운 뒤 공지를 지우고 삭제된 첨부 수를 기록한다")
    void deleteRemovesFilesFirst() {
        Notice n = existing(false);
        when(fileRepository.deleteByNoticeId(1L)).thenReturn(2);
        service.delete(1L, ACTOR);

        InOrder order = inOrder(fileRepository, noticeRepository);
        order.verify(fileRepository).deleteByNoticeId(1L);
        order.verify(noticeRepository).delete(n);
        assertEquals("NOTICE_DELETED:NOTICE#1:title=기존 제목, files=2", audits.getFirst());
    }

    @Test
    @DisplayName("첨부 삭제는 암호문을 읽지 않고(메타 프로젝션) 소속 공지를 target 으로 기록한다")
    void deleteFileUsesNoticeTarget() {
        when(fileRepository.findMetaById(7L)).thenReturn(Optional.of(
                new NoticeFileMeta(7L, 3L, "b.xlsx", null, 88L, 1, Instant.now())));
        service.deleteFile(7L, ACTOR);
        verify(fileRepository).deleteByIdJpql(7L);
        verify(fileRepository, never()).findById(any());
        assertEquals("NOTICE_FILE_DELETED:NOTICE#3:fileId=7, name=b.xlsx", audits.getFirst());

        when(fileRepository.findMetaById(8L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.NOTICE_FILE_NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.deleteFile(8L, ACTOR)).getErrorCode());
    }

    // ---------------------------------------------------------------- 목록

    @Test
    @DisplayName("목록은 중요 공지가 먼저 오도록 pinned DESC 를 최우선 정렬로 두고, 허용되지 않은 정렬은 createdAt 으로 대체한다")
    @SuppressWarnings("unchecked")
    void listPinnedFirst() {
        Notice pinned = new Notice("중요", "c", true, ACTOR, "관리자");
        setId(pinned, 2L);
        Notice normal = new Notice("일반", "c", false, ACTOR, "관리자");
        setId(normal, 1L);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(noticeRepository.findAll(any(Specification.class), pageable.capture()))
                .thenReturn(new PageImpl<>(List.of(pinned, normal)));
        List<Object[]> counts = Collections.singletonList(new Object[]{2L, 3L});
        when(fileRepository.countByNoticeIds(any())).thenReturn(counts);

        PageResponse<NoticeSummary> page = service.list("중", NoticeSearchScope.TITLE, null, 0, 20, "hack", "asc");

        List<Sort.Order> orders = new ArrayList<>();
        pageable.getValue().getSort().forEach(orders::add);
        assertEquals("pinned", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
        assertEquals("createdAt", orders.get(1).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(1).getDirection());
        assertEquals(2, page.content().size());
        assertEquals(3L, page.content().getFirst().fileCount());
        assertEquals(0L, page.content().get(1).fileCount());
        assertFalse(page.content().get(1).pinned());
    }
}
