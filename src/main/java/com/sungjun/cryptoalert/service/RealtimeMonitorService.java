package com.sungjun.cryptoalert.service;

import com.sungjun.cryptoalert.client.OkxWebSocketClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 실시간 모니터링 서비스
 * - WebSocket으로 수신한 캔들 데이터를 Strategy Engine에 전달
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeMonitorService {

    private final OkxWebSocketClient webSocketClient;
    private final StrategyEngine strategyEngine;

    @Value("${alert.targets}")
    private String targets;

    /**
     * 애플리케이션 시작 시 모니터링 시작
     */
    @PostConstruct
    public void startMonitoring() {
        List<String> symbols = Arrays.asList(targets.split(","));

        for (String symbol : symbols) {
            String trimmedSymbol = symbol.trim();

            // WebSocket 캔들 리스너 등록
            webSocketClient.addCandleListener(trimmedSymbol, candle -> {
                log.debug("Received candle for {}: close={}", trimmedSymbol, candle.getClose());

                // Strategy Engine에 전달하여 알림 조건 평가
                strategyEngine.processCandle(trimmedSymbol, candle);
            });

            log.info("Started monitoring for {}", trimmedSymbol);
        }

        log.info("Realtime monitoring started for {} symbols", symbols.size());
    }
}
