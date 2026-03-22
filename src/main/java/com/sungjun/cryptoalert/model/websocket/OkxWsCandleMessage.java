package com.sungjun.cryptoalert.model.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OKX WebSocket 캔들 메시지
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OkxWsCandleMessage {
    private String event;  // "subscribe", "error", etc.
    private String arg;    // JSON object as string
    private List<List<String>> data;  // 캔들 데이터

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandleArg {
        private String channel;
        private String instId;
    }
}
