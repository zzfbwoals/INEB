package com.ineb.kms.notice;

import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.common.KstTime;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.crypto.AttachmentCodec;
import com.ineb.kms.domain.Notice;
import com.ineb.kms.domain.NoticeFile;
import com.ineb.kms.notice.dto.NoticeDetail;
import com.ineb.kms.notice.dto.NoticeFileContent;
import com.ineb.kms.notice.dto.NoticeFileItem;
import com.ineb.kms.notice.dto.NoticeFileMeta;
import com.ineb.kms.notice.dto.NoticeForm;
import com.ineb.kms.notice.dto.NoticeSearchScope;
import com.ineb.kms.notice.dto.NoticeSummary;
import com.ineb.kms.repository.NoticeFileRepository;
import com.ineb.kms.repository.NoticeRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공지사항 게시판 — 첨부파일은 마스터키 AES-256-GCM 으로 암호화해 DB(base64 봉투)에 저장하고 다운로드 시에만 복호화한다.
 * 중요(pinned) 공지는 어떤 정렬에서도 상단에 고정된다. 모든 쓰기·다운로드는 감사 로그(NOTICE_*, target NOTICE#{id})에 남긴다.
 */
@Service
public class NoticeService {

    public static final int MAX_FILES = 5;
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private static final Set<String> SORTABLE = Set.of("createdAt", "viewCount");
    private static final int DETAIL_TITLE_LEN = 50;

    private final NoticeRepository noticeRepository;
    private final NoticeFileRepository fileRepository;
    private final AttachmentCodec codec;
    private final AuditHook auditHook;

    public NoticeService(NoticeRepository noticeRepository, NoticeFileRepository fileRepository,
                         AttachmentCodec codec, AuditHook auditHook) {
        this.noticeRepository = noticeRepository;
        this.fileRepository = fileRepository;
        this.codec = codec;
        this.auditHook = auditHook;
    }

    // ---------------------------------------------------------------- 목록 · 상세

    @Transactional(readOnly = true)
    public PageResponse<NoticeSummary> list(String keyword, NoticeSearchScope scope, Boolean pinned,
                                            int page, int size, String sort, String direction) {
        NoticeSearchScope effectiveScope = scope == null ? NoticeSearchScope.TITLE_CONTENT : scope;
        Specification<Notice> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(switch (effectiveScope) {
                    case TITLE -> cb.like(cb.lower(root.get("title")), like);
                    case AUTHOR -> cb.like(cb.lower(root.get("authorName")), like);
                    case TITLE_CONTENT -> cb.or(cb.like(cb.lower(root.get("title")), like),
                            cb.like(cb.lower(root.get("content")), like));
                });
            }
            if (pinned != null) {
                ps.add(cb.equal(root.get("pinned"), pinned));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        String field = sort != null && SORTABLE.contains(sort) ? sort : "createdAt";
        Sort.Direction dir = sort == null ? Sort.Direction.DESC
                : "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        // 중요 공지는 정렬과 무관하게 상단 고정
        Page<Notice> result = noticeRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "pinned").and(Sort.by(dir, field))
                                .and(Sort.by(Sort.Direction.DESC, "id"))));
        Map<Long, Long> fileCounts = fileCounts(result.getContent().stream().map(Notice::getId).toList());
        return PageResponse.of(result, n -> new NoticeSummary(n.getId(), n.getTitle(), n.isPinned(),
                n.getAuthorName(), n.getViewCount(), fileCounts.getOrDefault(n.getId(), 0L),
                KstTime.format(n.getCreatedAt())));
    }

    /** 통합 검색 — 제목·작성자명 부분일치(대소문자 무시), 최신순 limit 건 (고정 우선 없음) */
    @Transactional(readOnly = true)
    public PageResponse<NoticeSummary> search(String q, int limit) {
        String like = "%" + q.trim().toLowerCase() + "%";
        Specification<Notice> spec = (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(root.get("authorName")), like));
        Page<Notice> result = noticeRepository.findAll(spec,
                PageRequest.of(0, Math.min(Math.max(limit, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))));
        Map<Long, Long> fileCounts = fileCounts(result.getContent().stream().map(Notice::getId).toList());
        return PageResponse.of(result, n -> new NoticeSummary(n.getId(), n.getTitle(), n.isPinned(),
                n.getAuthorName(), n.getViewCount(), fileCounts.getOrDefault(n.getId(), 0L),
                KstTime.format(n.getCreatedAt())));
    }

    /**
     * 상세. countView 가 true 면 조회수 +1 (설계: 상세 조회 시 증가). 실시간 재조회·수정 후 재조회는 false 로 호출해
     * 조회수가 부풀지 않게 한다. 조회 자체는 감사 대상이 아니다.
     */
    @Transactional
    public NoticeDetail get(Long id, boolean countView) {
        if (countView && noticeRepository.increaseViewCount(id) == 0) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
        }
        return toDetail(load(id));
    }

    // ---------------------------------------------------------------- 등록 · 수정 · 삭제

    @Transactional
    public NoticeDetail create(NoticeForm form, String actor, String authorName) {
        List<MultipartFile> files = form.filesOrEmpty();
        validateFiles(files, 0);
        Notice notice = noticeRepository.save(new Notice(form.title().trim(), form.content(),
                form.pinnedOrDefault(), actor, authorName));
        for (MultipartFile file : files) {
            storeEncrypted(notice, file);
        }
        auditHook.record(actor, "NOTICE_CREATED", AuditHook.noticeTarget(notice.getId()),
                "title=" + shortTitle(notice.getTitle()) + ", pinned=" + notice.isPinned() + ", files=" + files.size());
        return toDetail(notice);
    }

    /** 제목·본문·중요 여부 갱신 + 새 첨부 추가. 기존 첨부 삭제는 deleteFile 로 */
    @Transactional
    public NoticeDetail update(Long id, NoticeForm form, String actor) {
        Notice notice = load(id);
        List<MultipartFile> files = form.filesOrEmpty();
        validateFiles(files, fileRepository.countByNotice_Id(notice.getId()));

        List<String> changed = new ArrayList<>();
        String title = form.title().trim();
        if (!notice.getTitle().equals(title)) {
            changed.add("title");
        }
        if (!notice.getContent().equals(form.content())) {
            changed.add("content");
        }
        if (notice.isPinned() != form.pinnedOrDefault()) {
            changed.add("pinned:" + notice.isPinned() + "→" + form.pinnedOrDefault());
        }
        if (!files.isEmpty()) {
            changed.add("files+" + files.size());
        }
        notice.edit(title, form.content(), form.pinnedOrDefault());
        for (MultipartFile file : files) {
            storeEncrypted(notice, file);
        }
        auditHook.record(actor, "NOTICE_UPDATED", AuditHook.noticeTarget(notice.getId()),
                "fields=" + (changed.isEmpty() ? "none" : String.join(",", changed)));
        return toDetail(notice);
    }

    /** 첨부(암호문)를 먼저 지우고 공지를 지운다 — FK 에 cascade 가 없다 */
    @Transactional
    public void delete(Long id, String actor) {
        Notice notice = load(id);
        int files = fileRepository.deleteByNoticeId(notice.getId());
        noticeRepository.delete(notice);
        auditHook.record(actor, "NOTICE_DELETED", AuditHook.noticeTarget(notice.getId()),
                "title=" + shortTitle(notice.getTitle()) + ", files=" + files);
    }

    // ---------------------------------------------------------------- 첨부파일

    /** 복호화 다운로드 — 성공 여부와 무관하게 시도 자체가 통제 대상이므로 복호화 전에 기록한다 (개인정보 원문 조회와 같은 규칙) */
    @Transactional(noRollbackFor = BusinessException.class)
    public NoticeFileContent download(Long fileId, String actor) {
        NoticeFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_FILE_NOT_FOUND));
        auditHook.record(actor, "NOTICE_FILE_DOWNLOADED", AuditHook.noticeTarget(file.getNotice().getId()),
                "fileId=" + file.getId() + ", name=" + file.getOriginalName() + ", size=" + file.getFileSize());
        return new NoticeFileContent(file.getOriginalName(), codec.decrypt(file.getEncData()));
    }

    @Transactional
    public void deleteFile(Long fileId, String actor) {
        NoticeFileMeta meta = fileRepository.findMetaById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_FILE_NOT_FOUND));
        fileRepository.deleteByIdJpql(meta.id());
        auditHook.record(actor, "NOTICE_FILE_DELETED", AuditHook.noticeTarget(meta.noticeId()),
                "fileId=" + meta.id() + ", name=" + meta.originalName());
    }

    // ---------------------------------------------------------------- 헬퍼

    private Notice load(Long id) {
        return noticeRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
    }

    /** 개수 상한(기존 + 신규)·빈 파일·크기 상한. multipart 설정이 먼저 잡지만 서비스에서도 한 번 더 막는다 */
    static void validateFiles(List<MultipartFile> files, long existing) {
        if (existing + files.size() > MAX_FILES) {
            throw new BusinessException(ErrorCode.NOTICE_FILE_LIMIT);
        }
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new BusinessException(ErrorCode.NOTICE_FILE_EMPTY);
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.NOTICE_FILE_TOO_LARGE);
            }
        }
    }

    /** 평문을 읽어 암호화·저장하고 즉시 지운다. 원본명은 경로를 떼고 255자로 자른다 */
    private void storeEncrypted(Notice notice, MultipartFile file) {
        byte[] plain;
        try {
            plain = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("첨부파일을 읽지 못했습니다", e);
        }
        try {
            String encData = codec.encrypt(plain);
            fileRepository.save(new NoticeFile(notice, safeName(file.getOriginalFilename()),
                    file.getContentType(), plain.length, encData));
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    static String safeName(String originalFilename) {
        String name = StringUtils.getFilename(originalFilename == null ? "" : originalFilename.replace('\\', '/'));
        if (name == null || name.isBlank()) {
            name = "file";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static String shortTitle(String title) {
        return title.length() > DETAIL_TITLE_LEN ? title.substring(0, DETAIL_TITLE_LEN) + "…" : title;
    }

    private Map<Long, Long> fileCounts(List<Long> noticeIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (noticeIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : fileRepository.countByNoticeIds(noticeIds)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private NoticeDetail toDetail(Notice notice) {
        List<NoticeFileItem> files = fileRepository.findMetaByNoticeId(notice.getId()).stream()
                .map(m -> new NoticeFileItem(m.id(), m.originalName(), m.fileSize(), m.encVer(),
                        KstTime.format(m.createdAt())))
                .toList();
        return new NoticeDetail(notice.getId(), notice.getTitle(), notice.getContent(), notice.isPinned(),
                notice.getCreatedBy(), notice.getAuthorName(), notice.getViewCount(), files,
                KstTime.format(notice.getCreatedAt()), KstTime.format(notice.getUpdatedAt()));
    }
}
