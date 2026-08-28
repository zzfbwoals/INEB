package com.ineb.kms.key;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.DeactivationTrigger;
import com.ineb.kms.domain.HistoryTrigger;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.domain.KeyStatusHistory;
import com.ineb.kms.repository.KeyMaterialRepository;
import com.ineb.kms.repository.KeyStatusHistoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 키 버전 상태 전이의 유일한 통로. 전이 표 검증 → 특수 규칙 → 버전 상태 변경 → 이력 기록 → 키 상태 재계산 → 무결성 해시 재계산.
 *
 * 허용 전이 (설계서 9.2):
 *   PRE_ACTIVE  → ACTIVE (활성일 도래·ACTIVATE) · DESTROYED
 *   ACTIVE      → DEACTIVATED (DEACTIVATE · 무결성 위반 자동)
 *   DEACTIVATED → DESTROYED · ACTIVE (REACTIVATE — 무결성 정지 버전 한정)
 *   DESTROYED   → 없음 (종단)
 * 금지: 관리자 수동 정지 버전의 재활성화 · ACTIVE→DESTROYED 직행 · PRE_ACTIVE→DEACTIVATED · 최신 버전 단독 정지.
 */
@Component
public class KeyStateMachine {

    private static final Map<KeyState, Set<KeyState>> ALLOWED = Map.of(
            KeyState.PRE_ACTIVE, Set.of(KeyState.ACTIVE, KeyState.DESTROYED),
            KeyState.ACTIVE, Set.of(KeyState.DEACTIVATED),
            KeyState.DEACTIVATED, Set.of(KeyState.DESTROYED, KeyState.ACTIVE),
            KeyState.DESTROYED, Set.of());

    private final KeyMaterialRepository materialRepository;
    private final KeyStatusHistoryRepository historyRepository;
    private final KeyIntegrityHasher hasher;

    public KeyStateMachine(KeyMaterialRepository materialRepository,
                           KeyStatusHistoryRepository historyRepository,
                           KeyIntegrityHasher hasher) {
        this.materialRepository = materialRepository;
        this.historyRepository = historyRepository;
        this.hasher = hasher;
    }

    /** 단일 버전 전이. 최신 버전 단독 정지는 거부된다 (키 전체 정지는 {@link #transitionKeyWide}). */
    @Transactional
    public KeyMaterial transition(KeyMaterial material, KeyState to, HistoryTrigger trigger,
                                  String reason, String actor) {
        return doTransition(material, to, trigger, reason, actor, false);
    }

    /** 키 전체 정지처럼 최신 버전을 포함해 여러 버전을 함께 바꿀 때 사용 (최신 버전 단독 정지 검사 생략). */
    @Transactional
    public KeyMaterial transitionKeyWide(KeyMaterial material, KeyState to, HistoryTrigger trigger,
                                         String reason, String actor) {
        return doTransition(material, to, trigger, reason, actor, true);
    }

    /**
     * 새 버전 생성(등록·갱신) 이력. 상태 검증 없이 from=null 이력을 남기고 키 상태·해시를 재계산한다.
     * 호출 전에 material 이 저장돼 있어야 한다.
     */
    @Transactional
    public KeyMaterial recordCreated(KeyMaterial material, HistoryTrigger trigger, String reason, String actor) {
        CryptoKey key = material.getKey();
        historyRepository.save(new KeyStatusHistory(key, material.getVersion(), null, material.getState(),
                reason, trigger, actor));
        refresh(key, material);
        return material;
    }

    private KeyMaterial doTransition(KeyMaterial material, KeyState to, HistoryTrigger trigger,
                                     String reason, String actor, boolean keyWide) {
        KeyState from = material.getState();
        CryptoKey key = material.getKey();

        if (from == to) {
            throw new BusinessException(ErrorCode.KEY_STATE_CONFLICT);
        }
        if (!ALLOWED.get(from).contains(to)) {
            throw new BusinessException(ErrorCode.KEY_TRANSITION_NOT_ALLOWED);
        }
        if (to == KeyState.ACTIVE && from == KeyState.DEACTIVATED) {
            if (!material.isReactivatable() || trigger != HistoryTrigger.REACTIVATE) {
                throw new BusinessException(ErrorCode.KEY_REACTIVATE_NOT_ALLOWED);
            }
        }
        if (to == KeyState.DEACTIVATED && trigger == HistoryTrigger.OPERATION && !keyWide
                && material.getVersion() == key.getCurrentVersion()) {
            throw new BusinessException(ErrorCode.KEY_LATEST_VERSION_DEACTIVATE);
        }

        DeactivationTrigger deactivationTrigger = null;
        if (to == KeyState.DEACTIVATED) {
            deactivationTrigger = trigger == HistoryTrigger.INTEGRITY
                    ? DeactivationTrigger.INTEGRITY : DeactivationTrigger.OPERATION;
        }
        material.transition(to, deactivationTrigger);

        historyRepository.save(new KeyStatusHistory(key, material.getVersion(), from, to, reason, trigger, actor));
        refresh(key, material);
        return material;
    }

    /** 키 상태 파생 재계산 + 버전·키 무결성 해시 재계산 (state 가 정규화 문자열에 포함되므로 필수). */
    private void refresh(CryptoKey key, KeyMaterial changed) {
        List<KeyMaterial> materials = new ArrayList<>(
                key.getId() == null ? List.of() : materialRepository.findByKeyIdOrderByVersionDesc(key.getId()));
        // 영속성 컨텍스트 밖(테스트·다른 인스턴스)에서 온 목록이면 변경된 버전으로 치환
        materials.removeIf(m -> m != changed && sameVersion(m, changed));
        if (!materials.contains(changed)) {
            materials.add(changed);
        }
        key.recalcStatus(materials);
        hasher.rehash(changed);
        hasher.rehash(key);
    }

    private static boolean sameVersion(KeyMaterial a, KeyMaterial b) {
        return a.getId() != null && a.getId().equals(b.getId());
    }
}
