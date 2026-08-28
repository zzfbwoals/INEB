package com.ineb.kms.key;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.common.KstTime;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.HistoryTrigger;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyMode;
import com.ineb.kms.domain.KeyPurpose;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.domain.UsageOperation;
import com.ineb.kms.domain.UsageResult;
import com.ineb.kms.domain.KeyUsageLog;
import com.ineb.kms.key.dto.HistoryItem;
import com.ineb.kms.key.dto.KeyCreateRequest;
import com.ineb.kms.key.dto.KeyDetail;
import com.ineb.kms.key.dto.KeySummary;
import com.ineb.kms.key.dto.KeyUpdateRequest;
import com.ineb.kms.key.dto.UsageItem;
import com.ineb.kms.key.dto.UsageResponse;
import com.ineb.kms.key.dto.UsageStats;
import com.ineb.kms.key.dto.VersionInfo;
import com.ineb.kms.repository.CryptoKeyRepository;
import com.ineb.kms.repository.KeyMaterialRepository;
import com.ineb.kms.repository.KeyStatusHistoryRepository;
import com.ineb.kms.repository.KeyUsageLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 키 목록·상세·등록·메타 수정. 상태 변경 연산은 KeyOperationService, 테스트는 KeyTestService 담당.
 */
@Service
public class KeyService {

    private static final Set<String> SORTABLE = Set.of("keyName", "algorithm", "nextRotationAt", "createdAt");
    private static final Duration STATS_WINDOW = Duration.ofDays(30);

    private final CryptoKeyRepository keyRepository;
    private final KeyMaterialRepository materialRepository;
    private final KeyUsageLogRepository usageLogRepository;
    private final KeyStatusHistoryRepository historyRepository;
    private final KeyMaterialFactory materialFactory;
    private final KeyStateMachine stateMachine;
    private final KeyIntegrityHasher hasher;
    private final KeyIntegrityGuard integrityGuard;
    private final AuditHook auditHook;

    public KeyService(CryptoKeyRepository keyRepository, KeyMaterialRepository materialRepository,
                      KeyUsageLogRepository usageLogRepository, KeyStatusHistoryRepository historyRepository,
                      KeyMaterialFactory materialFactory,
                      KeyStateMachine stateMachine, KeyIntegrityHasher hasher,
                      KeyIntegrityGuard integrityGuard, AuditHook auditHook) {
        this.keyRepository = keyRepository;
        this.materialRepository = materialRepository;
        this.usageLogRepository = usageLogRepository;
        this.historyRepository = historyRepository;
        this.materialFactory = materialFactory;
        this.stateMachine = stateMachine;
        this.hasher = hasher;
        this.integrityGuard = integrityGuard;
        this.auditHook = auditHook;
    }

    // ---------------------------------------------------------------- 목록

    /**
     * @param status "LIVE"(기본, DESTROYED 제외) · "ALL" · KeyState 이름
     */
    @Transactional(readOnly = true)
    public PageResponse<KeySummary> list(String keyword, KeyAlgorithm algorithm, String status, KeyPurpose purpose,
                                         int page, int size, String sort, String direction) {
        Specification<CryptoKey> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("keyName")), "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (algorithm != null) {
                ps.add(cb.equal(root.get("algorithm"), algorithm));
            }
            if (purpose != null) {
                ps.add(cb.equal(root.get("purpose"), purpose));
            }
            String st = status == null || status.isBlank() ? "LIVE" : status.trim().toUpperCase();
            if ("LIVE".equals(st)) {
                ps.add(cb.notEqual(root.get("status"), KeyState.DESTROYED));
            } else if (!"ALL".equals(st)) {
                ps.add(cb.equal(root.get("status"), parseState(st)));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<CryptoKey> result = keyRepository.findAll(spec, pageable(page, size, sort, direction));
        return PageResponse.of(result, this::toSummary);
    }

    private static KeyState parseState(String value) {
        try {
            return KeyState.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static Pageable pageable(int page, int size, String sort, String direction) {
        String field = sort != null && SORTABLE.contains(sort) ? sort : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        if (sort == null) {
            dir = Sort.Direction.DESC;
        }
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(dir, field));
    }

    // ---------------------------------------------------------------- 상세

    @Transactional(readOnly = true)
    public KeyDetail get(String keyUid) {
        return toDetail(load(keyUid));
    }

    CryptoKey load(String keyUid) {
        return keyRepository.findByKeyUid(keyUid).orElseThrow(() -> new BusinessException(ErrorCode.KEY_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 등록

    @Transactional
    public KeyDetail create(KeyCreateRequest req, String actor) {
        if (keyRepository.existsByKeyName(req.keyName())) {
            throw new BusinessException(ErrorCode.KEY_NAME_DUPLICATE);
        }
        KeyAlgorithm algorithm = req.algorithm();
        KeyMode mode = validateAlgorithmParams(algorithm, req.keySize(), req.mode());
        KeyPurpose purpose = resolvePurpose(algorithm, req.purpose());
        Integer period = validateRotation(req.autoRotateOrDefault(), req.rotationPeriodDays());

        Instant now = Instant.now();
        Instant activationDate = KstTime.parse(req.activationDate());
        boolean immediate = activationDate == null || !activationDate.isAfter(now);
        if (immediate) {
            activationDate = now;
        }

        CryptoKey key = new CryptoKey(req.keyName(), algorithm, req.keySize(), mode, purpose,
                req.autoRotateOrDefault(), period, blankToNull(req.description()));
        key.pointCurrent(1);
        keyRepository.save(key);

        KeyMaterialFactory.Generated g = materialFactory.generate(key);
        KeyMaterial v1 = new KeyMaterial(key, 1, immediate ? KeyState.ACTIVE : KeyState.PRE_ACTIVE,
                g.wrappedKey(), g.iv(), g.publicKey(), activationDate);
        materialRepository.save(v1);

        stateMachine.recordCreated(v1, HistoryTrigger.OPERATION,
                immediate ? "키 등록 (즉시 활성)" : "키 등록 (활성일 " + KstTime.format(activationDate) + " 지정)", actor);
        auditHook.record(actor, "KEY_CREATED", key.getKeyUid(),
                "algorithm=" + algorithm + ", size=" + req.keySize() + ", state=" + v1.getState());
        return toDetail(key);
    }

    // ---------------------------------------------------------------- 수정

    @Transactional
    public KeyDetail update(String keyUid, KeyUpdateRequest req, String actor) {
        CryptoKey key = load(keyUid);
        if (key.getStatus() == KeyState.DESTROYED) {
            throw new BusinessException(ErrorCode.KEY_STATE_CONFLICT);
        }
        if (keyRepository.existsByKeyNameAndIdNot(req.keyName(), key.getId())) {
            throw new BusinessException(ErrorCode.KEY_NAME_DUPLICATE);
        }
        Integer period = validateRotation(req.autoRotate(), req.rotationPeriodDays());

        key.rename(req.keyName(), blankToNull(req.description()));
        if (req.autoRotate() != key.isAutoRotate()
                || (req.autoRotate() && !period.equals(key.getRotationPeriodDays()))) {
            key.changeRotation(req.autoRotate(), period, Instant.now());
        }

        String activationNote = "";
        if (req.activationDate() != null && !req.activationDate().isBlank()) {
            KeyMaterial current = materialRepository.findByKeyIdAndVersion(key.getId(), key.getCurrentVersion())
                    .orElseThrow(() -> new BusinessException(ErrorCode.KEY_VERSION_NOT_FOUND));
            if (current.getState() != KeyState.PRE_ACTIVE) {
                throw new BusinessException(ErrorCode.KEY_ACTIVATION_DATE_NOT_EDITABLE);
            }
            Instant newDate = KstTime.parse(req.activationDate());
            Instant now = Instant.now();
            if (!newDate.isAfter(now)) {
                current.rescheduleActivation(now);
                stateMachine.transition(current, KeyState.ACTIVE, HistoryTrigger.OPERATION,
                        "활성일을 과거로 수정 — 즉시 활성", actor);
                activationNote = ", activated=true";
            } else {
                current.rescheduleActivation(newDate);
                hasher.rehash(current);
                activationNote = ", activationDate=" + KstTime.format(newDate);
            }
        }
        hasher.rehash(key);
        auditHook.record(actor, "KEY_UPDATED", key.getKeyUid(),
                "name=" + key.getKeyName() + ", autoRotate=" + key.isAutoRotate()
                        + ", period=" + key.getRotationPeriodDays() + activationNote);
        return toDetail(key);
    }

    // ---------------------------------------------------------------- 이력 · 사용 이력

    @Transactional(readOnly = true)
    public List<HistoryItem> history(String keyUid) {
        CryptoKey key = load(keyUid);
        return historyRepository.findByKeyIdOrderByChangedAtDescIdDesc(key.getId()).stream()
                .map(h -> new HistoryItem(h.getVersion(),
                        h.getFromState() == null ? null : h.getFromState().name(), h.getToState().name(),
                        h.getReason(), h.getTrigger().name(), h.getChangedBy(), KstTime.format(h.getChangedAt())))
                .toList();
    }

    @Transactional(readOnly = true)
    public UsageResponse usage(String keyUid, int page, int size) {
        CryptoKey key = load(keyUid);
        Page<KeyUsageLog> logs = usageLogRepository.findByKeyIdOrderByUsedAtDescIdDesc(key.getId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        int current = key.getCurrentVersion();
        return new UsageResponse(usageStats(key), PageResponse.of(logs, l -> new UsageItem(l.getVersion(),
                l.getOperation().name(), l.getResult().name(), l.getFailReason(), KstTime.format(l.getUsedAt()),
                l.getVersion() != current)));
    }

    // ---------------------------------------------------------------- 검증 헬퍼

    static KeyMode validateAlgorithmParams(KeyAlgorithm algorithm, Integer keySize, KeyMode mode) {
        if (keySize == null || !algorithm.supportsSize(keySize)) {
            throw new BusinessException(ErrorCode.KEY_INVALID_ALGORITHM_PARAM);
        }
        if (algorithm.requiresMode()) {
            if (!algorithm.supportsMode(mode)) {
                throw new BusinessException(ErrorCode.KEY_INVALID_ALGORITHM_PARAM);
            }
            return mode;
        }
        if (mode != null) {
            throw new BusinessException(ErrorCode.KEY_INVALID_ALGORITHM_PARAM);
        }
        return null;
    }

    static KeyPurpose resolvePurpose(KeyAlgorithm algorithm, KeyPurpose requested) {
        KeyPurpose expected = algorithm.defaultPurpose();
        if (requested != null && requested != expected) {
            throw new BusinessException(ErrorCode.KEY_INVALID_ALGORITHM_PARAM);
        }
        return expected;
    }

    static Integer validateRotation(boolean autoRotate, Integer days) {
        if (!autoRotate) {
            return null;
        }
        int d = days == null ? CryptoKey.ROTATION_DEFAULT_DAYS : days;
        if (d < CryptoKey.ROTATION_MIN_DAYS || d > CryptoKey.ROTATION_MAX_DAYS) {
            throw new BusinessException(ErrorCode.KEY_ROTATION_PERIOD_INVALID);
        }
        return d;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ---------------------------------------------------------------- DTO 변환

    KeySummary toSummary(CryptoKey key) {
        List<KeyMaterial> materials = materialRepository.findByKeyIdOrderByVersionDesc(key.getId());
        KeyMaterial scheduled = materials.stream()
                .filter(m -> m.getState() == KeyState.PRE_ACTIVE && m.getVersion() != key.getCurrentVersion())
                .findFirst().orElse(null);
        return new KeySummary(key.getKeyUid(), key.getKeyName(), key.getAlgorithm().name(), key.getKeySize(),
                key.getMode() == null ? null : key.getMode().name(), key.getPurpose().name(), key.getStatus().name(),
                key.getCurrentVersion(), materials.size(),
                scheduled == null ? null : scheduled.getVersion(),
                scheduled == null ? null : KstTime.format(scheduled.getActivationDate()),
                key.isAutoRotate(), key.getRotationPeriodDays(), KstTime.format(key.getNextRotationAt()),
                integrityGuard.isValid(key) && materials.stream().allMatch(integrityGuard::isValid));
    }

    KeyDetail toDetail(CryptoKey key) {
        List<KeyMaterial> materials = materialRepository.findByKeyIdOrderByVersionDesc(key.getId());
        boolean canEncPurpose = key.getPurpose().canEncrypt() || key.getPurpose().canSign();
        List<VersionInfo> versions = materials.stream().map(m -> {
            boolean active = m.getState() == KeyState.ACTIVE;
            boolean latest = m.getVersion() == key.getCurrentVersion();
            return new VersionInfo(m.getVersion(), m.getState().name(),
                    m.getDeactivationTrigger() == null ? null : m.getDeactivationTrigger().name(),
                    KstTime.format(m.getActivationDate()), KstTime.format(m.getDestroyedAt()),
                    usageLogRepository.findTopByKeyIdAndVersionOrderByUsedAtDesc(key.getId(), m.getVersion())
                            .map(l -> KstTime.format(l.getUsedAt())).orElse(null),
                    usageLogRepository.countByKeyIdAndVersion(key.getId(), m.getVersion()),
                    integrityGuard.isValid(m),
                    active && latest && canEncPurpose, active);
        }).toList();

        KeyMaterial current = materials.stream().filter(m -> m.getVersion() == key.getCurrentVersion())
                .findFirst().orElse(null);
        String pem = current == null || current.getPublicKey() == null ? null : toPem(current.getPublicKey());
        String hash = key.getIntegrityHash();
        String shortHash = hash == null ? null : hash.substring(0, 6) + "…" + hash.substring(hash.length() - 4);

        return new KeyDetail(key.getKeyUid(), key.getKeyName(), key.getAlgorithm().name(), key.getKeySize(),
                key.getMode() == null ? null : key.getMode().name(), key.getPurpose().name(), key.getStatus().name(),
                key.getCurrentVersion(), materials.size(), CryptoKey.MAX_VERSIONS,
                key.isAutoRotate(), key.getRotationPeriodDays(), KstTime.format(key.getNextRotationAt()),
                key.getDescription(), KstTime.format(key.getCreatedAt()), KeyMaterial.WRAP_ALGO, pem, shortHash,
                integrityGuard.isValid(key), versions, usageStats(key));
    }

    private UsageStats usageStats(CryptoKey key) {
        Instant since = Instant.now().minus(STATS_WINDOW);
        Long id = key.getId();
        return new UsageStats(
                usageLogRepository.countByKeyIdAndUsedAtAfter(id, since),
                usageLogRepository.countByKeyIdAndOperationAndUsedAtAfter(id, UsageOperation.ENCRYPT, since),
                usageLogRepository.countByKeyIdAndOperationAndUsedAtAfter(id, UsageOperation.DECRYPT, since),
                usageLogRepository.countByKeyIdAndOperationAndUsedAtAfter(id, UsageOperation.SIGN, since),
                usageLogRepository.countByKeyIdAndOperationAndUsedAtAfter(id, UsageOperation.VERIFY, since),
                usageLogRepository.countByKeyIdAndVersionNotAndUsedAtAfter(id, key.getCurrentVersion(), since),
                usageLogRepository.countByKeyIdAndResultAndUsedAtAfter(id, UsageResult.FAIL, since));
    }

    private static String toPem(String base64) {
        StringBuilder sb = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        return sb.append("-----END PUBLIC KEY-----").toString();
    }
}
