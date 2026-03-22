package com.sungjun.cryptoalert.client;

import com.sungjun.cryptoalert.model.Candle;
import com.sungjun.cryptoalert.model.OkxCandleResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OKX REST API 클라이언트
 *
 * API 문서: https://www.okx.com/docs-v5/en/#rest-api-market-data-get-candlesticks
 */
@Slf4j
@Component
public class OkxRestClient {

    private final RestClient restClient;

    public OkxRestClient(@Value("${okx.rest.base-url:https://www.okx.com}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * 캔들 데이터 조회 (여러 번 호출하여 충분한 데이터 확보)
     *
     * @param instId 거래쌍 (예: BTC-USDT-SWAP)
     * @param bar 봉 타입 (15m, 1H, 4H, 1D 등)
     * @param limit 조회할 캔들 개수
     * @return 캔들 리스트 (시간순 정렬)
     */
    public List<Candle> getCandles(String instId, String bar, int limit) {
        try {
            log.info("Fetching {} candles for {}", limit, instId);

            List<Candle> allCandles = new java.util.ArrayList<>();
            int batchSize = 300; // OKX API 최대 제한
            int remainingCandles = limit;
            Long after = null; // 이 타임스탬프 이후(더 오래된) 데이터를 조회

            while (remainingCandles > 0) {
                int currentLimit = Math.min(remainingCandles, batchSize);

                List<Candle> batch = fetchCandleBatch(instId, bar, currentLimit, after);

                if (batch.isEmpty()) {
                    log.warn("No more candles available for {}", instId);
                    break;
                }

                allCandles.addAll(batch);
                remainingCandles -= batch.size();

                // 다음 배치를 위해 가장 오래된 캔들의 타임스탬프를 after로 설정
                // OKX API는 최신 데이터부터 반환하므로, 배치의 마지막(가장 오래된) 캔들의 타임스탬프 사용
                after = batch.get(batch.size() - 1).getTimestamp();

                log.info("Fetched batch: size={}, total={}, oldest_ts={}, next_after={}",
                        batch.size(), allCandles.size(),
                        after, after);

                // 배치 크기가 요청한 것보다 작으면 더 이상 데이터가 없음
                if (batch.size() < batchSize) {
                    log.info("Received fewer candles than requested - reached end of available data");
                    break;
                }

                // API 요청 제한을 위한 짧은 대기
                if (remainingCandles > 0) {
                    try {
                        Thread.sleep(100); // 100ms 대기
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            // 시간순 정렬 (오래된 것부터)
            allCandles.sort((c1, c2) -> Long.compare(c1.getTimestamp(), c2.getTimestamp()));

            log.info("Successfully fetched {} total candles for {}", allCandles.size(), instId);
            return allCandles;

        } catch (Exception e) {
            log.error("Error fetching candles for {}: {}", instId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 15분봉 캔들 데이터 조회 (편의 메서드)
     */
    public List<Candle> getCandles(String instId, int limit) {
        return getCandles(instId, "15m", limit);
    }

    /**
     * 캔들 데이터 한 배치 조회
     */
    private List<Candle> fetchCandleBatch(String instId, String bar, int limit, Long after) {
        try {
            OkxCandleResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder
                            .path("/api/v5/market/candles")
                            .queryParam("instId", instId)
                            .queryParam("bar", bar)
                            .queryParam("limit", limit);

                        // after 파라미터가 있으면 추가 (이 타임스탬프보다 오래된 데이터 조회)
                        if (after != null) {
                            uriBuilder.queryParam("after", after);
                        }

                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(OkxCandleResponse.class);

            if (response == null || !response.isSuccess()) {
                log.error("Failed to fetch candles for {}: {}", instId,
                         response != null ? response.getMsg() : "null response");
                return Collections.emptyList();
            }

            return response.getData().stream()
                    .map(this::parseCandle)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error fetching batch for {}: {}", instId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * OKX API 응답 데이터를 Candle 객체로 변환
     *
     * Data format: [timestamp, open, high, low, close, volume, volumeCcy, volumeCcyQuote, confirm]
     */
    private Candle parseCandle(List<String> data) {
        return Candle.builder()
                .timestamp(Long.parseLong(data.get(0)))
                .open(new BigDecimal(data.get(1)))
                .high(new BigDecimal(data.get(2)))
                .low(new BigDecimal(data.get(3)))
                .close(new BigDecimal(data.get(4)))
                .volume(new BigDecimal(data.get(5)))
                .build();
    }
}
