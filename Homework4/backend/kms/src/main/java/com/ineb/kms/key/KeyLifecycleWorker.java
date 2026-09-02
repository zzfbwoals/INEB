package com.ineb.kms.key;

import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.HistoryTrigger;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.domain.KeyStatusHistory;
import com.ineb.kms.repository.CryptoKeyRepository;
import com.ineb.kms.repository.KeyMaterialRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스케줄러 작업의 건별 트랜잭션 단위. 한 건 실패가 다른 건을 막지 않도록 REQUIRES_NEW 로 분리하고,
 * 트랜잭션 안에서 엔티티를 다시 읽어 조건을 재확인한다(조회 시점과 처리 시점 사이의 상태 변화 대비).
 */
@Component
public class KeyLifecycleWorker {

    static final String ACTIVATION_REASON = "활성일 도래 — 자동 활성";
    static final String ROTATION_REASON = "갱신 주기 도래 — 자동 갱신";

    private final KeyMaterialRepository materialRepository;
    private final CryptoKeyRepository keyRepository;
    private final KeyStateMachine stateMachine;
    private final KeyOperationService operationService;
    private final KeyIntegrityHasher hasher;
    private final KeyIntegrityGuard integrityGuard;

    public KeyLifecycleWorker(KeyMaterialRepository materialRepository, CryptoKeyRepository keyRepository,
                              KeyStateMachine stateMachine, KeyOperationService operationService,
                              KeyIntegrityHasher hasher, KeyIntegrityGuard integrityGuard) {
        this.materialRepository = materialRepository;
        this.keyRepository = keyRepository;
        this.stateMachine = stateMachine;
        this.operationService = operationService;
        this.hasher = hasher;
        this.integrityGuard = integrityGuard;
    }

    /** 활성일이 도래한 PRE_ACTIVE 버전을 ACTIVE 로 전이하고 current 를 교체한다. 처리했으면 true. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean activateDue(Long materialId, Instant now) {
        KeyMaterial m = materialRepository.findById(materialId).orElse(null);
        if (m == null || m.getState() != KeyState.PRE_ACTIVE || m.getActivationDate().isAfter(now)) {
            return false;
        }
        CryptoKey key = m.getKey();
        stateMachine.transition(m, KeyState.ACTIVE, HistoryTrigger.DATE_REACHED, ACTIVATION_REASON,
                KeyStatusHistory.SYSTEM_ACTOR);
        if (m.getVersion() > key.getCurrentVersion()
                || materialRepository.findByKeyIdAndVersion(key.getId(), key.getCurrentVersion())
                        .map(c -> c.getState() != KeyState.ACTIVE).orElse(true)) {
            key.pointCurrent(m.getVersion());
            hasher.rehash(key);
        }
        return true;
    }

    /** 갱신 주기가 도래한 자동 갱신 키를 회전한다 (trigger=SCHEDULE, 다음 갱신일 재계산). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean rotateDue(Long keyId, Instant now) {
        CryptoKey key = keyRepository.findById(keyId).orElse(null);
        if (key == null || !key.isAutoRotate() || key.getStatus() != KeyState.ACTIVE
                || key.getNextRotationAt() == null || key.getNextRotationAt().isAfter(now)) {
            return false;
        }
        operationService.rotate(key, null, ROTATION_REASON, KeyStatusHistory.SYSTEM_ACTOR, HistoryTrigger.SCHEDULE);
        return true;
    }

    /** 무결성 배치 검증 — 위반 버전 자동 정지 + 키 메타(crypto_key) 위반 키 전체 정지 건수 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int sweepIntegrity() {
        int violated = integrityGuard.sweep();
        for (CryptoKey key : keyRepository.findAll()) {
            if (integrityGuard.enforceKey(key)) {
                violated++;
            }
        }
        return violated;
    }
}
