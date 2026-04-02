# 포트폴리오 AI

개인 포트폴리오 랜딩 페이지에 탑재된 AI 채팅 서버입니다.
방문자가 질문을 입력하면 포트폴리오 정보를 기반으로 실시간 스트리밍 응답을 반환합니다.

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

---

## 실행 방법

### 1. Qdrant 실행 (Docker)

```bash
docker-compose up -d
```

Qdrant 대시보드: `http://localhost:6333/dashboard`

### 2. 환경변수 설정

IntelliJ 기준: `Run > Edit Configurations > Environment variables`

```
OPENAI_API_KEY=sk-...실제키...
```

Qdrant는 로컬 Docker 기준 별도 인증 불필요합니다.
클라우드(Qdrant Cloud) 사용 시 `QDRANT_API_KEY`도 추가하세요.

### 3. 앱 실행

IntelliJ에서 `PortfolioAiApplication` 실행 또는:

```bash
./gradlew bootRun
```

### 4. 브라우저 접속

```
http://localhost:8080
```

Spring Boot가 UI와 API를 함께 서빙하므로 별도 프론트엔드 서버가 필요 없습니다.

---

## 아키텍처

```
브라우저 (localhost:8080)
    │
    ├─ GET /          → static/index.html (채팅 UI)
    │
    └─ POST /api/chat → PortfolioChatService
                            ├─ Qdrant 유사도 검색
                            ├─ System Prompt 구성
                            └─ OpenAI GPT-4o-mini 스트리밍
                                    │
                                    ▼
                            SSE (text/event-stream) → 브라우저
```

### RAG 흐름

```
앱 시작 시 자동 실행
  └─ portfolio.md 로드
  └─ 512 토큰 단위 청킹
  └─ OpenAI Embedding API로 벡터화
  └─ Qdrant에 저장 (컬렉션: portfolio)

질문 수신
  └─ 질문 벡터화
  └─ Qdrant 유사도 검색 (상위 4개 청크)
  └─ 검색 결과 + 질문 → GPT-4o-mini
  └─ SSE 스트리밍 응답
```

---

## 프로젝트 구조

```
src/main/
├── java/com/example/portfolioai/
│   ├── PortfolioAiApplication.java
│   ├── config/
│   │   ├── VectorStoreConfig.java           # Qdrant 자동 구성 (auto-config)
│   │   └── WebConfig.java                   # CORS 설정
│   ├── controller/
│   │   ├── PortfolioChatController.java     # POST /api/chat (SSE 스트리밍)
│   │   └── AdminController.java             # POST /api/admin/init
│   ├── service/
│   │   ├── PortfolioChatService.java        # RAG 로직, OpenAI 스트리밍
│   │   └── PortfolioDataInitService.java    # portfolio.md 로드 및 벡터 저장
│   └── dto/
│       └── ChatRequestDto.java
└── resources/
    ├── static/
    │   └── index.html                       # 채팅 UI (Spring Boot가 서빙)
    ├── data/
    │   └── portfolio.md                     # 포트폴리오 데이터 (.gitignore)
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

### POST /api/admin/init

`portfolio.md` 내용 변경 후 재시작 없이 재적재할 때 사용합니다.

```json
{ "status": "success", "chunksStored": 4 }
```

### Swagger UI
```
http://localhost:8080/swagger-ui.html
```

---

## 포트폴리오 데이터 수정

`src/main/resources/data/portfolio.md` 수정 후 앱 재시작하면 자동 반영됩니다.
(`portfolio.md`는 개인 정보 보호를 위해 `.gitignore` 처리되어 있습니다)
