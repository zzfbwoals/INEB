package com.ineb.kms.user;

import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.common.KstTime;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.crypto.PersonalDataCodec;
import com.ineb.kms.domain.AppUser;
import com.ineb.kms.domain.UserStatus;
import com.ineb.kms.repository.AppUserRepository;
import com.ineb.kms.user.dto.UserCreateRequest;
import com.ineb.kms.user.dto.UserPlainResponse;
import com.ineb.kms.user.dto.UserSummary;
import com.ineb.kms.user.dto.UserUpdateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 사용자 관리 — 개인정보는 마스터키 암호화 저장, 응답은 마스킹, 원문은 ADMIN 전용 API 로만.
 * 검색(2026-09-10 개정): 암호화 컬럼은 DB LIKE 가 불가능하므로, 검색어가 있으면 상태 조건만 DB 에 걸고
 * 해당 행을 전부 읽어 요청 처리 중에 복호화 → 이름·연락처·이메일 부분일치 판정 → 서버 측 페이징한다.
 * 평문은 응답 생성 후 버리며 어디에도 캐시하지 않는다. 단일 어드민·소규모 사용자 전제(대규모는 블라인드 인덱스로 전환).
 */
@Service
public class UserService {

    private final AppUserRepository repository;
    private final PersonalDataCodec codec;
    private final UserIntegrityHasher hasher;
    private final PasswordEncoder passwordEncoder;
    private final AuditHook auditHook;

    public UserService(AppUserRepository repository, PersonalDataCodec codec,
                       UserIntegrityHasher hasher, PasswordEncoder passwordEncoder, AuditHook auditHook) {
        this.repository = repository;
        this.codec = codec;
        this.hasher = hasher;
        this.passwordEncoder = passwordEncoder;
        this.auditHook = auditHook;
    }

    // ---------------------------------------------------------------- 목록 · 상세

    private static final java.util.Set<String> SORTABLE = java.util.Set.of("name", "createdAt");

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> list(String keyword, UserStatus status,
                                          int page, int size, String sort, String direction) {
        Specification<AppUser> spec = (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
        String field = sort != null && SORTABLE.contains(sort) ? sort : "createdAt";
        Sort.Direction dir = sort == null ? Sort.Direction.DESC
                : "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort order = Sort.by(dir, field).and(Sort.by(Sort.Direction.DESC, "id"));
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) {
            // 검색어 없음 — DB 페이징, 한 페이지만 복호화
            Page<AppUser> result = repository.findAll(spec, PageRequest.of(safePage, safeSize, order));
            return PageResponse.of(result, this::toSummary);
        }

        // 검색어 있음 — 상태 조건에 맞는 행 전부를 복호화해 부분일치 판정 후 서버 측 페이징
        List<UserSummary> matched = new ArrayList<>();
        for (AppUser user : repository.findAll(spec, order)) {
            String phone = safeDecrypt(user.getPhoneEnc());
            String email = safeDecrypt(user.getEmailEnc());
            if (matches(user.getName(), phone, email, q)) {
                matched.add(toSummary(user, phone, email));
            }
        }
        int from = Math.min(safePage * safeSize, matched.size());
        int to = Math.min(from + safeSize, matched.size());
        int totalPages = (int) Math.ceil(matched.size() / (double) safeSize);
        return new PageResponse<>(List.copyOf(matched.subList(from, to)), safePage, safeSize, matched.size(), totalPages);
    }

    /**
     * 통합 검색 판정 — 이름·이메일은 소문자 contains, 연락처는 검색어와 저장값 모두 숫자만 남겨 contains.
     * 복호화 실패(null) 필드는 판정에서 제외한다. 검색어에 숫자가 하나도 없으면 연락처 비교는 건너뛴다.
     */
    static boolean matches(String name, String phone, String email, String keyword) {
        String lower = keyword.toLowerCase(Locale.ROOT);
        if (name != null && name.toLowerCase(Locale.ROOT).contains(lower)) {
            return true;
        }
        if (email != null && PersonalDataCodec.normalizeEmail(email).contains(lower)) {
            return true;
        }
        String digits = PersonalDataCodec.normalizePhone(keyword);
        return !digits.isEmpty() && phone != null && PersonalDataCodec.normalizePhone(phone).contains(digits);
    }

    @Transactional(readOnly = true)
    public UserSummary get(Long id) {
        return toSummary(load(id));
    }

    // ---------------------------------------------------------------- 등록 · 수정

    @Transactional
    public UserSummary create(UserCreateRequest req, String actor) {
        validatePassword(req.password());
        String emailHash = codec.emailHash(req.email());
        if (repository.existsByEmailHash(emailHash)) {
            throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATE);
        }
        AppUser user = new AppUser(req.name().trim(), passwordEncoder.encode(req.password()),
                req.statusOrDefault(),
                codec.encrypt(req.phone()), codec.encrypt(req.email()), emailHash);
        hasher.rehash(user);
        repository.save(user);
        auditHook.record(actor, "USER_CREATED", AuditHook.userTarget(user.getId()),
                "name=" + user.getName() + ", status=" + user.getStatus());
        return toSummary(user);
    }

    @Transactional
    public UserSummary update(Long id, UserUpdateRequest req, String actor) {
        AppUser user = load(id);
        String emailHash = codec.emailHash(req.email());
        if (repository.existsByEmailHashAndIdNot(emailHash, user.getId())) {
            throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATE);
        }

        List<String> changed = new ArrayList<>();
        if (!user.getName().equals(req.name().trim())) {
            changed.add("name");
        }
        // 연락처 변경 감지 — 해시 컬럼이 없으므로 기존 암호문을 복호화해 정규화 비교(복호화 실패 행은 변경으로 본다)
        String currentPhone = safeDecrypt(user.getPhoneEnc());
        if (currentPhone == null
                || !PersonalDataCodec.normalizePhone(currentPhone).equals(PersonalDataCodec.normalizePhone(req.phone()))) {
            changed.add("phone");
        }
        if (!user.getEmailHash().equals(emailHash)) {
            changed.add("email");
        }
        if (user.getStatus() != req.status()) {
            changed.add("status:" + user.getStatus() + "→" + req.status());
        }

        user.rename(req.name().trim());
        user.changeStatus(req.status());
        // 변경 여부와 무관하게 새 IV 로 재암호화 — 코드가 단순하고 IV 재사용 여지가 없다
        user.applyPhone(codec.encrypt(req.phone()));
        user.applyEmail(codec.encrypt(req.email()), emailHash);
        if (req.password() != null && !req.password().isBlank()) {
            validatePassword(req.password());
            user.changePassword(passwordEncoder.encode(req.password()));
            changed.add("password");
        }
        hasher.rehash(user);
        auditHook.record(actor, "USER_UPDATED", AuditHook.userTarget(user.getId()),
                "fields=" + (changed.isEmpty() ? "none" : String.join(",", changed)));
        return toSummary(user);
    }

    // ---------------------------------------------------------------- 원문 조회 (ADMIN)

    /** 사유 필수 — 복호화 성공 여부와 무관하게 감사 통제 대상이므로 호출 즉시 기록한다 */
    @Transactional(noRollbackFor = BusinessException.class)
    public UserPlainResponse viewPlain(Long id, String reason, String actor) {
        AppUser user = load(id);
        auditHook.record(actor, "USER_PLAIN_VIEWED", AuditHook.userTarget(user.getId()),
                "reason=" + reason);
        return new UserPlainResponse(user.getId(), user.getName(),
                codec.decrypt(user.getPhoneEnc()), codec.decrypt(user.getEmailEnc()));
    }

    // ---------------------------------------------------------------- 헬퍼

    private AppUser load(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /** 8자 이상 + 특수문자(영숫자 외 문자) 1개 이상 — 등록·재설정 공용 */
    static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_POLICY);
        }
    }

    private static final String DECRYPT_FAILED = "(복호화 실패)";

    private UserSummary toSummary(AppUser user) {
        return toSummary(user, safeDecrypt(user.getPhoneEnc()), safeDecrypt(user.getEmailEnc()));
    }

    /** 검색 경로는 판정에 쓴 복호화 값을 그대로 넘겨 이중 복호화를 피한다 */
    private UserSummary toSummary(AppUser user, String phone, String email) {
        return new UserSummary(user.getId(), user.getName(),
                phone == null ? DECRYPT_FAILED : PrivacyMask.phone(phone),
                email == null ? DECRYPT_FAILED : PrivacyMask.email(email),
                user.getStatus().name(), user.getEncVer(), hasher.verify(user),
                KstTime.format(user.getCreatedAt()), KstTime.format(user.getUpdatedAt()));
    }

    /** 목록은 암호문 손상 행이 있어도 나머지를 보여줘야 한다 — 실패 시 표시 문자열로 대체 */
    private String safeDecrypt(String encoded) {
        try {
            return codec.decrypt(encoded);
        } catch (BusinessException e) {
            return null;
        }
    }
}
