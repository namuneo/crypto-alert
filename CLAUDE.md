# Crypto Alert System

## 프로젝트 개요
OKX 시총 TOP 20 코인의 EMA 지표를 실시간 분석하여 카카오톡 알림 전송

## 기술 스택
- Java 17, Spring Boot 3.x, Gradle, H2, WebSocket

## 핵심 규칙
- 15분봉 기준 EMA(20, 80, 320, 1920) + ATR(20)
- Scheduled: 7AM/10PM 정배열/역배열 요약
- Realtime: 정배열 주의(gap≤0.8%), Entry 알림(±1%)
- 쿨다운: 중요 4h, 확인 1h, 주의 4h

## 상세 스펙
README.md 참조