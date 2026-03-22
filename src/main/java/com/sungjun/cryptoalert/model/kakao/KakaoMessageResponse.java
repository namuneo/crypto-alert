package com.sungjun.cryptoalert.model.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 카카오 메시지 전송 응답
 */
@Data
public class KakaoMessageResponse {

    @JsonProperty("result_code")
    private Integer resultCode;

    @JsonProperty("error")
    private String error;

    @JsonProperty("error_description")
    private String errorDescription;
}
