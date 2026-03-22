package com.sungjun.cryptoalert.service;

import com.sungjun.cryptoalert.client.OkxRestClient;
import com.sungjun.cryptoalert.model.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 시장 데이터 서비스
 * - 캔들 데이터 캐싱
 * - EMA(20, 80, 320, 1920) 계산
 * - ATR(20) 계산
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final OkxRestClient okxRestClient;

    // 캔들 데이터 캐시: symbol -> List<Candle>
    private final Map<String, List<Candle>> candleCache = new ConcurrentHashMap<>();

    // 캐시 갱신 시간: symbol -> timestamp
    private final Map<String, Long> cacheTimestamp = new ConcurrentHashMap<>();

    // EMA 기간 설정
    private static final int[] EMA_PERIODS = {20, 80, 320, 1920};
    private static final int MAX_CANDLES_NEEDED = 2500; // 1920 EMA 계산을 위한 충분한 데이터
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5분 캐시 유효 시간

    /**
     * 특정 코인의 캔들 데이터를 조회하고 캐시에 저장
     */
    public List<Candle> fetchAndCacheCandles(String symbol) {
        // SWAP = Perpetual Futures (무기한 선물)
        String instId = symbol + "-USDT-SWAP";

        // 캐시 확인 - TTL 체크
        Long lastUpdate = cacheTimestamp.get(symbol);
        long now = System.currentTimeMillis();

        if (lastUpdate != null && (now - lastUpdate) < CACHE_TTL_MS) {
            List<Candle> cached = candleCache.get(symbol);
            if (cached != null && !cached.isEmpty()) {
                log.info("Using cached data for {} ({} candles, age: {}s)",
                        symbol, cached.size(), (now - lastUpdate) / 1000);
                return cached;
            }
        }

        log.info("Fetching fresh {} candles for {}", MAX_CANDLES_NEEDED, symbol);

        // OkxRestClient가 내부적으로 여러 배치를 처리
        List<Candle> candles = okxRestClient.getCandles(instId, MAX_CANDLES_NEEDED);

        if (candles.isEmpty()) {
            log.warn("No candles fetched for {}", symbol);
            return Collections.emptyList();
        }

        // 캐시에 저장 및 타임스탬프 업데이트
        candleCache.put(symbol, candles);
        cacheTimestamp.put(symbol, now);

        log.info("Cached {} candles for {}", candles.size(), symbol);
        return candles;
    }

    /**
     * EMA 계산 (TA4j 라이브러리 사용)
     *
     * @param candles 캔들 리스트 (시간순 정렬 필수)
     * @param period EMA 기간
     * @return EMA 값 (최신 값)
     */
    public BigDecimal calculateEMA(List<Candle> candles, int period) {
        if (candles.size() < period) {
            log.warn("Not enough candles ({}) for EMA({})", candles.size(), period);
            return BigDecimal.ZERO;
        }

        try {
            // TA4j BarSeries 생성
            BarSeries series = new BaseBarSeriesBuilder()
                    .withName("Market-Data")
                    .withNumTypeOf(DecimalNum.class)
                    .build();

            // 중복 타임스탬프 제거: 같은 타임스탬프가 있으면 건너뛰기
            Long previousTimestamp = null;
            int skippedCount = 0;

            for (Candle candle : candles) {
                // 이전 캔들과 동일한 타임스탬프면 건너뛰기
                if (previousTimestamp != null && candle.getTimestamp().equals(previousTimestamp)) {
                    skippedCount++;
                    continue;
                }

                ZonedDateTime time = ZonedDateTime.ofInstant(
                        candle.getInstant(),
                        ZoneId.systemDefault()
                );

                series.addBar(
                        time,
                        candle.getOpen().doubleValue(),
                        candle.getHigh().doubleValue(),
                        candle.getLow().doubleValue(),
                        candle.getClose().doubleValue(),
                        candle.getVolume().doubleValue()
                );

                previousTimestamp = candle.getTimestamp();
            }

            if (skippedCount > 0) {
                log.debug("Skipped {} duplicate timestamp candles", skippedCount);
            }

            // 중복 제거 후 데이터가 부족한 경우 체크
            if (series.getBarCount() < period) {
                log.warn("Not enough unique candles ({}) for EMA({}) after deduplication",
                        series.getBarCount(), period);
                return BigDecimal.ZERO;
            }

            // EMA 계산
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            EMAIndicator emaIndicator = new EMAIndicator(closePrice, period);

            // 최신 EMA 값 반환
            int lastIndex = series.getEndIndex();
            double emaValue = emaIndicator.getValue(lastIndex).doubleValue();

            return BigDecimal.valueOf(emaValue).setScale(2, RoundingMode.HALF_UP);

        } catch (Exception e) {
            log.error("Error calculating EMA({}): {}", period, e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 모든 EMA 계산 (20, 80, 320, 1920)
     *
     * @param symbol 코인 심볼
     * @return Map<Period, EMA Value>
     */
    public Map<Integer, BigDecimal> calculateAllEMAs(String symbol) {
        List<Candle> candles = candleCache.get(symbol);

        if (candles == null || candles.isEmpty()) {
            log.warn("No cached candles for {}, fetching...", symbol);
            candles = fetchAndCacheCandles(symbol);
        }

        Map<Integer, BigDecimal> emas = new LinkedHashMap<>();

        for (int period : EMA_PERIODS) {
            BigDecimal ema;

            // EMA(1920)은 일봉 EMA(20)으로 대체 (15분봉 데이터 부족 문제 해결)
            if (period == 1920) {
                ema = calculateEMAFromDailyCandles(symbol, 20);
            } else {
                ema = calculateEMA(candles, period);
            }

            emas.put(period, ema);
            log.info("{} EMA({}): {}", symbol, period, ema);
        }

        return emas;
    }

    /**
     * 일봉 데이터로 EMA 계산 (15분봉 EMA(1920) 대체용)
     *
     * @param symbol 코인 심볼
     * @param period EMA 기간 (일봉 기준)
     * @return EMA 값
     */
    private BigDecimal calculateEMAFromDailyCandles(String symbol, int period) {
        String instId = symbol + "-USDT-SWAP";

        // 일봉 데이터 조회 (충분한 여유를 두고 50개)
        List<Candle> dailyCandles = okxRestClient.getCandles(instId, "1D", 50);

        if (dailyCandles.isEmpty()) {
            log.warn("No daily candles fetched for {}", symbol);
            return BigDecimal.ZERO;
        }

        return calculateEMA(dailyCandles, period);
    }

    /**
     * ATR(Average True Range) 계산
     */
    public BigDecimal calculateATR(List<Candle> candles, int period) {
        if (candles.size() < period + 1) {
            log.warn("Not enough candles ({}) for ATR({})", candles.size(), period);
            return BigDecimal.ZERO;
        }

        List<BigDecimal> trueRanges = new ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {
            Candle current = candles.get(i);
            Candle previous = candles.get(i - 1);

            BigDecimal tr1 = current.getHigh().subtract(current.getLow());
            BigDecimal tr2 = current.getHigh().subtract(previous.getClose()).abs();
            BigDecimal tr3 = current.getLow().subtract(previous.getClose()).abs();

            BigDecimal trueRange = tr1.max(tr2).max(tr3);
            trueRanges.add(trueRange);
        }

        // ATR은 True Range의 EMA
        BigDecimal sum = trueRanges.subList(0, period).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal atr = sum.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);

        // 나머지에 대해 EMA 적용
        BigDecimal k = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(period + 1), 10, RoundingMode.HALF_UP);

        for (int i = period; i < trueRanges.size(); i++) {
            atr = trueRanges.get(i).multiply(k)
                    .add(atr.multiply(BigDecimal.ONE.subtract(k)));
        }

        return atr.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 현재 가격 조회 (최신 캔들의 종가)
     */
    public BigDecimal getCurrentPrice(String symbol) {
        List<Candle> candles = candleCache.get(symbol);
        if (candles == null || candles.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return candles.get(candles.size() - 1).getClose();
    }
}
