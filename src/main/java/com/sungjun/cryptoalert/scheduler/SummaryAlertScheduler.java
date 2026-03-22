package com.sungjun.cryptoalert.scheduler;

import com.sungjun.cryptoalert.client.KakaoClient;
import com.sungjun.cryptoalert.model.AlertHistory;
import com.sungjun.cryptoalert.repository.AlertHistoryRepository;
import com.sungjun.cryptoalert.service.AlignmentService;
import com.sungjun.cryptoalert.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Summary Alert 스케줄러
 * - 매일 7AM/10PM에 정배열/역배열 요약 알림
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryAlertScheduler {

    private final MarketDataService marketDataService;
    private final AlignmentService alignmentService;
    private final KakaoClient kakaoClient;
    private final AlertHistoryRepository alertHistoryRepository;

    @Value("${alert.targets}")
    private String targets;

    /**
     * 매일 7:00 AM / 10:00 PM KST 실행
     * cron: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "${schedule.summary-cron:0 0 7,22 * * *}")
    public void sendSummaryAlert() {
        log.info("Starting Summary Alert...");

        try {
            List<String> symbols = Arrays.asList(targets.split(","));
            List<String> bullish = new ArrayList<>();
            List<String> bearish = new ArrayList<>();
            List<String> neutral = new ArrayList<>();

            // 각 코인의 배열 상태 확인
            for (String symbol : symbols) {
                try {
                    Map<Integer, BigDecimal> emas = marketDataService.calculateAllEMAs(symbol.trim());

                    if (alignmentService.isBullishAlignment(emas)) {
                        bullish.add(symbol.trim());
                    } else if (alignmentService.isBearishAlignment(emas)) {
                        bearish.add(symbol.trim());
                    } else {
                        neutral.add(symbol.trim());
                    }

                    // API 부하 방지를 위한 짧은 대기
                    Thread.sleep(500);

                } catch (Exception e) {
                    log.error("Error processing {}: {}", symbol, e.getMessage());
                    neutral.add(symbol.trim());
                }
            }

            // 메시지 생성
            String message = buildSummaryMessage(bullish, bearish, neutral);

            // 카카오톡 전송
            kakaoClient.sendMessageToMe(message);

            // 알림 이력 저장
            saveAlertHistory(message);

            log.info("Summary Alert sent successfully");
            log.info("Bullish: {}, Bearish: {}, Neutral: {}",
                    bullish.size(), bearish.size(), neutral.size());

        } catch (Exception e) {
            log.error("Failed to send Summary Alert: {}", e.getMessage(), e);
        }
    }

    /**
     * Summary 메시지 생성
     */
    private String buildSummaryMessage(List<String> bullish, List<String> bearish, List<String> neutral) {
        StringBuilder sb = new StringBuilder();

        // 헤더
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        sb.append("📊 [Summary] ").append(timestamp).append(" KST\n\n");

        // 정배열
        if (!bullish.isEmpty()) {
            sb.append("🟢 정배열: ").append(String.join(", ", bullish)).append("\n");
        }

        // 역배열
        if (!bearish.isEmpty()) {
            sb.append("🔴 역배열: ").append(String.join(", ", bearish)).append("\n");
        }

        // 중립
        if (!neutral.isEmpty()) {
            sb.append("⚪ 해당없음: ").append(neutral.size()).append("개");
        }

        return sb.toString();
    }

    /**
     * 알림 이력 저장
     */
    private void saveAlertHistory(String message) {
        try {
            AlertHistory history = AlertHistory.builder()
                    .symbol("SUMMARY")
                    .alertType("SUMMARY")
                    .direction("NONE")
                    .message(message)
                    .build();

            alertHistoryRepository.save(history);
            log.debug("Saved summary alert history");
        } catch (Exception e) {
            log.error("Failed to save summary alert history: {}", e.getMessage());
        }
    }
}
