package com.sungjun.cryptoalert.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * OKX REST API 캔들 응답 모델
 *
 * Response format:
 * {
 *   "code": "0",
 *   "msg": "",
 *   "data": [
 *     ["timestamp", "open", "high", "low", "close", "volume", "volumeCcy", "volumeCcyQuote", "confirm"]
 *   ]
 * }
 */
@Data
public class OkxCandleResponse {

    @JsonProperty("code")
    private String code;

    @JsonProperty("msg")
    private String msg;

    @JsonProperty("data")
    private List<List<String>> data;

    /**
     * API 호출 성공 여부
     */
    public boolean isSuccess() {
        return "0".equals(code);
    }
}