package com.ineb.kms.audit;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실시간 이벤트 구독 (SSE). 브라우저 EventSource 는 Authorization 헤더를 설정할 수 없어
 * 이 경로에 한해 JWT 를 쿼리 파라미터(token)로도 받는다 — {@code JwtAuthenticationFilter} 참조.
 */
@RestController
public class EventStreamController {

    private final AuditEventStream eventStream;

    public EventStreamController(AuditEventStream eventStream) {
        this.eventStream = eventStream;
    }

    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return eventStream.subscribe();
    }
}
