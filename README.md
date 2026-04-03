# 포트폴리오 AI

개인 포트폴리오 랜딩 페이지에 탑재된 AI 채팅 서버입니다.
방문자가 질문을 입력하면 포트폴리오 정보를 기반으로 실시간 스트리밍 응답을 반환합니다.

---

## 실행 방법

### 1. Qdrant 실행 (Docker)

```bash
docker-compose up -d
```

Qdrant 대시보드: `http://localhost:6333/dashboard`
Qdrant 컬렉션 확인: `http://localhost:6333/dashboard#/collections/portfolio`

### 2. 환경변수 설정

IntelliJ 기준: `Run > Edit Configurations > Environment variables`

```
OPENAI_API_KEY=sk-...실제키...
```

Qdrant는 로컬 Docker 기준 별도 인증 불필요합니다.

### 3. 앱 실행

IntelliJ에서 `PortfolioAiApplication` 실행 또는:

```bash
./gradlew bootRun
```

앱 시작 시 `PortfolioDataLoader`가 Qdrant에 데이터가 없는 경우 자동으로 인덱싱합니다.

### 4. 브라우저 접속

```
http://localhost:8080
```

---

## 아키텍처

```
브라우저 (localhost:8080)
    │
    ├─ GET /          → static/index.html (채팅 UI)
    │
    └─ POST /api/chat → PortfolioChatService
                            ├─ Qdrant 유사도 검색 (상위 3개 청크)
                            ├─ System Prompt 구성
                            └─ OpenAI GPT-4o-mini 스트리밍
                                    │
                                    ▼
                            SSE (text/event-stream) → 브라우저
```

### RAG 흐름

```
앱 시작 시 (Qdrant가 비어있을 때만 실행)
  └─ portfolio.md 로드
  └─ ## 섹션 단위로 청킹 (기본 정보, 교육, 경력, 프로젝트 등)
  └─ OpenAI text-embedding-3-small로 벡터화
  └─ Qdrant에 저장 (컬렉션: portfolio)

질문 수신
  └─ 질문 벡터화
  └─ Qdrant 유사도 검색 (상위 3개 청크)
  └─ 검색된 청크 + 질문 → GPT-4o-mini
  └─ SSE 스트리밍 응답
```

---

## 프로젝트 구조

```
src/main/
├── java/com/example/portfolioai/
│   ├── PortfolioAiApplication.java
│   ├── config/
│   │   └── WebConfig.java                   # CORS 설정
│   ├── controller/
│   │   └── PortfolioChatController.java     # POST /api/chat (SSE 스트리밍)
│   ├── service/
│   │   ├── PortfolioChatService.java        # 벡터 검색 + OpenAI 스트리밍
│   │   └── PortfolioDataLoader.java         # 앱 시작 시 portfolio.md 인덱싱
│   └── dto/
│       └── ChatRequestDto.java
└── resources/
    ├── static/
    │   └── index.html                       # 채팅 UI
    ├── data/
    │   └── portfolio.md                     # 포트폴리오 데이터
    └── application.yml
```

---

## API

### POST /api/chat

**Request**
```json
{ "message": "어떤 기술 스택을 사용하시나요?" }
```

**Response** `Content-Type: text/event-stream`
```
data: Spring Boot와 Java를 주로 사용합니다...
```

---

## Qdrant

| 항목 | URL |
|------|-----|
| 대시보드 | `http://localhost:6333/dashboard` |
| portfolio 컬렉션 | `http://localhost:6333/dashboard#/collections/portfolio` |

컬렉션을 초기화하려면 대시보드에서 삭제하거나:

```bash
curl -X DELETE http://localhost:6333/collections/portfolio
```

삭제 후 앱을 재시작하면 `PortfolioDataLoader`가 자동으로 재인덱싱합니다.

---

## 포트폴리오 데이터 수정

`src/main/resources/data/portfolio.md` 수정 후 앱을 재시작하면 자동 반영됩니다.

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.4.4 + WebFlux |
| AI | Spring AI 1.0.0-M6 + OpenAI GPT-4o-mini |
| Embedding | OpenAI text-embedding-3-small |
| Vector Store | Qdrant |
| UI | 내장 HTML (static/index.html) |
| 문서화 | SpringDoc OpenAPI (Swagger) |
