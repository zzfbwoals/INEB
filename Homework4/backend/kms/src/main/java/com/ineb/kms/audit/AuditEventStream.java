package com.ineb.kms.audit;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 관리자 행위 실시간 브로드캐스트 (SSE).
 * <p>
 * 모든 관리자 행위·시스템 이벤트는 audit_log 체인 기록을 거치므로, {@link AuditChainService}가
 * 기록할 때마다 {action, target}을 접속 중인 브라우저 전부에 쏘아 화면(키 상세·목록·감사 로그)이
 * 새로고침 없이 갱신되게 한다. 브로드캐스트는 트랜잭션 커밋 이후에 수행한다 — 수신 측이 즉시
 * refetch 했을 때 방금 커밋된 데이터가 보이도록.
 */
@Component
public class AuditEventStream {

    private static final Logger log = LoggerFactory.getLogger(AuditEventStream.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** 타임아웃 없음(0L) — 연결 유지는 하트비트가, 정리는 전송 실패 시점이 담당 */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void publish(String action, String target) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(action, target);
                }
            });
        } else {
            broadcast(action, target);
        }
    }

    private void broadcast(String action, String target) {
        String json = "{\"action\":\"" + escape(action) + "\",\"target\":\"" + escape(target) + "\"}";
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("audit").data(json));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

    /** 프록시(Nginx)·브라우저 유휴 타임아웃으로 연결이 끊기지 않도록 주석 프레임을 보낸다 */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

    int connectionCount() {
        return emitters.size();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
