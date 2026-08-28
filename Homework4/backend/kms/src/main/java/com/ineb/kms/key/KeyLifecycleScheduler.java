package com.ineb.kms.key;

import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.repository.CryptoKeyRepository;
import com.ineb.kms.repository.KeyMaterialRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 키 생명주기 스케줄러 (60초). 설계서 9.2 "활성일 지정 및 자동 전이" · 갱신 주기.
 *  ① activation_date <= now AND PRE_ACTIVE → ACTIVE (trigger=DATE_REACHED)
 *  ② next_rotation_at <= now AND auto_rotate AND 키 ACTIVE → ROTATE (trigger=SCHEDULE)
 *  ③ (선택) 전 버전 무결성 재검증 → 위반 자동 정지 — kms.scheduler.integrity-check (기본 false)
 * 각 건은 KeyLifecycleWorker 의 독립 트랜잭션으로 처리한다. kms.scheduler.enabled=false 로 전체 비활성.
 */
@Component
public class KeyLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeyLifecycleScheduler.class);

    private final KeyMaterialRepository materialRepository;
    private final CryptoKeyRepository keyRepository;
    private final KeyLifecycleWorker worker;
    private final boolean enabled;
    private final boolean integrityCheck;

    public KeyLifecycleScheduler(KeyMaterialRepository materialRepository, CryptoKeyRepository keyRepository,
                                 KeyLifecycleWorker worker,
                                 @Value("${kms.scheduler.enabled:true}") boolean enabled,
                                 @Value("${kms.scheduler.integrity-check:false}") boolean integrityCheck) {
        this.materialRepository = materialRepository;
        this.keyRepository = keyRepository;
        this.worker = worker;
        this.enabled = enabled;
        this.integrityCheck = integrityCheck;
    }

    @Scheduled(fixedDelayString = "${kms.scheduler.interval-ms:60000}", initialDelayString = "${kms.scheduler.initial-delay-ms:30000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();
        int activated = activateDue(now);
        int rotated = rotateDue(now);
        int violated = integrityCheck ? sweep() : 0;
        if (activated + rotated + violated > 0) {
            log.info("키 생명주기 스케줄러: 활성 {}건, 자동 갱신 {}건, 무결성 위반 정지 {}건", activated, rotated, violated);
        }
    }

    int activateDue(Instant now) {
        List<KeyMaterial> due = materialRepository.findByStateAndActivationDateLessThanEqual(KeyState.PRE_ACTIVE, now);
        int count = 0;
        for (KeyMaterial m : due) {
            try {
                if (worker.activateDue(m.getId(), now)) {
                    count++;
                }
            } catch (RuntimeException e) {
                log.error("활성일 자동 전이 실패: materialId={}", m.getId(), e);
            }
        }
        return count;
    }

    int rotateDue(Instant now) {
        List<CryptoKey> due = keyRepository.findByAutoRotateTrueAndNextRotationAtLessThanEqualAndStatus(now, KeyState.ACTIVE);
        int count = 0;
        for (CryptoKey k : due) {
            try {
                if (worker.rotateDue(k.getId(), now)) {
                    count++;
                }
            } catch (RuntimeException e) {
                log.error("자동 갱신 실패: keyUid={}", k.getKeyUid(), e);
            }
        }
        return count;
    }

    int sweep() {
        try {
            return worker.sweepIntegrity();
        } catch (RuntimeException e) {
            log.error("무결성 배치 검증 실패", e);
            return 0;
        }
    }
}
