package Marketly.Payment.Service.callbacks;

import Marketly.Payment.Service.config.AppProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class CallbackWorker {
    private static final Logger log = LoggerFactory.getLogger(CallbackWorker.class);

    private static final int BATCH = 10;
    private static final int LEASE_SECONDS = 30;
    private static final long MAX_BACKOFF_SECONDS = 60;

    private record Due(UUID id, String payload, int attempts) {}

    private final AppProps props;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public CallbackWorker(AppProps props, JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.props = props;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelay = 500)
    public void run() {
        try {
            for (Due d : claim()) {
                deliver(d);
            }
        } catch (Exception e) {
            log.error("Callback worker loop failed", e);
        }
    }


    private List<Due> claim() {
        List<Due> rows = tx.execute(status -> {
            List<Due> due = jdbc.query("""
                SELECT id, payload, attempts FROM callback_outbox
                WHERE delivered_at IS NULL AND next_attempt_time <= now()
                ORDER BY next_attempt_time
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                    (rs, i) -> new Due(rs.getObject("id", UUID.class), rs.getString("payload"), rs.getInt("attempts")),
                    BATCH);
            for (Due d : due) {
                jdbc.update("UPDATE callback_outbox SET next_attempt_time = now() + (? * interval '1 second') WHERE id = ?",
                        LEASE_SECONDS, d.id());
            }
            return due;
        });
        return rows == null ? List.of() : rows;
    }

    // The slow HTTP call happens outside any transaction
    private void deliver(Due d) {
        try {
            long ts = Instant.now().getEpochSecond();
            HttpRequest req = HttpRequest.newBuilder(URI.create(props.callbackUrl()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Marketly-Signature", "t=" + ts + ",v1=" + sign(ts, d.payload()))
                    .POST(HttpRequest.BodyPublishers.ofString(d.payload(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            int code = res.statusCode();
            if (code >= 200 && code < 300) {
                markDelivered(d);
            } else {
                markFailed(d, "HTTP " + code);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            markFailed(d, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void markDelivered(Due d) {
        jdbc.update("""
            UPDATE callback_outbox
            SET delivered_at = now(), attempts = attempts + 1, last_error = NULL
            WHERE id = ?
            """, d.id());
        log.info("Callback {} delivered after {} attempt(s)", d.id(), d.attempts() + 1);
    }

    private void markFailed(Due d, String error) {
        // 1s, 2s, 4s, 8s, 16s, 32s, then 60s forever.
        long delay = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(d.attempts(), 20));
        jdbc.update("""
            UPDATE callback_outbox
            SET attempts = attempts + 1,
                last_error = ?,
                next_attempt_time = now() + (? * interval '1 second')
            WHERE id = ?
            """, error, delay, d.id());
        log.warn("Callback {} failed ({}), attempt {}, retrying in {}s", d.id(), error, d.attempts() + 1, delay);
    }

    /** HMAC-SHA256 over "<timestamp>.<body>" with CALLBACK_SECRET, hex encoded. */
    private String sign(long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(props.callbackSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }
}