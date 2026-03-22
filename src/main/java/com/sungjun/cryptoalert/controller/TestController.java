package com.sungjun.cryptoalert.controller;

import com.sungjun.cryptoalert.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Phase 1 테스트용 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final MarketDataService marketDataService;

    /**
     * 특정 코인의 EMA 계산 테스트
     *
     * 사용법: GET /api/test/ema/BTC
     */
    @GetMapping("/ema/{symbol}")
    public Map<String, Object> testEMA(@PathVariable String symbol) {
        log.info("Testing EMA calculation for {}", symbol);

        // 캔들 데이터 조회 및 EMA 계산
        Map<Integer, BigDecimal> emas = marketDataService.calculateAllEMAs(symbol);
        BigDecimal currentPrice = marketDataService.getCurrentPrice(symbol);

        // 정배열/역배열 판단
        String alignment = determineAlignment(emas);

        return Map.of(
                "symbol", symbol,
                "currentPrice", currentPrice,
                "ema20", emas.getOrDefault(20, BigDecimal.ZERO),
                "ema80", emas.getOrDefault(80, BigDecimal.ZERO),
                "ema320", emas.getOrDefault(320, BigDecimal.ZERO),
                "ema1920", emas.getOrDefault(1920, BigDecimal.ZERO),
                "alignment", alignment
        );
    }

    /**
     * 정배열/역배열 판단
     */
    private String determineAlignment(Map<Integer, BigDecimal> emas) {
        BigDecimal ema20 = emas.get(20);
        BigDecimal ema80 = emas.get(80);
        BigDecimal ema320 = emas.get(320);
        BigDecimal ema1920 = emas.get(1920);

        if (ema20 == null || ema80 == null || ema320 == null || ema1920 == null) {
            return "N/A";
        }

        // 정배열: 20 > 80 > 320 > 1920
        if (ema20.compareTo(ema80) > 0 &&
            ema80.compareTo(ema320) > 0 &&
            ema320.compareTo(ema1920) > 0) {
            return "정배열 (Bullish)";
        }

        // 역배열: 20 < 80 < 320 < 1920
        if (ema20.compareTo(ema80) < 0 &&
            ema80.compareTo(ema320) < 0 &&
            ema320.compareTo(ema1920) < 0) {
            return "역배열 (Bearish)";
        }

        return "중립 (Neutral)";
    }

    /**
     * 헬스체크
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "OK", "message", "Phase 1 ready!");
    }
}
