package com.app.filecloud.service;

import com.app.filecloud.dto.ScanProgressDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ScanProgressService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        // 1. Dọn dẹp cái cũ
        SseEmitter old = emitters.remove(userId);
        if (old != null) {
            old.complete();
        }

        // 2. Tạo cái mới (30p)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        // Callbacks
        emitter.onCompletion(() -> {
            log.info("SSE Completed for user: {}", userId);
            emitters.remove(userId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE Timeout for user: {}", userId);
            emitter.complete();
            emitters.remove(userId);
        });
        emitter.onError((e) -> {
            log.error("SSE Error for user {}: {}", userId, e.getMessage());
            emitter.complete();
            emitters.remove(userId);
        });

        emitters.put(userId, emitter);
        log.info("SSE Subscribed: {} (Total active: {})", userId, emitters.size());

        // 3. Gửi Init Event ngay lập tức
        try {
            emitter.send(SseEmitter.event().name("init").data("Connected"));
        } catch (IOException e) {
            log.error("Failed to send INIT event", e);
        }

        return emitter;
    }

    public void sendProgress(String userId, ScanProgressDto progress) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            synchronized (emitter) {
                try {
                    emitter.send(SseEmitter.event().name("progress").data(progress));
                    // Uncomment dòng dưới nếu muốn debug từng gói tin (sẽ spam log)
                    // log.debug("Sent progress to {}", userId);
                } catch (Exception e) {
                    log.error("Failed to send progress to {}: {}", userId, e.getMessage());
                    // KHÔNG XÓA EMITTER Ở ĐÂY, để các lần gửi sau còn cơ hội
                }
            }
        } else {
            // QUAN TRỌNG: Log này sẽ cho biết nếu UserId bị sai hoặc Emitter đã mất
            log.warn("Emitter not found for UserID: {} (Map keys: {})", userId, emitters.keySet());
        }
    }
}
