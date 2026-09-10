package com.ineb.kms.dashboard;

import com.ineb.kms.audit.AuditLogService;
import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.common.KstTime;
import com.ineb.kms.dashboard.dto.DashboardSummary;
import com.ineb.kms.dashboard.dto.DashboardSummary.AlgoCount;
import com.ineb.kms.dashboard.dto.DashboardSummary.Failure;
import com.ineb.kms.dashboard.dto.DashboardSummary.Integrity;
import com.ineb.kms.dashboard.dto.DashboardSummary.Keys;
import com.ineb.kms.dashboard.dto.DashboardSummary.Notices;
import com.ineb.kms.dashboard.dto.DashboardSummary.Signal;
import com.ineb.kms.dashboard.dto.DashboardSummary.Users;
import com.ineb.kms.dashboard.dto.ExpiringItem;
import com.ineb.kms.dashboard.dto.UsageTrend;
import com.ineb.kms.domain.AppUser;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.domain.KeyUsageLog;
import com.ineb.kms.domain.UsageOperation;
import com.ineb.kms.domain.UsageResult;
import com.ineb.kms.domain.UserStatus;
import com.ineb.kms.key.KeyIntegrityHasher;
import com.ineb.kms.repository.AppUserRepository;
import com.ineb.kms.repository.AuditLogRepository;
import com.ineb.kms.repository.CryptoKeyRepository;
import com.ineb.kms.repository.KeyMaterialRepository;
import com.ineb.kms.repository.KeyUsageLogRepository;
import com.ineb.kms.repository.NoticeFileRepository;
import com.ineb.kms.repository.NoticeRepository;
import com.ineb.kms.user.UserIntegrityHasher;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 집계 — 조회 전용(감사 기록 없음). 무결성 검증은 해셔로 "검증만" 하고 자동 정지 같은 부수효과는 일으키지 않는다
 * (정지는 KeyIntegrityGuard·스케줄러 경로가 담당).
 */
@Service
public class DashboardService {

    static final int FAILURE_DAYS = 30;
    static final int FAILURE_LIMIT = 20;
    static final int SIGNAL_HOURS = 24;

    private final CryptoKeyRepository keyRepository;
    private final KeyMaterialRepository materialRepository;
    private final KeyUsageLogRepository usageLogRepository;
    private final AppUserRepository userRepository;
    private final NoticeRepository noticeRepository;
    private final NoticeFileRepository fileRepository;
    private final AuditLogRepository auditLogRepository;
    private final KeyIntegrityHasher keyHasher;
    private final UserIntegrityHasher userHasher;
    private final AuditLogService auditLogService;

    public DashboardService(CryptoKeyRepository keyRepository, KeyMaterialRepository materialRepository,
                            KeyUsageLogRepository usageLogRepository, AppUserRepository userRepository,
                            NoticeRepository noticeRepository, NoticeFileRepository fileRepository,
                            AuditLogRepository auditLogRepository, KeyIntegrityHasher keyHasher,
                            UserIntegrityHasher userHasher, AuditLogService auditLogService) {
        this.keyRepository = keyRepository;
        this.materialRepository = materialRepository;
        this.usageLogRepository = usageLogRepository;
        this.userRepository = userRepository;
        this.noticeRepository = noticeRepository;
        this.fileRepository = fileRepository;
        this.auditLogRepository = auditLogRepository;
        this.keyHasher = keyHasher;
        this.userHasher = userHasher;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------------- 요약

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        Instant now = Instant.now();
        return new DashboardSummary(keys(), users(now), notices(), integrity(), signals(now), failures(now), algorithms());
    }

    private Keys keys() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (KeyState state : KeyState.values()) {
            byStatus.put(state.name(), keyRepository.countByStatus(state));
        }
        return new Keys(keyRepository.count(), byStatus, materialRepository.count(),
                materialRepository.countByStateAndVersionNotCurrent(KeyState.ACTIVE),
                materialRepository.countByState(KeyState.PRE_ACTIVE),
                materialRepository.countByState(KeyState.DEACTIVATED));
    }

    private Users users(Instant now) {
        return new Users(userRepository.count(), userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.SUSPENDED),
                userRepository.countByCreatedAtGreaterThanEqual(now.minus(30, ChronoUnit.DAYS)));
    }

    private Notices notices() {
        Instant monthStart = LocalDate.now(KstTime.ZONE).withDayOfMonth(1).atStartOfDay(KstTime.ZONE).toInstant();
        return new Notices(noticeRepository.count(), noticeRepository.countByPinnedTrue(),
                noticeRepository.countByCreatedAtGreaterThanEqual(monthStart), fileRepository.count());
    }

    /** 키 메타·버전·사용자·감사 체인 위반 수 — 해시 재검증만 수행, 상태 변경 없음 */
    private Integrity integrity() {
        long keyMeta = 0;
        CryptoKey first = null;
        for (CryptoKey key : keyRepository.findAll()) {
            if (!keyHasher.verify(key)) {
                keyMeta++;
                if (first == null) {
                    first = key;
                }
            }
        }
        long keyVersion = 0;
        for (KeyMaterial material : materialRepository.findByStateNot(KeyState.DESTROYED)) {
            if (!keyHasher.verify(material)) {
                keyVersion++;
                if (first == null) {
                    first = material.getKey();
                }
            }
        }
        long user = 0;
        for (AppUser appUser : userRepository.findAll()) {
            if (!userHasher.verify(appUser)) {
                user++;
            }
        }
        long auditChain = auditLogService.status().violations().size();
        return new Integrity(keyMeta, keyVersion, user, auditChain, keyMeta + keyVersion + user + auditChain,
                first == null ? null : first.getKeyUid(), first == null ? null : first.getKeyName());
    }

    /** 최근 24시간 감사 기록 기반 보안 신호 4종 — 항상 같은 순서로 4개 */
    private List<Signal> signals(Instant now) {
        Map<String, Long> byAction = new LinkedHashMap<>();
        Map<String, Long> loginFailedByTarget = new LinkedHashMap<>();
        for (Object[] row : auditLogRepository.findActionTargetSince(now.minus(SIGNAL_HOURS, ChronoUnit.HOURS))) {
            String action = (String) row[0];
            String target = (String) row[1];
            byAction.merge(action, 1L, Long::sum);
            if ("LOGIN_FAILED".equals(action)) {
                loginFailedByTarget.merge(target == null ? "" : target, 1L, Long::sum);
            }
        }
        String loginSub = loginFailedByTarget.isEmpty() ? "없음"
                : loginFailedByTarget.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                        .limit(3)
                        .map(e -> e.getKey() + " " + e.getValue())
                        .collect(Collectors.joining(" · "));
        long keyViolation = byAction.getOrDefault("KEY_INTEGRITY_VIOLATION", 0L);
        long userViolation = byAction.getOrDefault("USER_INTEGRITY_VIOLATION", 0L);
        long chainViolation = byAction.getOrDefault("AUDIT_CHAIN_VIOLATION", 0L);
        return List.of(
                new Signal("LOGIN_FAILED", "로그인 실패", byAction.getOrDefault("LOGIN_FAILED", 0L), loginSub, "warn"),
                new Signal("USER_PLAIN_VIEWED", "개인정보 원문 조회", byAction.getOrDefault("USER_PLAIN_VIEWED", 0L),
                        "USER_PLAIN_VIEWED · 사유 기록됨", "warn"),
                new Signal("KEY_MATERIAL_VIEWED", "키값 조회", byAction.getOrDefault("KEY_MATERIAL_VIEWED", 0L),
                        "KEY_MATERIAL_VIEWED · 사유 기록됨", "warn"),
                new Signal("INTEGRITY_VIOLATION", "무결성 위반 감지", keyViolation + userViolation + chainViolation,
                        "키 " + keyViolation + " · 사용자 " + userViolation + " · 감사 체인 " + chainViolation, "bad"));
    }

    private List<Failure> failures(Instant now) {
        return usageLogRepository.findFirst20ByResultAndUsedAtGreaterThanEqualOrderByUsedAtDescIdDesc(
                        UsageResult.FAIL, now.minus(FAILURE_DAYS, ChronoUnit.DAYS)).stream()
                .map(u -> new Failure(u.getKey().getKeyUid(), u.getKey().getKeyName(), u.getVersion(),
                        u.getOperation().name(), u.getFailReason(), KstTime.format(u.getUsedAt())))
                .toList();
    }

    private List<AlgoCount> algorithms() {
        Map<String, Long> counts = new TreeMap<>();
        for (CryptoKey key : keyRepository.findByStatusNot(KeyState.DESTROYED)) {
            counts.merge(key.getAlgorithm().name(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new AlgoCount(e.getKey(), e.getValue()))
                .toList();
    }

    // ---------------------------------------------------------------- 사용 추이

    /**
     * @param days 7 | 30
     * @param op   ALL | ENC(ENCRYPT+DECRYPT) | SIGN(SIGN+VERIFY)
     */
    @Transactional(readOnly = true)
    public UsageTrend usageTrend(int days, String op) {
        if (days != 7 && days != 30) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String opName = op == null || op.isBlank() ? "ALL" : op.trim().toUpperCase();
        Set<UsageOperation> ops = switch (opName) {
            case "ALL" -> EnumSet.allOf(UsageOperation.class);
            case "ENC" -> EnumSet.of(UsageOperation.ENCRYPT, UsageOperation.DECRYPT);
            case "SIGN" -> EnumSet.of(UsageOperation.SIGN, UsageOperation.VERIFY);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
        LocalDate today = LocalDate.now(KstTime.ZONE);
        LocalDate start = today.minusDays(days - 1L);
        Map<LocalDate, long[]> agg = new TreeMap<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            agg.put(d, new long[2]);
        }
        Instant since = start.atStartOfDay(KstTime.ZONE).toInstant();
        for (KeyUsageLog log : usageLogRepository.findSince(since)) {
            if (!ops.contains(log.getOperation())) {
                continue;
            }
            LocalDate d = log.getUsedAt().atZone(KstTime.ZONE).toLocalDate();
            long[] slot = agg.get(d);
            if (slot == null) {
                continue;   // 미래 시각(시계 오차)은 무시
            }
            slot[log.getResult() == UsageResult.FAIL ? 1 : 0]++;
        }
        List<UsageTrend.Point> points = agg.entrySet().stream()
                .map(e -> new UsageTrend.Point(e.getKey().toString(), e.getValue()[0], e.getValue()[1]))
                .toList();
        return new UsageTrend(days, opName, points);
    }

    // ---------------------------------------------------------------- 갱신 임박 · 예약 활성

    /** 자동 갱신 키(ACTIVE)의 next_rotation_at 과 PRE_ACTIVE 버전의 activation_date 가 days 일 이내인 항목, 예정 시각 오름차순 */
    @Transactional(readOnly = true)
    public List<ExpiringItem> expiring(int days) {
        if (days < 1 || days > 365) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Instant now = Instant.now();
        Instant until = now.plus(days, ChronoUnit.DAYS);
        LocalDate today = LocalDate.now(KstTime.ZONE);
        List<ExpiringItem> items = new ArrayList<>();
        for (CryptoKey key : keyRepository.findByAutoRotateTrueAndStatusAndNextRotationAtLessThanEqual(KeyState.ACTIVE, until)) {
            items.add(item(key, key.getCurrentVersion(), "ROTATION", key.getNextRotationAt(), today));
        }
        for (KeyMaterial material : materialRepository.findByStateAndActivationDateLessThanEqual(KeyState.PRE_ACTIVE, until)) {
            items.add(item(material.getKey(), material.getVersion(), "ACTIVATION", material.getActivationDate(), today));
        }
        items.sort(Comparator.comparing(ExpiringItem::at));
        return items;
    }

    private static ExpiringItem item(CryptoKey key, int version, String kind, Instant at, LocalDate today) {
        long dday = ChronoUnit.DAYS.between(today, at.atZone(KstTime.ZONE).toLocalDate());
        return new ExpiringItem(key.getKeyUid(), key.getKeyName(), key.getAlgorithm().name(), key.getKeySize(),
                version, kind, KstTime.format(at), dday);
    }
}
