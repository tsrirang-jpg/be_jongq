package com.example.jongq.booking;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@EnableScheduling
public class BookingEvents {
    private final Set<SseEmitter> clients = ConcurrentHashMap.newKeySet();
    public SseEmitter connect() {
        var emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> { clients.remove(emitter); emitter.complete(); });
        emitter.onError(error -> clients.remove(emitter));
        send(emitter, SseEmitter.event().name("connected").reconnectTime(5000L).data("{}"));
        return emitter;
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void changed(BookingChanged change) {
        for (var client : clients) send(client, SseEmitter.event().name("booking-changed").data(change));
    }
    // Keep the HTTP stream alive; no database read or client reload is triggered.
    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        for (var client : clients) send(client, SseEmitter.event().comment("keep-alive"));
    }
    private void send(SseEmitter client, SseEmitter.SseEventBuilder event) {
        try { client.send(event); }
        catch (IOException | IllegalStateException error) {
            clients.remove(client);
            try { client.complete(); } catch (IllegalStateException ignored) { }
        }
    }
    @PreDestroy public void close() {
        for (var client : clients) client.complete();
        clients.clear();
    }
}