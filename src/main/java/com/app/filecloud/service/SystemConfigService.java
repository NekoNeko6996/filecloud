package com.app.filecloud.service;

import com.app.filecloud.entity.SysConfig;
import com.app.filecloud.repository.SysConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SysConfigRepository configRepository;

    // Cache config on RAM
    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    // 1. Load all config on RAM
    @PostConstruct
    public void init() {
        refreshCache();
    }

    public void refreshCache() {
        log.info("Loading system configurations from Database...");
        configRepository.findAll().forEach(cfg -> {
            configCache.put(cfg.getKey(), cfg.getValue() != null ? cfg.getValue() : "");
        });
        log.info("Loaded {} configurations.", configCache.size());
    }

    // 2.Get Config (Getters)
    public String get(String key) {
        return configCache.getOrDefault(key, null);
    }

    public String get(String key, String defaultValue) {
        return configCache.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        try {
            String val = configCache.get(key);
            return val != null ? Integer.parseInt(val) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = configCache.get(key);
        return val != null ? Boolean.parseBoolean(val) : defaultValue;
    }

    // 3. Update Config (Update) -> Save DB & Update Cache
    public void updateConfig(String key, String value) {
        SysConfig config = configRepository.findById(key)
                .orElseThrow(() -> new RuntimeException("Config not found: " + key));

        config.setValue(value);
        configRepository.save(config);

        // Update cache Instance (no need restart)
        configCache.put(key, value);
        log.info("Updated config: {} = {}", key, value);
    }
}