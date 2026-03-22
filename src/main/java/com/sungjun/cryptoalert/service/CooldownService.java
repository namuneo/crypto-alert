package com.sungjun.cryptoalert.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 쿨다운 관리 서비스
 * - 동일 코인 + 동일 조건에 대한 중복 알림 방지
 */
@Slf4j
@Service
public class CooldownService {

    @Value("${cooldown.alignment-warning:4h}")
    private String alignmentWarningCooldown;

    @Value("${cooldown.entry-important:4h}")
    private String entryImportantCooldown;

    @Value("${cooldown.entry-confirm:1h}")
    private String entryConfirmCooldown;

    // 쿨다운 맵: key -> last alert timestamp
    private final Map<String, Long> cooldownMap = new ConcurrentHashMap<>();

    /**
     * 알림 가능 여부 확인 및 쿨다운 갱신
     *
     * @param symbol 코인 심볼
     * @param alertType 알림 타입 (ALIGNMENT_WARNING, ENTRY_IMPORTANT, ENTRY_CONFIRM)
     * @param direction 방향 (BULLISH, BEARISH, LONG, SHORT 등)
     * @return 알림 가능 여부
     */
    public boolean canAlert(String symbol, AlertType alertType, String direction) {
        String key = buildKey(symbol, alertType, direction);
        long now = System.currentTimeMillis();
        Long lastAlert = cooldownMap.get(key);

        if (lastAlert == null) {
            // 첫 알림
            cooldownMap.put(key, now);
            log.info("First alert for {}", key);
            return true;
        }

        long elapsedMs = now - lastAlert;
        long cooldownMs = getCooldownMs(alertType);

        if (elapsedMs >= cooldownMs) {
            // 쿨다운 경과
            cooldownMap.put(key, now);
            log.info("Cooldown expired for {} (elapsed: {}ms)", key, elapsedMs);
            return true;
        }

        // 쿨다운 중
        long remainingMs = cooldownMs - elapsedMs;
        log.debug("Cooldown active for {} (remaining: {}ms)", key, remainingMs);
        return false;
    }

    /**
     * 쿨다운 키 생성
     * 형식: {symbol}:{alertType}:{direction}
     */
    private String buildKey(String symbol, AlertType alertType, String direction) {
        return String.format("%s:%s:%s", symbol, alertType, direction);
    }

    /**
     * 알림 타입별 쿨다운 시간 (밀리초)
     */
    private long getCooldownMs(AlertType alertType) {
        return switch (alertType) {
            case ALIGNMENT_WARNING -> parseDuration(alignmentWarningCooldown);
            case ENTRY_IMPORTANT -> parseDuration(entryImportantCooldown);
            case ENTRY_CONFIRM -> parseDuration(entryConfirmCooldown);
        };
    }

    /**
     * Duration 문자열 파싱 (예: "4h", "1h")
     */
    private long parseDuration(String durationStr) {
        try {
            if (durationStr.endsWith("h")) {
                int hours = Integer.parseInt(durationStr.substring(0, durationStr.length() - 1));
                return Duration.ofHours(hours).toMillis();
            } else if (durationStr.endsWith("m")) {
                int minutes = Integer.parseInt(durationStr.substring(0, durationStr.length() - 1));
                return Duration.ofMinutes(minutes).toMillis();
            }
        } catch (NumberFormatException e) {
            log.error("Invalid duration format: {}", durationStr);
        }
        return Duration.ofHours(4).toMillis(); // 기본값: 4시간
    }

    /**
     * 특정 키의 쿨다운 초기화
     */
    public void resetCooldown(String symbol, AlertType alertType, String direction) {
        String key = buildKey(symbol, alertType, direction);
        cooldownMap.remove(key);
        log.info("Cooldown reset for {}", key);
    }

    /**
     * 전체 쿨다운 초기화
     */
    public void resetAllCooldowns() {
        cooldownMap.clear();
        log.info("All cooldowns reset");
    }

    /**
     * 알림 타입 열거형
     */
    public enum AlertType {
        ALIGNMENT_WARNING,  // 정배열/역배열 주의
        ENTRY_IMPORTANT,    // 중요 알림
        ENTRY_CONFIRM       // 확인 알림
    }
}
