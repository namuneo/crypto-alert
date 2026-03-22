# Quick Start Guide

## 사전 준비

### 1. 카카오 개발자 앱 등록

1. [카카오 개발자 콘솔](https://developers.kakao.com/) 접속
2. 애플리케이션 추가하기
3. 내 애플리케이션 > 앱 설정 > 요약 정보에서 REST API 키 확인
4. 플랫폼 > Web 플랫폼 등록 > `http://localhost:8080` 추가
5. 제품 설정 > 카카오 로그인 > Redirect URI 등록 > `http://localhost:8080/api/kakao/callback` 추가
6. 제품 설정 > 카카오 로그인 > 동의항목에서 `talk_message` 권한 설정

### 2. 환경 변수 설정

`.env` 파일 생성:

```bash
KAKAO_CLIENT_ID=your_rest_api_key
KAKAO_REDIRECT_URI=http://localhost:8080/api/kakao/callback
```

## 실행 방법

### 1. 빌드

```bash
./gradlew clean build
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 3. 카카오톡 인증

1. 브라우저에서 접속: http://localhost:8080/api/kakao/auth
2. 카카오 로그인 후 권한 동의
3. 자동으로 토큰이 발급되어 메시지 전송 준비 완료

### 4. 동작 확인

#### 헬스체크
```bash
curl http://localhost:8080/api/test/health
```

#### EMA 계산 테스트
```bash
curl http://localhost:8080/api/test/ema/BTC
```

#### WebSocket 연결 확인
로그에서 다음 메시지 확인:
```
Connected to OKX WebSocket successfully
Subscribed to 20 candle channels
```

#### 알림 이력 조회
```bash
# 전체 알림 이력
curl http://localhost:8080/api/alert-history

# BTC 알림 이력
curl http://localhost:8080/api/alert-history/symbol/BTC

# SUMMARY 알림 이력
curl http://localhost:8080/api/alert-history/type/SUMMARY
```

#### H2 콘솔 접속
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:cryptoalert`
- Username: `sa`
- Password: (비어 있음)

## 알림 종류

### 1. Summary Alert (정기)
- 실행 시간: 매일 7:00 AM / 10:00 PM KST
- 내용: 시총 TOP 20 코인의 정배열/역배열 요약

### 2. Alignment Warning (실시간)
- 조건: 320EMA와 1920EMA의 gap ≤ 0.8%
- 쿨다운: 4시간

### 3. Entry Alert - 중요 (실시간)
- Long: price ≥ 320EMA × 1.01
- Short: price ≤ 320EMA × 0.99
- 쿨다운: 4시간

### 4. Entry Alert - 확인 (실시간)
- Long: price ≥ 80EMA × 1.01
- Short: price ≤ 80EMA × 0.99
- 쿨다운: 1시간

## 트러블슈팅

### WebSocket 연결 실패
- OKX API 상태 확인: https://www.okx.com/status
- 방화벽 설정 확인

### 카카오톡 메시지 전송 실패
- 토큰 만료: `/api/kakao/auth`로 재인증
- 권한 확인: 카카오 개발자 콘솔에서 `talk_message` 권한 확인

### EMA 계산 값이 0
- 충분한 캔들 데이터가 수집되지 않음 (초기 실행 시 1-2분 소요)
- OKX API 응답 확인

## 로그 레벨 조정

`application.yml` 수정:

```yaml
logging:
  level:
    com.sungjun.cryptoalert: DEBUG  # 상세 로그
```

## 종료

```bash
Ctrl + C
```

또는

```bash
./gradlew --stop
```
