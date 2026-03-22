package com.sungjun.cryptoalert.service;

import com.sungjun.cryptoalert.client.KakaoClient;
import com.sungjun.cryptoalert.model.AlertHistory;
import com.sungjun.cryptoalert.model.Candle;
import com.sungjun.cryptoalert.repository.AlertHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Strategy Engine
 * - 실시간 캔들 데이터 분석
 * - 알림 조건 평가 및 발송
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyEngine {

    private final MarketDataService marketDataService;
    private final AlignmentService alignmentService;
    private final CooldownService cooldownService;
    private final KakaoClient kakaoClient;
    private final AlertHistoryRepository alertHistoryRepository;

    private static final BigDecimal ALIGNMENT_WARNING_GAP_THRESHOLD = new BigDecimal("0.008"); // 0.8%
    private static final BigDecimal ENTRY_THRESHOLD = new BigDecimal("0.01"); // 1%

    /**
     * 새로운 캔들 데이터 처리
     * - Alignment Warning 체크
     * - Entry Alert 체크
     */
    public void processCandle(String symbol, Candle candle) {
        try {
            log.debug("Processing candle for {}: close={}", symbol, candle.getClose());

            // 캔들 데이터 갱신 및 EMA 계산
            marketDataService.fetchAndCacheCandles(symbol);
            Map<Integer, BigDecimal> emas = marketDataService.calculateAllEMAs(symbol);

            if (emas.isEmpty() || emas.values().stream().anyMatch(v -> v.compareTo(BigDecimal.ZERO) == 0)) {
                log.warn("EMA calculation failed for {}", symbol);
                return;
            }

            BigDecimal currentPrice = candle.getClose();

            // 1. Alignment Warning 체크
            checkAlignmentWarning(symbol, emas);

            // 2. Entry Alert 체크
            checkEntryAlert(symbol, emas, currentPrice);

        } catch (Exception e) {
            log.error("Error processing candle for {}: {}", symbol, e.getMessage(), e);
        }
    }

    /**
     * Alignment Warning 체크
     * - 정배열 주의: 320EMA < 1920EMA && gap ≤ 0.8%
     * - 역배열 주의: 320EMA > 1920EMA && gap ≤ 0.8%
     */
    private void checkAlignmentWarning(String symbol, Map<Integer, BigDecimal> emas) {
        BigDecimal ema320 = emas.get(320);
        BigDecimal ema1920 = emas.get(1920);

        if (ema320 == null || ema1920 == null) {
            return;
        }

        // Gap 계산: |320EMA - 1920EMA| / 1920EMA
        BigDecimal gap = ema320.subtract(ema1920).abs()
                .divide(ema1920, 10, RoundingMode.HALF_UP);

        // Gap이 임계값 이하인 경우에만 체크
        if (gap.compareTo(ALIGNMENT_WARNING_GAP_THRESHOLD) > 0) {
            return;
        }

        // 정배열 주의: 320이 아래에서 1920에 접근
        if (ema320.compareTo(ema1920) < 0) {
            if (cooldownService.canAlert(symbol, CooldownService.AlertType.ALIGNMENT_WARNING, "BULLISH")) {
                sendAlignmentWarningAlert(symbol, ema320, ema1920, gap, true);
            }
        }
        // 역배열 주의: 320이 위에서 1920에 접근
        else if (ema320.compareTo(ema1920) > 0) {
            if (cooldownService.canAlert(symbol, CooldownService.AlertType.ALIGNMENT_WARNING, "BEARISH")) {
                sendAlignmentWarningAlert(symbol, ema320, ema1920, gap, false);
            }
        }
    }

    /**
     * Entry Alert 체크
     * - Long: 320EMA > 1920EMA 일 때
     *   - 중요: price >= 320EMA × 1.01
     *   - 확인: price >= 80EMA × 1.01
     * - Short: 320EMA < 1920EMA 일 때
     *   - 중요: price <= 320EMA × 0.99
     *   - 확인: price <= 80EMA × 0.99
     */
    private void checkEntryAlert(String symbol, Map<Integer, BigDecimal> emas, BigDecimal currentPrice) {
        BigDecimal ema80 = emas.get(80);
        BigDecimal ema320 = emas.get(320);
        BigDecimal ema1920 = emas.get(1920);

        if (ema80 == null || ema320 == null || ema1920 == null) {
            return;
        }

        // Long Position (320EMA > 1920EMA)
        if (ema320.compareTo(ema1920) > 0) {
            // 중요 알림: price >= 320EMA × 1.01
            BigDecimal importantThreshold = ema320.multiply(BigDecimal.ONE.add(ENTRY_THRESHOLD));
            if (currentPrice.compareTo(importantThreshold) >= 0) {
                if (cooldownService.canAlert(symbol, CooldownService.AlertType.ENTRY_IMPORTANT, "LONG")) {
                    sendEntryAlert(symbol, currentPrice, ema320, "LONG", true);
                }
            }

            // 확인 알림: price >= 80EMA × 1.01
            BigDecimal confirmThreshold = ema80.multiply(BigDecimal.ONE.add(ENTRY_THRESHOLD));
            if (currentPrice.compareTo(confirmThreshold) >= 0) {
                if (cooldownService.canAlert(symbol, CooldownService.AlertType.ENTRY_CONFIRM, "LONG")) {
                    sendEntryAlert(symbol, currentPrice, ema80, "LONG", false);
                }
            }
        }
        // Short Position (320EMA < 1920EMA)
        else if (ema320.compareTo(ema1920) < 0) {
            // 중요 알림: price <= 320EMA × 0.99
            BigDecimal importantThreshold = ema320.multiply(BigDecimal.ONE.subtract(ENTRY_THRESHOLD));
            if (currentPrice.compareTo(importantThreshold) <= 0) {
                if (cooldownService.canAlert(symbol, CooldownService.AlertType.ENTRY_IMPORTANT, "SHORT")) {
                    sendEntryAlert(symbol, currentPrice, ema320, "SHORT", true);
                }
            }

            // 확인 알림: price <= 80EMA × 0.99
            BigDecimal confirmThreshold = ema80.multiply(BigDecimal.ONE.subtract(ENTRY_THRESHOLD));
            if (currentPrice.compareTo(confirmThreshold) <= 0) {
                if (cooldownService.canAlert(symbol, CooldownService.AlertType.ENTRY_CONFIRM, "SHORT")) {
                    sendEntryAlert(symbol, currentPrice, ema80, "SHORT", false);
                }
            }
        }
    }

    /**
     * Alignment Warning 알림 전송
     */
    private void sendAlignmentWarningAlert(String symbol, BigDecimal ema320, BigDecimal ema1920,
                                          BigDecimal gap, boolean bullish) {
        String gapPercent = gap.multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP) + "%";

        String message = String.format(
                "⚠️ [%s 주의] %s\n320EMA: %s | 1920EMA: %s\nGap: %s — %s",
                bullish ? "정배열" : "역배열",
                symbol,
                formatPrice(ema320),
                formatPrice(ema1920),
                gapPercent,
                bullish ? "돌파 시 정배열 완성" : "이탈 시 역배열 완성"
        );

        kakaoClient.sendMessageToMe(message);
        log.info("Sent alignment warning: {} - {}", symbol, bullish ? "BULLISH" : "BEARISH");

        // 알림 이력 저장
        saveAlertHistory(symbol, "ALIGNMENT_WARNING", bullish ? "BULLISH" : "BEARISH", message);
    }

    /**
     * Entry Alert 알림 전송
     */
    private void sendEntryAlert(String symbol, BigDecimal price, BigDecimal emaValue,
                               String direction, boolean important) {
        String emoji = important ? "🔴" : "🟡";
        String level = important ? "중요" : "확인";
        String emaLabel = important ? "320EMA" : "80EMA";

        BigDecimal diff = price.subtract(emaValue).divide(emaValue, 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        String message = String.format(
                "%s [%s — %s] %s\nPrice: %s | %s: %s\nPrice는 %s 대비 %+.2f%% 위치",
                emoji, level, direction, symbol,
                formatPrice(price),
                emaLabel,
                formatPrice(emaValue),
                emaLabel,
                diff.doubleValue()
        );

        kakaoClient.sendMessageToMe(message);
        log.info("Sent entry alert: {} - {} - {}", symbol, direction, level);

        // 알림 이력 저장
        String alertType = important ? "ENTRY_IMPORTANT" : "ENTRY_CONFIRM";
        saveAlertHistory(symbol, alertType, direction, message);
    }

    /**
     * 알림 이력 저장
     */
    private void saveAlertHistory(String symbol, String alertType, String direction, String message) {
        try {
            AlertHistory history = AlertHistory.builder()
                    .symbol(symbol)
                    .alertType(alertType)
                    .direction(direction)
                    .message(message)
                    .build();

            alertHistoryRepository.save(history);
            log.debug("Saved alert history for {} - {}", symbol, alertType);
        } catch (Exception e) {
            log.error("Failed to save alert history: {}", e.getMessage());
        }
    }

    /**
     * 가격 포맷팅 (천 단위 콤마)
     */
    private String formatPrice(BigDecimal price) {
        return String.format("%,.2f", price.setScale(2, RoundingMode.HALF_UP));
    }
}
