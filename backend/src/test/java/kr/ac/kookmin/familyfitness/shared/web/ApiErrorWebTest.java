package kr.ac.kookmin.familyfitness.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * 실패가 모두 {@code {"error":{"code","message"}}} 봉투와 제 상태 코드로 나가는지 본다.
 * 실제 주소(칭찬 · me · 없는 경로)와, 이 시험에만 있는 {@link ProbeController} 로 표준 예외 · 제약 위반 · /error 경로를 재현한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ApiErrorWebTest {
    /** 시드 데모 부모(db/seed). /me 가 정상으로 끝나야 하는 406 시험에 쓴다. */
    static final UUID DEMO_PARENT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    static final String PROBE = "/api/v1/test-probe";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestAuth auth;

    @Test
    @DisplayName("Content-Type 이 JSON 이 아니면 500 이 아니라 415 UNSUPPORTED_MEDIA_TYPE")
    void Content_Type_이_JSON_이_아니면_415() throws Exception {
        mvc.perform(post("/api/v1/families/" + UUID.randomUUID() + "/cheers")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("화이팅"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("본문이 없으면 400 이고 message 에 Java 메서드 시그니처가 실리지 않는다")
    void 본문이_없으면_400_이고_message_에_메서드_시그니처가_없다() throws Exception {
        mvc.perform(post("/api/v1/families/" + UUID.randomUUID() + "/cheers")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("요청 본문을 읽을 수 없습니다"));
    }

    @Test
    @DisplayName("깨진 JSON 도 같은 고정 문구로 400")
    void 깨진_JSON_도_같은_고정_문구로_400() throws Exception {
        mvc.perform(post("/api/v1/families/" + UUID.randomUUID() + "/cheers")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromProfileId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("요청 본문을 읽을 수 없습니다"));
    }

    @Test
    @DisplayName("없는 메서드는 405, 없는 경로는 404, 받을 수 없는 Accept 는 406")
    void 없는_메서드는_405_없는_경로는_404_받을_수_없는_Accept_는_406() throws Exception {
        mvc.perform(put("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(get("/api/v1/no-such-path").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(DEMO_PARENT))
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("NOT_ACCEPTABLE"));
    }

    @Test
    @DisplayName("토큰이 없으면 401 UNAUTHORIZED")
    void 토큰이_없으면_401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("필수 헤더가 없으면 400, multipart 를 못 읽으면 400, 올린 파일이 크면 413, 비동기 시간 초과는 503")
    void 표준_예외는_제_상태로_나간다() throws Exception {
        mvc.perform(get(PROBE + "/header").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(get(PROBE + "/multipart").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(get(PROBE + "/too-large").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.error.code").value("CONTENT_TOO_LARGE"));
        mvc.perform(get(PROBE + "/timeout").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    @DisplayName("유니크 제약 위반은 409 CONFLICT — H2 가 실제로 낸 위반과 JPA 가 감싼 위반 모두")
    void 유니크_제약_위반은_409_CONFLICT() throws Exception {
        mvc.perform(post(PROBE + "/unique").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        mvc.perform(get(PROBE + "/jpa-unique").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("읽은 뒤 다른 요청이 행을 먼저 바꾸거나 지워 UPDATE 가 0행이면(낙관적 잠금 실패) 500 이 아니라 409 CONFLICT")
    void 낙관적_잠금_실패는_409_CONFLICT() throws Exception {
        mvc.perform(get(PROBE + "/stale").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message", not(containsString("mission_participants"))));
    }

    @Test
    @DisplayName("NOT NULL 같은 다른 무결성 위반은 서버 버그라 500 으로 남긴다")
    void 다른_무결성_위반은_500() throws Exception {
        mvc.perform(post(PROBE + "/not-null").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("IllegalArgumentException 은 400 으로 돌리되 스택과 함께 WARN 로그를 남긴다")
    void IllegalArgumentException_은_400_이고_로그가_남는다(CapturedOutput output) throws Exception {
        mvc.perform(get(PROBE + "/illegal").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("probe-illegal-argument"));

        assertThat(output.getOut())
                .contains("WARN")
                .contains("java.lang.IllegalArgumentException: probe-illegal-argument");
    }

    @Test
    @DisplayName("날짜 · 달 쿼리는 어노테이션 없이 ISO 로 읽고, 형식이 틀리면 400 에 Java 형 이름을 싣지 않는다")
    void 날짜_달_쿼리는_ISO_로_읽는다() throws Exception {
        mvc.perform(get(PROBE + "/dates")
                        .param("from", "2026-09-01")
                        .param("month", "2026-09")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(content().string("2026-09-01|2026-09"));
        mvc.perform(get(PROBE + "/dates")
                        .param("from", "2026/09/01")
                        .param("month", "2026-09")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message", not(containsString("java."))));
    }

    @Test
    @DisplayName("/error 경로도 봉투로 답한다 — 상태는 컨테이너가 넘긴 값, 예외 문구는 싣지 않는다")
    void error_경로도_봉투로_답한다() throws Exception {
        mvc.perform(get("/error")
                        .accept(MediaType.APPLICATION_JSON)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1//me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.status").doesNotExist());
        mvc.perform(get("/error")
                        .accept(MediaType.APPLICATION_JSON)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(
                                RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("probe-filter-failure")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("서버 오류가 발생했습니다"));
    }

    private String bearer() {
        return auth.bearer(UUID.randomUUID());
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        ProbeController probeController(JdbcTemplate jdbc) {
            return new ProbeController(jdbc);
        }
    }

    /** 이 시험 컨텍스트에만 올라가는 컨트롤러. 실제 주소로는 재현하기 어려운 예외를 그대로 던진다. */
    @RestController
    @RequestMapping(PROBE)
    static class ProbeController {
        private static final String INSERT_USER =
                "insert into users (id, provider, provider_user_id, status, created_at, updated_at)"
                        + " values (?, ?, 'probe-same-id', 'ACTIVE', current_timestamp, current_timestamp)";

        private final JdbcTemplate jdbc;

        ProbeController(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @GetMapping("/header")
        String header(@RequestHeader("X-Probe") String value) {
            return value;
        }

        @GetMapping("/dates")
        String dates(@RequestParam LocalDate from, @RequestParam YearMonth month) {
            return from + "|" + month;
        }

        /** uq_users_provider(provider, provider_user_id) 를 실제 H2 에서 어긴다. 시험 트랜잭션이 끝나면 되돌린다. */
        @PostMapping("/unique")
        void unique() {
            jdbc.update(INSERT_USER, UUID.randomUUID(), "PROBE");
            jdbc.update(INSERT_USER, UUID.randomUUID(), "PROBE");
        }

        /** users.provider NOT NULL 을 실제 H2 에서 어긴다. */
        @PostMapping("/not-null")
        void notNull() {
            jdbc.update(INSERT_USER, UUID.randomUUID(), null);
        }

        /** JPA 저장소가 flush 때 내는 모양: DataIntegrityViolationException ← Hibernate ConstraintViolationException ← SQLException(23505). */
        @GetMapping("/jpa-unique")
        void jpaUnique() {
            SQLException root = new SQLException("unique violation", "23505");
            throw new DataIntegrityViolationException(
                    "could not execute statement",
                    new org.hibernate.exception.ConstraintViolationException(
                            "could not execute statement", root, "uq_probe"));
        }

        /** 지우기와 겹친 쓰기의 flush 가 내는 모양(QA SA-09): 행을 읽은 뒤 지워져 UPDATE 가 0행. */
        @GetMapping("/stale")
        void stale() {
            throw new ObjectOptimisticLockingFailureException(
                    "Unexpected row count (expected row count 1 but was 0) [update mission_participants set ...]",
                    new org.hibernate.StaleStateException("Unexpected row count"));
        }

        @GetMapping("/illegal")
        void illegal() {
            throw new IllegalArgumentException("probe-illegal-argument");
        }

        @GetMapping("/multipart")
        void multipart() {
            throw new MultipartException("Failed to parse multipart servlet request");
        }

        @GetMapping("/too-large")
        void tooLarge() {
            throw new MaxUploadSizeExceededException(1_048_576);
        }

        @GetMapping("/timeout")
        void timeout() {
            throw new AsyncRequestTimeoutException();
        }
    }
}
