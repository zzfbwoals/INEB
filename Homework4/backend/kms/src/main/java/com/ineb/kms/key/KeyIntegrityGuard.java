package com.ineb.kms.key;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.HistoryTrigger;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.domain.KeyStatusHistory;
import com.ineb.kms.repository.KeyMaterialRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 무결성 검증과 위반 시 자동 정지.
 * - 버전(key_material) 해시 불일치 → 해당 버전 DEACTIVATED(trigger=INTEGRITY). 무결성 정지 버전만 REACTIVATE 로 복구 가능.
 * - 키(crypto_key) 해시 불일치 → 그 키의 모든 ACTIVE 버전 정지.
 * 검증 시점: 상세 조회(플래그), 테스트 언래핑 직전(자동 정지), 스케줄러 배치(설정으로 켤 때).
 * 3주차 audit_log 연결 지점은 {@link AuditHook}.
 */
@Component
public class KeyIntegrityGuard {

    private static final Logger log = LoggerFactory.getLogger(KeyIntegrityGuard.class);
    static final String VIOLATION_REASON = "integrity_hash 불일치 감지 — 자동 정지";
    static final String KEY_VIOLATION_REASON = "crypto_key integrity_hash 불일치 감지 — 키의 운영 버전 전체 자동 정지";

    private final KeyIntegrityHasher hasher;
    private final KeyStateMachine stateMachine;
    private final KeyMaterialRepository materialRepository;
    private final AuditHook auditHook;

    public KeyIntegrityGuard(KeyIntegrityHasher hasher, KeyStateMachine stateMachine,
                             KeyMaterialRepository materialRepository, AuditHook auditHook) {
        this.hasher = hasher;
        this.stateMachine = stateMachine;
        this.materialRepository = materialRepository;
        this.auditHook = auditHook;
    }

    /** 조회용 — 상태를 바꾸지 않고 검증 결과만 돌려준다. */
    public boolean isValid(CryptoKey key) {
        return hasher.verify(key);
    }

    public boolean isValid(KeyMaterial material) {
        return hasher.verify(material);
    }

    /**
     * 언래핑 직전 검증. 불일치면 즉시 자동 정지하고 KEY_INTEGRITY_VIOLATION 을 던진다.
     * 이미 정지된 버전은 다시 정지하지 않는다.
     */
    @Transactional(noRollbackFor = BusinessException.class)   // 자동 정지를 커밋한 채 409 를 던진다
    public void verifyOrDeactivate(KeyMaterial material) {
        if (hasher.verify(material)) {
            return;
        }
        deactivateForViolation(material);
        throw new BusinessException(ErrorCode.KEY_INTEGRITY_VIOLATION);
    }

    /**
     * 키 메타 검증. 불일치면 키의 ACTIVE 버전을 전부 자동 정지하고 예외를 던진다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void verifyOrDeactivate(CryptoKey key) {
        if (hasher.verify(key)) {
            return;
        }
        deactivateKeyWide(key);
        throw new BusinessException(ErrorCode.KEY_INTEGRITY_VIOLATION);
    }

    /**
     * 조회 시점 강제 (2026-08-31 개정) — 스케줄러·사용 시점과 별개로, 상세 조회 순간
     * 키와 전 버전을 재검증해 위반을 즉시 자동 정지한다. 예외를 던지지 않으므로
     * 응답에는 정지된 상태(DEACTIVATED, trigger=INTEGRITY)가 그대로 실려 나간다.
     */
    @Transactional
    public void enforceOnRead(CryptoKey key) {
        for (KeyMaterial m : materialRepository.findByKeyIdOrderByVersionDesc(key.getId())) {
            if (m.getState() == KeyState.DEACTIVATED || m.getState() == KeyState.DESTROYED || hasher.verify(m)) {
                continue;
            }
            deactivateForViolation(m);
        }
        if (!hasher.verify(key) && !materialRepository.findByKeyIdAndState(key.getId(), KeyState.ACTIVE).isEmpty()) {
            deactivateKeyWide(key);
        }
    }

    private void deactivateKeyWide(CryptoKey key) {
        List<KeyMaterial> actives = materialRepository.findByKeyIdAndState(key.getId(), KeyState.ACTIVE);
        for (KeyMaterial m : actives) {
            stateMachine.transitionKeyWide(m, KeyState.DEACTIVATED, HistoryTrigger.INTEGRITY,
                    KEY_VIOLATION_REASON, KeyStatusHistory.SYSTEM_ACTOR);
        }
        log.warn("crypto_key 무결성 위반: keyUid={}, 정지된 버전 수={}", key.getKeyUid(), actives.size());
        auditHook.record(KeyStatusHistory.SYSTEM_ACTOR, "KEY_INTEGRITY_VIOLATION", key.getKeyUid(),
                "scope=KEY, deactivated=" + actives.size());
    }

    /**
     * 배치 검증(스케줄러): 재료가 남아 있는 모든 버전을 검사해 위반 버전을 정지한다. 정지 건수를 돌려준다.
     */
    @Transactional
    public int sweep() {
        int violated = 0;
        for (KeyMaterial m : materialRepository.findByStateNot(KeyState.DESTROYED)) {
            if (m.getState() == KeyState.DEACTIVATED || hasher.verify(m)) {
                continue;
            }
            deactivateForViolation(m);
            violated++;
        }
        return violated;
    }

    private void deactivateForViolation(KeyMaterial material) {
        if (material.getState() == KeyState.DEACTIVATED || material.getState() == KeyState.DESTROYED) {
            return;
        }
        CryptoKey key = material.getKey();
        if (material.getState() == KeyState.ACTIVE) {
            stateMachine.transitionKeyWide(material, KeyState.DEACTIVATED, HistoryTrigger.INTEGRITY,
                    VIOLATION_REASON, KeyStatusHistory.SYSTEM_ACTOR);
        } else {
            // PRE_ACTIVE 는 정지 전이가 없으므로 예약을 취소(삭제)한다
            stateMachine.transitionKeyWide(material, KeyState.DESTROYED, HistoryTrigger.INTEGRITY,
                    VIOLATION_REASON + " (준비 버전은 삭제)", KeyStatusHistory.SYSTEM_ACTOR);
            material.destroyMaterial(java.time.Instant.now());
        }
        log.warn("key_material 무결성 위반: keyUid={}, version={}", key.getKeyUid(), material.getVersion());
        auditHook.record(KeyStatusHistory.SYSTEM_ACTOR, "KEY_INTEGRITY_VIOLATION", key.getKeyUid(),
                "version=" + material.getVersion() + ", autoDeactivated=true");
    }
}
