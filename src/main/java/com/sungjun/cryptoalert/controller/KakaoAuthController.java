package com.sungjun.cryptoalert.controller;

import com.sungjun.cryptoalert.client.KakaoClient;
import com.sungjun.cryptoalert.scheduler.SummaryAlertScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 카카오 인증 및 테스트 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/kakao")
@RequiredArgsConstructor
public class KakaoAuthController {

    private final KakaoClient kakaoClient;
    private final SummaryAlertScheduler summaryAlertScheduler;

    /**
     * 카카오 OAuth 콜백
     */
    @GetMapping("/callback")
    public Map<String, String> callback(@RequestParam String code) {
        log.info("Received kakao auth code: {}", code);

        try {
            kakaoClient.getTokenWithAuthCode(code);
            return Map.of(
                    "status", "success",
                    "message", "Kakao token issued successfully"
            );
        } catch (Exception e) {
            log.error("Failed to get kakao token: {}", e.getMessage(), e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage()
            );
        }
    }

    /**
     * 토큰 수동 설정 (테스트용)
     */
    @PostMapping("/token")
    public Map<String, String> setToken(
            @RequestParam String accessToken,
            @RequestParam(required = false) String refreshToken
    ) {
        kakaoClient.setTokens(accessToken, refreshToken);
        return Map.of(
                "status", "success",
                "message", "Tokens set successfully"
        );
    }

    /**
     * 카카오 OAuth 인증 URL 생성
     */
    @GetMapping("/auth")
    public Map<String, String> getAuthUrl() {
        String authUrl = kakaoClient.getAuthUrl();
        return Map.of(
                "status", "success",
                "authUrl", authUrl,
                "message", "Please visit this URL to authenticate"
        );
    }

    /**
     * 테스트 메시지 전송 (자동 인증 포함)
     * 참고: 이 방식은 테스트 목적으로만 사용하세요.
     * 실제로는 사용자가 직접 브라우저에서 OAuth 인증을 거쳐야 합니다.
     */
    @PostMapping("/test")
    public Map<String, String> testMessage(@RequestParam String message) {
        try {
            // 토큰이 없으면 사용자에게 인증 요청
            if (!kakaoClient.hasToken()) {
                String authUrl = kakaoClient.getAuthUrl();
                return Map.of(
                        "status", "error",
                        "message", "Authentication required. Please visit the authUrl first.",
                        "authUrl", authUrl,
                        "instruction", "1. Visit the authUrl in your browser\n2. Complete authentication\n3. Then try sending the message again"
                );
            }

            kakaoClient.sendMessageToMe(message);
            return Map.of(
                    "status", "success",
                    "message", "Test message sent"
            );
        } catch (Exception e) {
            log.error("Failed to send test message: {}", e.getMessage(), e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage()
            );
        }
    }

    /**
     * Summary Alert 수동 실행 (테스트용)
     */
    @PostMapping("/summary/trigger")
    public Map<String, String> triggerSummary() {
        try {
            summaryAlertScheduler.sendSummaryAlert();
            return Map.of(
                    "status", "success",
                    "message", "Summary alert triggered"
            );
        } catch (Exception e) {
            log.error("Failed to trigger summary: {}", e.getMessage(), e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage()
            );
        }
    }
}
