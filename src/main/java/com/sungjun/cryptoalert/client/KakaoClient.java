package com.sungjun.cryptoalert.client;

import com.sungjun.cryptoalert.model.kakao.KakaoMessageResponse;
import com.sungjun.cryptoalert.model.kakao.KakaoTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 카카오 API 클라이언트
 * - 나에게 보내기 API 사용
 */
@Slf4j
@Component
public class KakaoClient {

    private final RestClient authClient;
    private final RestClient apiClient;
    private final String clientId;
    private final String redirectUri;

    private String accessToken;
    private String refreshToken;

    public KakaoClient(
            @Value("${notification.kakao.client-id}") String clientId,
            @Value("${notification.kakao.redirect-uri}") String redirectUri
    ) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;

        this.authClient = RestClient.builder()
                .baseUrl("https://kauth.kakao.com")
                .build();

        this.apiClient = RestClient.builder()
                .baseUrl("https://kapi.kakao.com")
                .build();
    }

    /**
     * 인가 코드로 토큰 발급
     */
    public void getTokenWithAuthCode(String authCode) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("redirect_uri", redirectUri);
        params.add("code", authCode);

        try {
            KakaoTokenResponse response = authClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(KakaoTokenResponse.class);

            if (response != null) {
                this.accessToken = response.getAccessToken();
                this.refreshToken = response.getRefreshToken();
                log.info("Kakao token issued successfully");
            }
        } catch (Exception e) {
            log.error("Failed to get Kakao token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get Kakao token", e);
        }
    }

    /**
     * 리프레시 토큰으로 액세스 토큰 갱신
     */
    public void refreshAccessToken() {
        if (refreshToken == null) {
            log.warn("No refresh token available");
            return;
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", clientId);
        params.add("refresh_token", refreshToken);

        try {
            KakaoTokenResponse response = authClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(KakaoTokenResponse.class);

            if (response != null) {
                this.accessToken = response.getAccessToken();
                if (response.getRefreshToken() != null) {
                    this.refreshToken = response.getRefreshToken();
                }
                log.info("Kakao token refreshed successfully");
            }
        } catch (Exception e) {
            log.error("Failed to refresh Kakao token: {}", e.getMessage(), e);
        }
    }

    /**
     * 나에게 메시지 보내기
     *
     * @param message 메시지 내용
     */
    public void sendMessageToMe(String message) {
        if (accessToken == null) {
            log.error("No access token available. Please authenticate first.");
            return;
        }

        // 템플릿 객체 생성 (텍스트 타입)
        String templateObject = String.format(
                "{\"object_type\":\"text\",\"text\":\"%s\",\"link\":{\"web_url\":\"https://www.okx.com\",\"mobile_web_url\":\"https://www.okx.com\"}}",
                message.replace("\"", "\\\"").replace("\n", "\\n")
        );

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("template_object", templateObject);

        try {
            KakaoMessageResponse response = apiClient.post()
                    .uri("/v2/api/talk/memo/default/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(KakaoMessageResponse.class);

            if (response != null && response.getResultCode() == 0) {
                log.info("Kakao message sent successfully");
            } else {
                log.error("Failed to send Kakao message: {} - {}",
                        response != null ? response.getError() : "null",
                        response != null ? response.getErrorDescription() : "null");
            }
        } catch (Exception e) {
            log.error("Failed to send Kakao message: {}", e.getMessage(), e);

            // 401 에러면 토큰 갱신 시도
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                log.info("Attempting to refresh token...");
                refreshAccessToken();
            }
        }
    }

    /**
     * 토큰 설정 (수동)
     */
    public void setTokens(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        log.info("Kakao tokens set manually");
    }

    /**
     * OAuth 인증 URL 생성
     */
    public String getAuthUrl() {
        return String.format(
                "https://kauth.kakao.com/oauth/authorize?client_id=%s&redirect_uri=%s&response_type=code&scope=talk_message",
                clientId, redirectUri
        );
    }

    /**
     * 토큰 존재 여부 확인
     */
    public boolean hasToken() {
        return accessToken != null && !accessToken.isEmpty();
    }
}
