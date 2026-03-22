# API Documentation

## 인증 API

### 카카오 OAuth 인증 시작
```
GET /api/kakao/auth
```

**설명:** 카카오 OAuth 인증 페이지로 리다이렉트

**Response:** 카카오 로그인 페이지 리다이렉트

---

### 카카오 OAuth 콜백
```
GET /api/kakao/callback?code={authCode}
```

**설명:** 카카오 OAuth 인증 후 콜백 처리

**Parameters:**
- `code` (query, required): 카카오에서 발급한 인증 코드

**Response:**
```json
{
  "message": "Kakao authentication successful!"
}
```

---

## 테스트 API

### 헬스체크
```
GET /api/test/health
```

**Response:**
```json
{
  "status": "OK",
  "message": "Phase 1 ready!"
}
```

---

### EMA 계산 테스트
```
GET /api/test/ema/{symbol}
```

**Parameters:**
- `symbol` (path, required): 코인 심볼 (예: BTC, ETH, SOL)

**Response:**
```json
{
  "symbol": "BTC",
  "currentPrice": 68500.00,
  "ema20": 68200.00,
  "ema80": 67800.00,
  "ema320": 67000.00,
  "ema1920": 65500.00,
  "alignment": "정배열 (Bullish)"
}
```

---

## 알림 이력 API

### 전체 알림 이력 조회
```
GET /api/alert-history
```

**Response:**
```json
[
  {
    "id": 1,
    "symbol": "BTC",
    "alertType": "ENTRY_IMPORTANT",
    "direction": "LONG",
    "message": "🔴 [중요 — LONG] BTC\nPrice: 68,500.00 | 320EMA: 67,400.00\nPrice는 320EMA 대비 +1.63% 위치",
    "createdAt": "2026-03-22T09:30:00"
  },
  {
    "id": 2,
    "symbol": "SUMMARY",
    "alertType": "SUMMARY",
    "direction": "NONE",
    "message": "📊 [Summary] 2026-03-22 07:00 KST\n\n🟢 정배열: BTC, ETH, SOL\n🔴 역배열: XRP\n⚪ 해당없음: 16개",
    "createdAt": "2026-03-22T07:00:00"
  }
]
```

---

### 특정 코인의 알림 이력 조회
```
GET /api/alert-history/symbol/{symbol}
```

**Parameters:**
- `symbol` (path, required): 코인 심볼 (예: BTC, ETH)

**Response:**
```json
[
  {
    "id": 1,
    "symbol": "BTC",
    "alertType": "ENTRY_IMPORTANT",
    "direction": "LONG",
    "message": "🔴 [중요 — LONG] BTC\n...",
    "createdAt": "2026-03-22T09:30:00"
  }
]
```

---

### 특정 타입의 알림 이력 조회
```
GET /api/alert-history/type/{alertType}
```

**Parameters:**
- `alertType` (path, required): 알림 타입
  - `SUMMARY`: 요약 알림
  - `ALIGNMENT_WARNING`: 정배열/역배열 주의
  - `ENTRY_IMPORTANT`: 중요 진입 알림
  - `ENTRY_CONFIRM`: 확인 진입 알림

**Response:**
```json
[
  {
    "id": 2,
    "symbol": "SUMMARY",
    "alertType": "SUMMARY",
    "direction": "NONE",
    "message": "📊 [Summary] 2026-03-22 07:00 KST\n...",
    "createdAt": "2026-03-22T07:00:00"
  }
]
```

---

### 특정 기간의 알림 이력 조회
```
GET /api/alert-history/period?start={startDateTime}&end={endDateTime}
```

**Parameters:**
- `start` (query, required): 시작 일시 (ISO 8601 형식, 예: 2026-03-22T00:00:00)
- `end` (query, required): 종료 일시 (ISO 8601 형식)

**Example:**
```bash
curl "http://localhost:8080/api/alert-history/period?start=2026-03-22T00:00:00&end=2026-03-22T23:59:59"
```

**Response:**
```json
[
  {
    "id": 1,
    "symbol": "BTC",
    "alertType": "ENTRY_IMPORTANT",
    "direction": "LONG",
    "message": "🔴 [중요 — LONG] BTC\n...",
    "createdAt": "2026-03-22T09:30:00"
  }
]
```

---

## 알림 타입 상세

### SUMMARY
- **설명:** 정기 요약 알림 (7AM/10PM)
- **Direction:** NONE
- **예시 메시지:**
```
📊 [Summary] 2026-03-22 07:00 KST

🟢 정배열: BTC, ETH, SOL, BNB
🔴 역배열: XRP, DOGE
⚪ 해당없음: 14개
```

### ALIGNMENT_WARNING
- **설명:** 정배열/역배열 주의 (gap ≤ 0.8%)
- **Direction:** BULLISH, BEARISH
- **쿨다운:** 4시간
- **예시 메시지:**
```
⚠️ [정배열 주의] BTC
320EMA: 67,420.00 | 1920EMA: 67,800.00
Gap: 0.56% — 돌파 시 정배열 완성
```

### ENTRY_IMPORTANT
- **설명:** 중요 진입 알림 (±1% EMA 320 기준)
- **Direction:** LONG, SHORT
- **쿨다운:** 4시간
- **예시 메시지:**
```
🔴 [중요 — LONG] BTC
Price: 68,100.00 | 320EMA: 67,400.00
Price는 320EMA 대비 +1.04% 위치
```

### ENTRY_CONFIRM
- **설명:** 확인 진입 알림 (±1% EMA 80 기준)
- **Direction:** LONG, SHORT
- **쿨다운:** 1시간
- **예시 메시지:**
```
🟡 [확인 — LONG] ETH
Price: 3,550.00 | 80EMA: 3,515.00
Price는 80EMA 대비 +1.00% 위치
```

---

## 에러 응답

모든 API는 에러 발생 시 다음과 같은 형식으로 응답합니다:

```json
{
  "timestamp": "2026-03-22T10:00:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Error message details",
  "path": "/api/test/ema/BTC"
}
```

---

## Rate Limiting

현재 Rate Limiting은 구현되지 않았으나, OKX API의 제한을 고려하여:
- REST API: 약 20 requests/sec
- WebSocket: 약 100 subscriptions

과도한 요청을 피하십시오.
