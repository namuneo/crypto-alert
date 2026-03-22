package com.sungjun.cryptoalert.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * EMA 정배열/역배열 판단 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlignmentService {

    /**
     * 정배열 여부 확인
     * 조건: 20EMA > 80EMA > 320EMA > 1920EMA
     */
    public boolean isBullishAlignment(Map<Integer, BigDecimal> emas) {
        BigDecimal ema20 = emas.get(20);
        BigDecimal ema80 = emas.get(80);
        BigDecimal ema320 = emas.get(320);
        BigDecimal ema1920 = emas.get(1920);

        if (ema20 == null || ema80 == null || ema320 == null || ema1920 == null) {
            return false;
        }

        // 0인 값이 있으면 정배열이 아님
        if (ema20.compareTo(BigDecimal.ZERO) == 0 ||
            ema80.compareTo(BigDecimal.ZERO) == 0 ||
            ema320.compareTo(BigDecimal.ZERO) == 0 ||
            ema1920.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        return ema20.compareTo(ema80) > 0 &&
               ema80.compareTo(ema320) > 0 &&
               ema320.compareTo(ema1920) > 0;
    }

    /**
     * 역배열 여부 확인
     * 조건: 20EMA < 80EMA < 320EMA < 1920EMA
     */
    public boolean isBearishAlignment(Map<Integer, BigDecimal> emas) {
        BigDecimal ema20 = emas.get(20);
        BigDecimal ema80 = emas.get(80);
        BigDecimal ema320 = emas.get(320);
        BigDecimal ema1920 = emas.get(1920);

        if (ema20 == null || ema80 == null || ema320 == null || ema1920 == null) {
            return false;
        }

        // 0인 값이 있으면 역배열이 아님
        if (ema20.compareTo(BigDecimal.ZERO) == 0 ||
            ema80.compareTo(BigDecimal.ZERO) == 0 ||
            ema320.compareTo(BigDecimal.ZERO) == 0 ||
            ema1920.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        return ema20.compareTo(ema80) < 0 &&
               ema80.compareTo(ema320) < 0 &&
               ema320.compareTo(ema1920) < 0;
    }

    /**
     * 배열 상태 문자열 반환
     */
    public String getAlignmentStatus(Map<Integer, BigDecimal> emas) {
        if (isBullishAlignment(emas)) {
            return "정배열 (Bullish)";
        } else if (isBearishAlignment(emas)) {
            return "역배열 (Bearish)";
        } else {
            return "중립 (Neutral)";
        }
    }
}
