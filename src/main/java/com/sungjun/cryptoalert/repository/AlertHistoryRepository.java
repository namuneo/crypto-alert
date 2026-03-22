package com.sungjun.cryptoalert.repository;

import com.sungjun.cryptoalert.model.AlertHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 이력 레포지토리
 */
@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {

    /**
     * 특정 코인의 알림 이력 조회
     */
    List<AlertHistory> findBySymbolOrderByCreatedAtDesc(String symbol);

    /**
     * 특정 기간의 알림 이력 조회
     */
    List<AlertHistory> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    /**
     * 특정 타입의 알림 이력 조회
     */
    List<AlertHistory> findByAlertTypeOrderByCreatedAtDesc(String alertType);
}
