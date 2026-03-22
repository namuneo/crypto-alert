package com.sungjun.cryptoalert.model.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OKX WebSocket 구독 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OkxWsSubscribeRequest {
    private String op; // "subscribe" or "unsubscribe"
    private List<SubscribeArg> args;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeArg {
        private String channel; // "candle15m"
        private String instId;  // "BTC-USDT-SWAP"
    }
}
