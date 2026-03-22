package com.sungjun.cryptoalert.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sungjun.cryptoalert.model.Candle;
import com.sungjun.cryptoalert.model.websocket.OkxWsSubscribeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * OKX WebSocket 클라이언트
 * - 실시간 15분봉 캔들 데이터 수신
 */
@Slf4j
@Component
public class OkxWebSocketClient extends TextWebSocketHandler {

    @Value("${okx.websocket.url:wss://ws.okx.com:8443/ws/v5/business}")
    private String wsUrl;

    @Value("${alert.targets}")
    private String targets;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StandardWebSocketClient wsClient = new StandardWebSocketClient();
    private WebSocketSession session;

    // 캔들 데이터 수신 콜백
    private final Map<String, Consumer<Candle>> candleListeners = new ConcurrentHashMap<>();

    /**
     * 애플리케이션 시작 시 WebSocket 연결 및 구독
     */
    @PostConstruct
    public void connect() {
        new Thread(() -> {
            try {
                log.info("Connecting to OKX WebSocket: {}", wsUrl);
                session = wsClient.execute(this, null, URI.create(wsUrl)).get();
                log.info("Connected to OKX WebSocket successfully");

                // 구독 시작 (약간의 대기 후)
                Thread.sleep(1000);
                subscribeToCandles();

            } catch (Exception e) {
                log.error("Failed to connect to OKX WebSocket: {}", e.getMessage(), e);
            }
        }).start();
    }

    /**
     * 애플리케이션 종료 시 연결 해제
     */
    @PreDestroy
    public void disconnect() {
        try {
            if (session != null && session.isOpen()) {
                session.close();
                log.info("Disconnected from OKX WebSocket");
            }
        } catch (Exception e) {
            log.error("Error closing WebSocket: {}", e.getMessage());
        }
    }

    /**
     * 15분봉 캔들 구독
     */
    private void subscribeToCandles() {
        List<String> symbols = List.of(targets.split(","));
        List<OkxWsSubscribeRequest.SubscribeArg> args = new ArrayList<>();

        for (String symbol : symbols) {
            String instId = symbol.trim() + "-USDT-SWAP";
            args.add(OkxWsSubscribeRequest.SubscribeArg.builder()
                    .channel("candle15m")
                    .instId(instId)
                    .build());
        }

        OkxWsSubscribeRequest request = OkxWsSubscribeRequest.builder()
                .op("subscribe")
                .args(args)
                .build();

        try {
            String payload = objectMapper.writeValueAsString(request);
            session.sendMessage(new TextMessage(payload));
            log.info("Subscribed to {} candle channels", args.size());
        } catch (Exception e) {
            log.error("Failed to subscribe to candles: {}", e.getMessage(), e);
        }
    }

    /**
     * WebSocket 메시지 수신 처리
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            JsonNode root = objectMapper.readTree(payload);

            // event 필드 확인 (구독 응답, 에러 등)
            if (root.has("event")) {
                String event = root.get("event").asText();
                if ("error".equals(event)) {
                    log.error("WebSocket error: {}", payload);
                } else if ("subscribe".equals(event)) {
                    log.info("Subscription confirmed: {}", payload);
                }
                return;
            }

            // 캔들 데이터 파싱
            if (root.has("arg") && root.has("data")) {
                JsonNode arg = root.get("arg");
                JsonNode data = root.get("data");

                if (arg.has("channel") && arg.has("instId")) {
                    String channel = arg.get("channel").asText();
                    String instId = arg.get("instId").asText();

                    if ("candle15m".equals(channel) && data.isArray() && data.size() > 0) {
                        JsonNode candleData = data.get(0);
                        Candle candle = parseCandle(candleData);

                        // 심볼 추출 (예: BTC-USDT-SWAP -> BTC)
                        String symbol = instId.split("-")[0];

                        log.debug("Received candle for {}: price={}, volume={}",
                                symbol, candle.getClose(), candle.getVolume());

                        // 리스너에게 전달
                        Consumer<Candle> listener = candleListeners.get(symbol);
                        if (listener != null) {
                            listener.accept(candle);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error handling WebSocket message: {}", e.getMessage(), e);
        }
    }

    /**
     * JSON 캔들 데이터를 Candle 객체로 변환
     * Data format: [timestamp, open, high, low, close, volume, volumeCcy, volumeCcyQuote, confirm]
     */
    private Candle parseCandle(JsonNode data) throws JsonProcessingException {
        if (!data.isArray() || data.size() < 6) {
            throw new JsonProcessingException("Invalid candle data format") {};
        }

        return Candle.builder()
                .timestamp(data.get(0).asLong())
                .open(new BigDecimal(data.get(1).asText()))
                .high(new BigDecimal(data.get(2).asText()))
                .low(new BigDecimal(data.get(3).asText()))
                .close(new BigDecimal(data.get(4).asText()))
                .volume(new BigDecimal(data.get(5).asText()))
                .build();
    }

    /**
     * 연결 종료 처리
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("WebSocket connection closed: {}", status);

        // 자동 재연결 (5초 후)
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                log.info("Attempting to reconnect...");
                connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 연결 에러 처리
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: {}", exception.getMessage(), exception);
    }

    /**
     * 캔들 데이터 수신 리스너 등록
     */
    public void addCandleListener(String symbol, Consumer<Candle> listener) {
        candleListeners.put(symbol, listener);
        log.info("Candle listener registered for {}", symbol);
    }

    /**
     * 캔들 데이터 수신 리스너 제거
     */
    public void removeCandleListener(String symbol) {
        candleListeners.remove(symbol);
        log.info("Candle listener removed for {}", symbol);
    }

    /**
     * WebSocket 연결 상태 확인
     */
    public boolean isConnected() {
        return session != null && session.isOpen();
    }
}
