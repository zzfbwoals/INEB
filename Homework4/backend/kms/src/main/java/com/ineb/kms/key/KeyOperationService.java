package com.ineb.kms.key;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.common.KstTime;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.HistoryTrigger;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.domain.KeyStatusHistory;
import com.ineb.kms.key.dto.KeyActionRequest;
import com.ineb.kms.key.dto.KeyDetail;
import com.ineb.kms.repository.KeyMaterialRepository;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상태 변경 연산 5종 (설계서 9.2 / 구현설계 1-7). 모든 전이는 KeyStateMachine 을 통해서만 수행한다.
 * <pre>
 * ACTIVATE   PRE_ACTIVE 버전 → ACTIVE 즉시, current 교체
 * REACTIVATE INTEGRITY 정지 버전만: 언래핑 검증 → 해시 재계산 → ACTIVE (구 버전이면 복호화 전용, 최신이면 current 복귀)
 * DEACTIVATE version 지정 → 구 ACTIVE 버전만(최신은 409) / 생략 → 키 전체 정지 + 자동 갱신 중단
 * ROTATE     새 버전 생성(ACTIVE 또는 예약 PRE_ACTIVE). 구 버전 ACTIVE 유지. 상한 100
 * DESTROY    version 지정 → DEACTIVATED/PRE_ACTIVE 버전 / 생략 → ACTIVE 존재 시 409, 남은 전 버전 파기
 * </pre>
 */
@Service
public class KeyOperationService {

    private final KeyService keyService;
    private final KeyMaterialRepository materialRepository;
    private final KeyMaterialFactory materialFactory;
    private final KeyStateMachine stateMachine;
    private final KeyIntegrityHasher hasher;
    private final AuditHook auditHook;

    public KeyOperationService(KeyService keyService, KeyMaterialRepository materialRepository,
                               KeyMaterialFactory materialFactory, KeyStateMachine stateMachine,
                               KeyIntegrityHasher hasher, AuditHook auditHook) {
        this.keyService = keyService;
        this.materialRepository = materialRepository;
        this.materialFactory = materialFactory;
        this.stateMachine = stateMachine;
        this.hasher = hasher;
        this.auditHook = auditHook;
    }

    @Transactional
    public KeyDetail execute(String keyUid, KeyActionRequest req, String actor) {
        CryptoKey key = keyService.load(keyUid);
        switch (req.action()) {
            case ACTIVATE -> activate(key, req, actor);
            case REACTIVATE -> reactivate(key, req, actor);
            case DEACTIVATE -> deactivate(key, req, actor);
            case ROTATE -> rotate(key, KstTime.parse(req.activationDate()), req.reason(), actor, HistoryTrigger.ROTATE);
            case DESTROY -> destroy(key, req, actor);
        }
        return keyService.toDetail(key);
    }

    // ---------------------------------------------------------------- ACTIVATE

    private void activate(CryptoKey key, KeyActionRequest req, String actor) {
        KeyMaterial target = req.version() != null
                ? version(key, req.version())
                : materialRepository.findByKeyIdAndState(key.getId(), KeyState.PRE_ACTIVE).stream()
                        .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.KEY_STATE_CONFLICT));
        if (target.getState() != KeyState.PRE_ACTIVE) {
            throw new BusinessException(ErrorCode.KEY_STATE_CONFLICT);
        }
        target.rescheduleActivation(Instant.now());
        stateMachine.transition(target, KeyState.ACTIVE, HistoryTrigger.OPERATION, req.reason(), actor);
        pointCurrentIfNewer(key, target);
        audit(actor, "KEY_STATUS_CHANGED", key, "action=ACTIVATE, version=" + target.getVersion());
    }

    // ---------------------------------------------------------------- REACTIVATE

    private void reactivate(CryptoKey key, KeyActionRequest req, String actor) {
        if (req.version() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        KeyMaterial target = version(key, req.version());
        if (!target.isReactivatable()) {
            throw new BusinessException(ErrorCode.KEY_REACTIVATE_NOT_ALLOWED);
        }
        // ① 재료 무손상 확인 — GCM 태그 검증 실패면 복구 불가
        byte[] plain = null;
        try {
            plain = materialFactory.unwrap(target.getWrappedKey(), target.getIv());
        } catch (GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.KEY_MATERIAL_CORRUPTED);
        } finally {
            if (plain != null) {
                Arrays.fill(plain, (byte) 0);
            }
        }
        // ② 현재 메타로 해시 재계산 → ③ ACTIVE 전이 (상태머신이 전이 후 다시 재계산)
        hasher.rehash(target);
        stateMachine.transition(target, KeyState.ACTIVE, HistoryTrigger.REACTIVATE, req.reason(), actor);
        pointCurrentIfNewer(key, target);
        audit(actor, "KEY_REACTIVATED", key, "version=" + target.getVersion());
    }

    // ---------------------------------------------------------------- DEACTIVATE

    private void deactivate(CryptoKey key, KeyActionRequest req, String actor) {
        if (req.version() != null) {
            KeyMaterial target = version(key, req.version());
            if (target.getState() != KeyState.ACTIVE) {
                throw new BusinessException(ErrorCode.KEY_STATE_CONFLICT);
            }
            stateMachine.transition(target, KeyState.DEACTIVATED, HistoryTrigger.OPERATION, req.reason(), actor);
            audit(actor, "KEY_STATUS_CHANGED", key, "action=DEACTIVATE, version=" + target.getVersion());
            return;
        }
        List<KeyMaterial> actives = materialRepository.findByKeyIdAndState(key.getId(), KeyState.ACTIVE);
        if (actives.isEmpty()) {
            throw new BusinessException(ErrorCode.KEY_STATE_CONFLICT);
        }
        for (KeyMaterial m : actives) {
            stateMachine.transitionKeyWide(m, KeyState.DEACTIVATED, HistoryTrigger.OPERATION,
                    "[키 정지] " + req.reason(), actor);
        }
        key.stopAutoRotation();
        hasher.rehash(key);
        audit(actor, "KEY_STATUS_CHANGED", key, "action=DEACTIVATE, scope=KEY, versions=" + actives.size());
    }

    // ---------------------------------------------------------------- ROTATE

    /**
     * 갱신. 스케줄러도 trigger=SCHEDULE 로 호출한다.
     * @param activationDate null 또는 과거면 즉시 ACTIVE·current 교체, 미래면 PRE_ACTIVE 예약
     */
    @Transactional
    public KeyMaterial rotate(CryptoKey key, Instant activationDate, String reason, String actor, HistoryTrigger trigger) {
        if (key.getStatus() == KeyState.DESTROYED) {
            throw new BusinessException(ErrorCode.KEY_STATE_CONFLICT);
        }
        List<KeyMaterial> materials = materialRepository.findByKeyIdOrderByVersionDesc(key.getId());
        if (materials.size() >= CryptoKey.MAX_VERSIONS) {
            throw new BusinessException(ErrorCode.KEY_VERSION_LIMIT);
        }
        // 이미 예약된 PRE_ACTIVE 버전이 있으면 취소(삭제)하고 새로 만든다
        for (KeyMaterial m : materials) {
            if (m.getState() == KeyState.PRE_ACTIVE) {
                stateMachine.transitionKeyWide(m, KeyState.DESTROYED, HistoryTrigger.ROTATE,
                        "새 갱신으로 예약 취소", actor);
                m.destroyMaterial(Instant.now());
                hasher.rehash(m);
            }
        }
        int nextVersion = materials.stream().mapToInt(KeyMaterial::getVersion).max().orElse(0) + 1;
        Instant now = Instant.now();
        boolean immediate = KeyService.isImmediate(activationDate, now);

        KeyMaterialFactory.Generated g = materialFactory.generate(key);
        KeyMaterial created = new KeyMaterial(key, nextVersion, immediate ? KeyState.ACTIVE : KeyState.PRE_ACTIVE,
                g.wrappedKey(), g.iv(), g.publicKey(), immediate ? now : activationDate);
        materialRepository.save(created);

        if (immediate) {
            key.pointCurrent(nextVersion);
            if (trigger == HistoryTrigger.SCHEDULE) {
                key.scheduleNextRotation(now);
            }
        }
        stateMachine.recordCreated(created, trigger,
                immediate ? reason : reason + " (활성일 " + KstTime.format(activationDate) + " 예약)", actor);
        audit(actor, "KEY_ROTATED", key, "newVersion=" + nextVersion + ", state=" + created.getState()
                + ", trigger=" + trigger);
        return created;
    }

    // ---------------------------------------------------------------- DESTROY

    private void destroy(CryptoKey key, KeyActionRequest req, String actor) {
        List<KeyMaterial> targets;
        if (req.version() != null) {
            KeyMaterial target = version(key, req.version());
            if (target.getState() == KeyState.ACTIVE) {
                throw new BusinessException(ErrorCode.KEY_STATE_CONFLICT);
            }
            targets = List.of(target);
        } else {
            if (!materialRepository.findByKeyIdAndState(key.getId(), KeyState.ACTIVE).isEmpty()) {
                throw new BusinessException(ErrorCode.KEY_ACTIVE_EXISTS);
            }
            targets = materialRepository.findByKeyIdOrderByVersionDesc(key.getId()).stream()
                    .filter(m -> m.getState() != KeyState.DESTROYED).toList();
            if (targets.isEmpty()) {
                throw new BusinessException(ErrorCode.KEY_STATE_CONFLICT);
            }
        }
        Instant now = Instant.now();
        for (KeyMaterial m : targets) {
            stateMachine.transitionKeyWide(m, KeyState.DESTROYED, HistoryTrigger.OPERATION, req.reason(), actor);
            m.destroyMaterial(now);
            hasher.rehash(m);            // wrapped_key NULL 반영
        }
        if (key.getStatus() == KeyState.DESTROYED) {
            key.stopAutoRotation();
            hasher.rehash(key);
        }
        audit(actor, "KEY_DESTROYED", key, req.version() != null
                ? "version=" + req.version() : "scope=KEY, versions=" + targets.size());
    }

    // ---------------------------------------------------------------- helpers

    private KeyMaterial version(CryptoKey key, int version) {
        return materialRepository.findByKeyIdAndVersion(key.getId(), version)
                .orElseThrow(() -> new BusinessException(ErrorCode.KEY_VERSION_NOT_FOUND));
    }

    /** 활성화된 버전이 현행보다 새롭거나, 현행 버전이 더 이상 ACTIVE 가 아니면 current 를 옮긴다. */
    private void pointCurrentIfNewer(CryptoKey key, KeyMaterial activated) {
        boolean currentActive = materialRepository.findByKeyIdAndVersion(key.getId(), key.getCurrentVersion())
                .map(m -> m.getState() == KeyState.ACTIVE).orElse(false);
        if (activated.getVersion() > key.getCurrentVersion() || !currentActive) {
            key.pointCurrent(activated.getVersion());
            hasher.rehash(key);
        }
    }

    private void audit(String actor, String action, CryptoKey key, String detail) {
        auditHook.record(actor == null ? KeyStatusHistory.SYSTEM_ACTOR : actor, action, key.getKeyUid(), detail);
    }
}
