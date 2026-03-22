package com.sungjun.cryptoalert.controller;

import com.sungjun.cryptoalert.model.AlertHistory;
import com.sungjun.cryptoalert.repository.AlertHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 이력 조회 컨트롤러
 */
@RestController
@RequestMapping("/api/alert-history")
@RequiredArgsConstructor
public class AlertHistoryController {

    private final AlertHistoryRepository alertHistoryRepository;

    /**
     * 전체 알림 이력 조회
     */
    @GetMapping
    public List<AlertHistory> getAllAlerts() {
        return alertHistoryRepository.findAll();
    }

    /**
     * 특정 코인의 알림 이력 조회
     */
    @GetMapping("/symbol/{symbol}")
    public List<AlertHistory> getAlertsBySymbol(@PathVariable String symbol) {
        return alertHistoryRepository.findBySymbolOrderByCreatedAtDesc(symbol);
    }

    /**
     * 특정 타입의 알림 이력 조회
     */
    @GetMapping("/type/{alertType}")
    public List<AlertHistory> getAlertsByType(@PathVariable String alertType) {
        return alertHistoryRepository.findByAlertTypeOrderByCreatedAtDesc(alertType);
    }

    /**
     * 특정 기간의 알림 이력 조회
     */
    @GetMapping("/period")
    public List<AlertHistory> getAlertsByPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        return alertHistoryRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }
}
