package kr.ac.kookmin.familyfitness.shared.ai;

/**
 * AI 서비스(FastAPI, `{base-url}/v1`) 호출 계약. Notion 「인터페이스 명세」(docs/03) 의 필드를 그대로 옮겼다.
 * AI 는 서비스 테이블에 쓰지 않고 JSON 만 돌려준다. 저장·승인은 전부 이쪽(API 서버)이 한다.
 * 구현: `HttpAiGateway`(app.ai.mode=http) · `StubAiGateway`(app.ai.mode=stub, AI 서비스 없이 결정적 응답).
 */
public interface AiGateway {
    /** POST /v1/fitness/assessment — 3s · 재시도 2회 */
    AssessmentResponse assess(AssessmentRequest request);

    /** POST /v1/fitness/trajectory — 3s · 재시도 2회 */
    TrajectoryResponse trajectory(TrajectoryRequest request);

    /** POST /v1/videos/search — 4s · 재시도 2회 */
    VideoSearchResponse searchVideos(VideoSearchRequest request);

    /** POST /v1/coach/runs — 2s · 재시도 0회. 202 접수. 중복은 409 {@link AiRunInProgressException}. */
    CoachRunAccepted startCoachRun(CoachRunRequest request);

    /** GET /v1/coach/runs/{run_id} — 3s. 호출자가 1.5s 간격 최대 40회 폴링한다. */
    CoachRunResult getCoachRun(String runId);

    /** POST /v1/coach/messages — 10s · 재시도 0회(중복 과금). */
    CoachMessageResponse ask(CoachMessageRequest request);
}
