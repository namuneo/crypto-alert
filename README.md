# 🔔 Crypto Alert System

> 시가총액 TOP 20 가상자산의 기술적 지표를 실시간 분석하여, 매매 조건 충족 시 카카오톡으로 즉시 알림을 전송하는 자동화 시스템

---

## 📌 Overview

| 항목 | 내용 |
|---|---|
| **거래소** | OKX |
| **분석 도구** | TradingView (시각적 분석) → 본 시스템 (자동 감시) |
| **대상** | 시가총액 TOP 20 가상자산 |
| **알림 채널** | 카카오톡 |
| **타임프레임** | 15분봉 (1D, 4H, 1H 추세를 EMA로 압축) |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                 Spring Boot Application              │
│                                                      │
│  ┌──────────────┐    ┌───────────────────────────┐  │
│  │   Scheduler   │    │   OKX WebSocket Client    │  │
│  │  (7AM/10PM)   │    │   (Realtime 15M candle)   │  │
│  └──────┬───────┘    └────────────┬──────────────┘  │
│         │                         │                  │
│         ▼                         ▼                  │
│  ┌────────────────────────────────────────────────┐  │
│  │            Market Data Service                  │  │
│  │        캔들 캐싱 + EMA/ATR 계산                  │  │
│  └───────────────────┬────────────────────────────┘  │
│                      │                               │
│                      ▼                               │
│  ┌────────────────────────────────────────────────┐  │
│  │             Strategy Engine                     │  │
│  │    정배열/역배열 + 가격 접근 조건 평가             │◄─── Config (yml + JSON)
│  └───────────────────┬────────────────────────────┘  │
│                      │                               │
│                      ▼                               │
│  ┌────────────────────────────────────────────────┐  │
│  │          Notification Service                   │  │
│  │           쿨다운 관리 + 알림 전송                  │  │
│  └──────────────────┬─────────────────────────────┘  │
│                     │                                │
│                     ▼                                │
│              ┌────────────┐                          │
│              │  KakaoTalk  │                          │
│              └────────────┘                          │
└─────────────────────────────────────────────────────┘
```

---

## ⚙️ Tech Stack

| 구분 | 기술 |
|---|---|
| **Language** | Java 17 |
| **Framework** | Spring Boot 3.x |
| **Build Tool** | Gradle |
| **Database** | H2 (embedded) — 알림 이력 저장 |
| **WebSocket** | Spring WebSocket Client — OKX 실시간 데이터 수신 |
| **HTTP Client** | Spring WebClient — OKX REST API 호출 |
| **Scheduling** | Spring @Scheduled — 정기 알림 |
| **Config** | application.yml + JSON — 조건 설정 |
| **IDE** | IntelliJ IDEA |

---

## 📊 Indicator Setup (15분봉 기준)

모든 지표는 **15분봉 차트**에서 계산하며, 상위 타임프레임 추세를 EMA 배수로 압축한다.

| 지표 | 대응 타임프레임 | 역할 |
|---|---|---|
| **20 EMA** | 15분봉 | 단기 트리거 |
| **80 EMA** | 1시간봉 (20 × 4) | 단기 추세 |
| **320 EMA** | 4시간봉 (20 × 16) | 메인 추세 필터 |
| **1,920 EMA** | 일봉 (20 × 96) | 장기 지지/저항 |
| **ATR(20)** | — | 변동성 기반 손절폭 계산 |

---

## 🔔 Alert Specifications

### 1. Summary Alert — 정배열/역배열 요약

> **모드:** Scheduled (정기 실행)
> **실행 시점:** 매일 7:00 AM / 10:00 PM KST
> **데이터 소스:** OKX REST API

| 알림 유형 | 조건 | 설명 |
|---|---|---|
| **정배열** | `20EMA > 80EMA > 320EMA > 1920EMA` | 모든 EMA 상승 정렬 완성 |
| **역배열** | `20EMA < 80EMA < 320EMA < 1920EMA` | 모든 EMA 하락 정렬 완성 |

**알림 형식 예시:**
```
📊 [Summary] 2026-03-19 07:00 KST

🟢 정배열: BTC, ETH, SOL, BNB
🔴 역배열: XRP, DOGE
⚪ 해당없음: 14개
```

---

### 2. Alignment Warning Alert — 정배열/역배열 주의

> **모드:** Realtime (24/7 실시간 감시)
> **데이터 소스:** OKX WebSocket (15분봉)
> **쿨다운:** 동일 코인 + 동일 조건 기준 **4시간**

| 알림 유형 | 조건 | 설명 |
|---|---|---|
| **정배열 주의** | `320EMA < 1920EMA` 이면서 gap ≤ 0.8% | 320이 아래에서 1920에 접근, 돌파 시 정배열 완성 |
| **역배열 주의** | `320EMA > 1920EMA` 이면서 gap ≤ 0.8% | 320이 위에서 1920에 접근, 이탈 시 역배열 완성 |

**Gap 계산:**
```
gap = |320EMA - 1920EMA| / 1920EMA
```

**알림 형식 예시:**
```
⚠️ [정배열 주의] BTC
320EMA: 67,420 | 1920EMA: 67,800
Gap: 0.56% — 돌파 시 정배열 완성
```

---

### 3. Entry Alert — 매매 타점 알림

> **모드:** Realtime (24/7 실시간 감시)
> **데이터 소스:** OKX WebSocket (15분봉)
> **트리거:** 각 조건 독립적으로 평가

#### Long Position (기본 조건: `320EMA > 1920EMA`)

| 구분 | 조건 | 쿨다운 | 설명 |
|---|---|---|---|
| **🔴 중요 알림** | `price ≥ 320EMA × 1.01` | 4시간 | 메인 추세선 위 안착 — 핵심 반등 자리 |
| **🟡 확인 알림** | `price ≥ 80EMA × 1.01` | 1시간 | 단기 눌림목 반응 가능 자리 |

#### Short Position (기본 조건: `320EMA < 1920EMA`)

| 구분 | 조건 | 쿨다운 | 설명 |
|---|---|---|---|
| **🔴 중요 알림** | `price ≤ 320EMA × 0.99` | 4시간 | 메인 추세선 아래 안착 — 핵심 저항 자리 |
| **🟡 확인 알림** | `price ≤ 80EMA × 0.99` | 1시간 | 단기 되돌림 반응 가능 자리 |

**알림 형식 예시:**
```
🔴 [중요 — Long] BTC
Price: 68,100 | 320EMA: 67,400
Price는 320EMA 대비 +1.04% 위치
```

---

## 🧊 Cooldown Policy

중복 알림 방지를 위해 동일 코인 + 동일 조건에 대해 쿨다운을 적용한다.

| 알림 유형 | 쿨다운 |
|---|---|
| 정배열/역배열 요약 | 해당 없음 (1일 2회 고정) |
| 정배열/역배열 주의 | **4시간** |
| 중요 알림 (Entry) | **4시간** |
| 확인 알림 (Entry) | **1시간** |

**쿨다운 키 구조:**
```
{coinSymbol}:{alertType}:{direction}
예: BTC:ENTRY_IMPORTANT:LONG
예: ETH:ALIGNMENT_WARNING:BULLISH
```

---

## 📁 Project Structure

```
com.sungjun.cryptoalert
├── client/                     # 외부 API 클라이언트
│   ├── OkxWebSocketClient      # WebSocket 실시간 데이터 수신
│   └── OkxRestClient           # REST API 호출 (Scheduled용)
│
├── service/                    # 핵심 비즈니스 로직
│   ├── MarketDataService       # 캔들 캐싱, EMA/ATR 계산
│   ├── StrategyEngine          # 알림 조건 평가
│   └── CooldownService         # 쿨다운 관리
│
├── notification/               # 알림 전송
│   ├── NotificationService     # 쿨다운 체크 + 알림 전송
│   └── KakaoTalkSender         # 카카오톡 API 연동
│
├── scheduler/                  # 정기 작업
│   └── SummaryAlertScheduler   # 7AM/10PM 요약 알림
│
├── config/                     # 설정
│   ├── AppConfig               # Spring 설정
│   └── AlertConditionConfig    # 조건 설정 로딩
│
├── model/                      # 데이터 모델
│   ├── Candle                  # 캔들스틱 DTO
│   ├── AlertEvent              # 알림 이벤트
│   └── CoinIndicator           # 코인별 지표 데이터
│
└── CryptoAlertApplication.java # Entry point
```

---

## 🔐 Configuration

### application.yml

```yaml
okx:
  websocket:
    url: wss://ws.okx.com:8443/ws/v5/business
  rest:
    base-url: https://www.okx.com
    
alert:
  targets: BTC,ETH,SOL,BNB,XRP,ADA,DOGE,AVAX,DOT,MATIC,LINK,UNI,ATOM,LTC,ETC,FIL,APT,ARB,OP,NEAR
  timeframe: 15m
  ema-periods: [20, 80, 320, 1920]
  atr-period: 20

cooldown:
  alignment-warning: 4h
  entry-important: 4h
  entry-confirm: 1h

schedule:
  summary-cron: "0 0 7,22 * * *"   # 7AM, 10PM KST

notification:
  kakao:
    client-id: ${KAKAO_CLIENT_ID}
    redirect-uri: ${KAKAO_REDIRECT_URI}
```

---

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Gradle 8.x
- OKX 계정 (API 키 불필요 — 공개 데이터만 사용)
- 카카오톡 REST API 키

### Quick Start

```bash
# 1. Clone
git clone https://github.com/sungjun/crypto-alert.git
cd crypto-alert

# 2. 환경변수 설정
cp .env.example .env
# .env 파일에 KAKAO_CLIENT_ID 등 입력

# 3. Build & Run
./gradlew bootRun

# 4. 카카오톡 인증 (브라우저)
# http://localhost:8080/api/kakao/auth

# 5. 동작 확인
curl http://localhost:8080/api/test/health
curl http://localhost:8080/api/test/ema/BTC
```

자세한 내용은 [QUICKSTART.md](QUICKSTART.md)와 [API.md](API.md)를 참조하세요.

---

## 📋 Alert Flow Summary

```
                    ┌─────────────────┐
                    │   OKX Market    │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
         REST API      WebSocket       WebSocket
        (2회/일)       (실시간)         (실시간)
              │              │              │
              ▼              ▼              ▼
     ┌────────────┐  ┌────────────┐  ┌────────────┐
     │  Summary   │  │ Alignment  │  │   Entry    │
     │   Alert    │  │  Warning   │  │   Alert    │
     │            │  │            │  │            │
     │ 정배열       │  │ 정배열주의   │  │ 중요 알림     │
     │ 역배열       │  │ 역배열주의   │  │ 확인 알림     │
     │            │  │            │  │            │
     │ 7AM/10PM   │  │ gap≤0.8%   │  │ ±1% EMA    │
     └─────┬──────┘  └─────┬──────┘  └─────┬──────┘
           │               │               │
           └───────────────┼───────────────┘
                           │
                    ┌──────┴───────┐
                    │  Cooldown    │
                    │   Check      │
                    └──────┬───────┘
                           │
                    ┌──────┴───────┐
                    │ Notification │
                    │   Service    │
                    └──────┬───────┘
                           │
                    ┌──────┴───────┐
                    │  KakaoTalk   │
                    └──────────────┘
```

---

## 🗺️ Roadmap

- [x] Phase 1 — Core: OKX 데이터 수집 + EMA 계산 + 콘솔 출력 ✅
- [x] Phase 2 — Alert: 카카오톡 알림 연동 ✅
- [x] Phase 3 — Entry: 매매 타점 알림 (중요/확인) ✅
- [x] Phase 4 — Polish: 쿨다운 + H2 알림 이력 + 에러 핸들링 ✅
- [ ] Phase 5 — Deploy: Docker 컨테이너화 + 클라우드 배포 (진행예정)

