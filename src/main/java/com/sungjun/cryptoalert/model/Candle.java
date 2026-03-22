package com.sungjun.cryptoalert.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * OKX 캔들스틱 데이터 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candle {

    /**
     * 캔들 시작 타임스탬프 (밀리초)
     */
    private Long timestamp;

    /**
     * 시가
     */
    private BigDecimal open;

    /**
     * 고가
     */
    private BigDecimal high;

    /**
     * 저가
     */
    private BigDecimal low;

    /**
     * 종가
     */
    private BigDecimal close;

    /**
     * 거래량
     */
    private BigDecimal volume;

    /**
     * Instant 타임스탬프 반환
     */
    public Instant getInstant() {
        return Instant.ofEpochMilli(timestamp);
    }
}
