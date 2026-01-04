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

    // Lưu trữ Emitter theo UserId để gửi đúng người
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        // Timeout 30 phút cho quá trình scan dài
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError((e) -> emitters.remove(userId));

        emitters.put(userId, emitter);
        return emitter;
    }

    public void sendProgress(String userId, ScanProgressDto progress) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(progress));
            } catch (Exception e) { // <--- SỬA: Catch Exception thay vì chỉ IOException
                // Nếu gửi lỗi, xóa emitter để tránh lỗi các lần sau
                emitters.remove(userId);
                // Log warning thôi, không throw exception để luồng chính tiếp tục chạy
                log.warn("Không thể gửi SSE tới user {}: {}", userId, e.getMessage());
            }
        }
    }
}
