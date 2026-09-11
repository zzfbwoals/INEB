package com.ineb.kms.integrity;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL LISTEN 스레드 — {@link IntegrityChangeTrigger} 가 보내는 {@code kms_integrity} 알림을 받아
 * {@link IntegrityChangeHandler} 로 넘긴다. 알림은 변경 트랜잭션이 커밋된 뒤 도착하므로 재처리는 커밋된 값을 본다.
 * <ul>
 *   <li>연결 하나를 풀에서 꺼내 상시 점유한다(반납하지 않음). 끊기면 5초 뒤 다시 LISTEN 한다.</li>
 *   <li>대량 UPDATE 는 행 수만큼 알림이 오므로 첫 알림 뒤 200ms 동안 잠잠해질 때까지(최대 500건) 모아 한 묶음으로 넘긴다.</li>
 *   <li>기동 시 자기 연결의 application_name 이 {@link IntegrityChangeTrigger#APP_NAME} 인지 확인한다 — 아니면 앱 자신의
 *       감사 기록 INSERT 까지 "직접 수정"으로 보여 무한 루프가 되므로, 그 경우 audit_log INSERT 알림은 무시하도록 핸들러에 알린다.</li>
 * </ul>
 * {@code kms.integrity.listen=false} 로 끌 수 있다. 스케줄러(60초)는 알림을 놓친 변경(앱 정지 중 등)의 안전망.
 */
@Component
public class IntegrityChangeListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(IntegrityChangeListener.class);
    private static final int POLL_MS = 5000;
    private static final int QUIET_MS = 200;
    private static final int BATCH_MAX = 500;
    private static final long RETRY_MS = 5000;

    private final DataSource dataSource;
    private final IntegrityChangeHandler handler;
    private final boolean enabled;
    private volatile boolean running;
    private Thread thread;

    public IntegrityChangeListener(DataSource dataSource, IntegrityChangeHandler handler,
                                   @Value("${kms.integrity.listen:true}") boolean enabled) {
        this.dataSource = dataSource;
        this.handler = handler;
        this.enabled = enabled;
    }

    @Override
    public void start() {
        if (!enabled || running) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "integrity-listen");
        thread.setDaemon(true);
        thread.start();
        log.info("DB 변경 알림 수신 시작 (LISTEN {})", IntegrityChangeTrigger.CHANNEL);
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                connection.setAutoCommit(true);
                checkOwnName(statement);
                statement.execute("LISTEN " + IntegrityChangeTrigger.CHANNEL);
                PGConnection pg = connection.unwrap(PGConnection.class);
                while (running) {
                    PGNotification[] first = pg.getNotifications(POLL_MS);
                    if (first == null || first.length == 0) {
                        continue;
                    }
                    List<String> batch = new ArrayList<>();
                    collect(batch, first);
                    // 잠잠해질 때까지 모은다 — 대량 변경을 한 묶음으로
                    while (running && batch.size() < BATCH_MAX) {
                        PGNotification[] more = pg.getNotifications(QUIET_MS);
                        if (more == null || more.length == 0) {
                            break;
                        }
                        collect(batch, more);
                    }
                    dispatch(batch);
                }
            } catch (Exception e) {
                if (!running) {
                    return;
                }
                log.warn("DB 변경 알림 연결 끊김 — {}ms 뒤 재시도: {}", RETRY_MS, e.toString());
                try {
                    Thread.sleep(RETRY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void checkOwnName(Statement statement) throws java.sql.SQLException {
        try (ResultSet rs = statement.executeQuery("SELECT current_setting('application_name', true)")) {
            String name = rs.next() ? rs.getString(1) : null;
            boolean ok = IntegrityChangeTrigger.APP_NAME.equals(name);
            handler.setOwnNameApplied(ok);
            if (!ok) {
                log.error("앱 연결의 application_name 이 '{}' 가 아니라 '{}' — 앱 자신의 저장을 직접 수정과 구분할 수 없어 audit_log INSERT 알림은 무시한다",
                        IntegrityChangeTrigger.APP_NAME, name);
            }
        }
    }

    private static void collect(List<String> batch, PGNotification[] notifications) {
        for (PGNotification n : notifications) {
            batch.add(n.getParameter());
        }
    }

    private void dispatch(List<String> payloads) {
        try {
            handler.handleBatch(payloads);
        } catch (RuntimeException e) {
            log.error("DB 변경 알림 처리 실패: {}건", payloads.size(), e);
        }
    }
}
