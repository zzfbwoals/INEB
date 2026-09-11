package com.ineb.kms.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.dto.AuditLogItem;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.crypto.MasterKeyHolder;
import com.ineb.kms.crypto.PersonalDataCodec;
import com.ineb.kms.crypto.WrappedSecretStore;
import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.repository.AuditLogRepository;
import com.ineb.kms.repository.AuditLogShadowRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** 감사 로그 detail 암호화 — 기록 시 detail 컬럼에 암호문 저장·암호문 기준 체인 해시, 목록 조회 시 복호화 (평문 레거시 행은 그대로) */
class AuditDetailEncryptionTest {

    private final byte[] integrityKey = new byte[32];
    private PersonalDataCodec codec;
    private AuditHasher hasher;
    private AuditLogRepository repository;
    private AuditLogShadowRepository shadowRepository;
    private final AtomicReference<AuditLog> saved = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        MasterKeyHolder holder = mock(MasterKeyHolder.class);
        when(holder.getKey()).thenReturn(new byte[32]);
        WrappedSecretStore store = mock(WrappedSecretStore.class);
        when(store.integrityKey()).thenReturn(integrityKey);
        codec = new PersonalDataCodec(holder, store);
        hasher = new AuditHasher(integrityKey);

        repository = mock(AuditLogRepository.class);
        when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(repository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog row = withId(inv.getArgument(0), 42);   // IDENTITY 는 persist 시 id 가 채워진다
            saved.set(row);
            return row;
        });
        shadowRepository = mock(AuditLogShadowRepository.class);
        when(shadowRepository.insertIgnore(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("append 는 detail 을 암호화해 저장하고 암호문으로 해시하며, 같은 id 로 섀도에도 이중 기록한다")
    void appendEncryptsDetail() {
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        AuditEventStream stream = mock(AuditEventStream.class);
        AuditChainService service = new AuditChainService(repository, shadowRepository, hasher, em, stream, codec);

        service.append("admin", "NOTICE_CREATED", "NOTICE#1", "title=점검 안내, files=2");

        AuditLog row = saved.get();
        assertNotEquals("title=점검 안내, files=2", row.getDetail());
        assertEquals("title=점검 안내, files=2", codec.decrypt(row.getDetail()));
        assertEquals(AuditLog.CHAIN_ANCHOR, row.getPrevHash());
        assertTrue(hasher.verifyRow(row));
        assertEquals(hasher.rowHash(AuditLog.CHAIN_ANCHOR, "admin", "NOTICE_CREATED", "NOTICE#1",
                row.getDetail(), row.getCreatedAt()), row.getRowHash());
        // 섀도 이중 기록 — 원본과 같은 id·같은 값(암호문·해시 포함)
        verify(shadowRepository).insertIgnore(eq(42L), eq("admin"), eq("NOTICE_CREATED"), eq("NOTICE#1"),
                eq(row.getDetail()), eq(AuditLog.CHAIN_ANCHOR), eq(row.getRowHash()), eq(row.getCreatedAt()));
    }

    @Test
    @DisplayName("목록은 암호문을 복호화해 보여주고, 복호화되지 않는 값(암호화 이전 평문 행)은 그대로 보여준다")
    @SuppressWarnings("unchecked")
    void listDecryptsDetail() {
        Instant at = Instant.parse("2026-09-09T00:00:00Z");
        AuditLog encrypted = withId(new AuditLog("admin", "KEY_CREATED", "KEY#u",
                codec.encrypt("algorithm=AES"), "EMPTY", "h1", at), 1);
        AuditLog legacy = withId(new AuditLog("admin", "LOGOUT", "AUTH#admin", "평문 상세", "h1", "h2", at), 2);
        AuditLog legacyBase64Like = withId(new AuditLog("admin", "LOGIN_SUCCESS", "AUTH#admin",
                "bm90LWEtdmFsaWQtY2lwaGVyLXRleHQtYnV0LWJhc2U2NA==", "h2", "h3", at), 3);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(encrypted, legacy, legacyBase64Like)));
        AuditLogService service = new AuditLogService(repository, shadowRepository, mock(AuditChainService.class),
                new AuditShadowComparer(new AuditChainVerifier(hasher)), mock(AuditShadowGuard.class), codec);

        PageResponse<AuditLogItem> page = service.list(null, null, null, null, null, 0, 20, null, null);

        assertEquals("algorithm=AES", page.content().get(0).detail());
        assertEquals("평문 상세", page.content().get(1).detail());
        assertEquals("bm90LWEtdmFsaWQtY2lwaGVyLXRleHQtYnV0LWJhc2U2NA==", page.content().get(2).detail());
    }

    private static AuditLog withId(AuditLog row, long id) {
        try {
            Field f = AuditLog.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(row, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return row;
    }
}
