package com.sungjun.cryptoalert.model.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 카카오 OAuth 토큰 응답
 */
@Data
public class KakaoTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("refresh_token_expires_in")
    private Integer refreshTokenExpiresIn;

    @JsonProperty("scope")
    private String scope;
}
