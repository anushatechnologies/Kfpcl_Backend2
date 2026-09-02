package com.project.Anusha.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Service
public class AdminDashboardStreamService {

    private final AdminDashboardLiveService adminDashboardLiveService;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService scheduler;

    public AdminDashboardStreamService(AdminDashboardLiveService adminDashboardLiveService) {
        this.adminDashboardLiveService = adminDashboardLiveService;
    }

    @PostConstruct
    void start() {
        ThreadFactory tf = runnable -> {
            Thread thread = new Thread(runnable, "admin-dashboard-sse");
            thread.setDaemon(true);
            return thread;
        };
        scheduler = Executors.newSingleThreadScheduledExecutor(tf);
        scheduler.scheduleWithFixedDelay(this::broadcast, 0, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public SseEmitter connect(int activeWindowMinutes) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ignored -> emitters.remove(emitter));

        try {
            Map<String, Object> snapshot = adminDashboardLiveService.getLiveSnapshot(activeWindowMinutes);
            emitter.send(SseEmitter.event()
                    .name("dashboard")
                    .data(snapshot, MediaType.APPLICATION_JSON));
        } catch (IOException ignored) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    private void broadcast() {
        if (emitters.isEmpty()) {
            return;
        }

        Map<String, Object> snapshot = adminDashboardLiveService.getLiveSnapshot(15);
        snapshot.put("heartbeatAt", LocalDateTime.now());

        for (Iterator<SseEmitter> iterator = emitters.iterator(); iterator.hasNext(); ) {
            SseEmitter emitter = iterator.next();
            try {
                emitter.send(SseEmitter.event()
                        .name("dashboard")
                        .data(snapshot, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}

