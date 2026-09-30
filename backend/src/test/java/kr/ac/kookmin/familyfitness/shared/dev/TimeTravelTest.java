package kr.ac.kookmin.familyfitness.shared.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 개발용 시간 이동: 서버 시계를 앞으로 옮기고, 건너뛴 정시 작업(알림 · 리그 정산 · 토큰 정리 …)을 원래 돌았어야 할 시각에 차례로 돌린다.
 * 시계를 옮기면 다른 시험의 DB 에 앞날 알림 · 리그 방이 쌓이므로 이 시험만 쓰는 인메모리 DB 에서 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:familyfitness-time-travel;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "app.dev.time-travel.enabled=true",
            "app.auth.dev-auto-login.enabled=true"
        })
@Import(TimeTravelTest.Recorder.class)
class TimeTravelTest {
    private static final String PARENT = "00000000-0000-4000-8000-000000000001";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    Recorder recorder;

    @BeforeEach
    void clear() {
        recorder.ran.clear();
    }

    @Test
    @DisplayName("시간 이동이 켜지면 서버 시계는 옮길 수 있는 시계다")
    void 시간_이동이_켜지면_서버_시계는_옮길_수_있는_시계다() {
        assertThat(clock).isInstanceOf(ShiftableClock.class);
    }

    @Test
    @DisplayName("지금 서버 시각 · 오늘 · 옮긴 폭을 본다")
    void 지금_서버_시각_오늘_옮긴_폭을_본다() throws Exception {
        mvc.perform(as(get("/api/v1/dev/clock")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.now").isString())
                .andExpect(jsonPath("$.today")
                        .value(LocalDate.ofInstant(clock.instant(), SEOUL).toString()))
                .andExpect(jsonPath("$.offset").isString());
    }

    @Test
    @DisplayName("by 만큼 앞으로 옮기면 서버 시계가 그 시각을 준다")
    void by_만큼_앞으로_옮기면_서버_시계가_그_시각을_준다() throws Exception {
        Instant before = clock.instant();

        mvc.perform(as(post("/api/v1/dev/clock")).content("{\"by\":\"P1DT2H\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").isString());

        assertThat(clock.instant())
                .isBetween(
                        before.plus(Duration.ofHours(26)),
                        before.plus(Duration.ofHours(26)).plusSeconds(30));
    }

    @Test
    @DisplayName("to 로 정한 시각으로 옮긴다")
    void to_로_정한_시각으로_옮긴다() throws Exception {
        Instant target = clock.instant().plus(Duration.ofHours(5));

        mvc.perform(as(post("/api/v1/dev/clock"))
                        .content("{\"to\":\"" + OffsetDateTime.ofInstant(target, SEOUL) + "\"}"))
                .andExpect(status().isOk());

        assertThat(clock.instant()).isBetween(target, target.plusSeconds(30));
    }

    @Test
    @DisplayName("건너뛴 정시 작업을 원래 시각에 차례로 돌린다 — 07:30 작업이 사흘이면 세 번, 그때마다 시계는 그날 07:30")
    void 건너뛴_정시_작업을_원래_시각에_차례로_돌린다() throws Exception {
        Instant from = clock.instant();

        mvc.perform(as(post("/api/v1/dev/clock")).content("{\"by\":\"P3D\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.ran[*].task",
                        hasItems(
                                "TimeTravelTest.Recorder.at0730",
                                "NotificationScheduler.missionReady",
                                "NotificationScheduler.remeasure",
                                "RefreshTokenSweeper.sweep",
                                "StaleCoachRunSweeper")))
                .andExpect(jsonPath("$.ran[?(@.task == 'TimeTravelTest.Recorder.at0730')].runs")
                        .value(3))
                .andExpect(jsonPath("$.ran[?(@.task == 'TimeTravelTest.Recorder.at0730')].failures")
                        .value(0));

        List<Instant> due = at0730Between(from, clock.instant());
        assertThat(due).hasSize(3);
        assertThat(recorder.ran).hasSize(3);
        for (int i = 0; i < due.size(); i++) {
            assertThat(recorder.ran.get(i)).isBetween(due.get(i), due.get(i).plusSeconds(5));
        }
    }

    @Test
    @DisplayName("달이 바뀌면 월초 리그 정산을 돌린다")
    void 달이_바뀌면_월초_리그_정산을_돌린다() throws Exception {
        LocalDate today = LocalDate.ofInstant(clock.instant(), SEOUL);
        LocalDate firstOfNext = YearMonth.from(today).plusMonths(1).atDay(1);
        Instant target = firstOfNext.atTime(LocalTime.of(0, 30)).atZone(SEOUL).toInstant();

        mvc.perform(as(post("/api/v1/dev/clock"))
                        .content("{\"to\":\"" + OffsetDateTime.ofInstant(target, SEOUL) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ran[*].task", hasItems("LeagueSettlementScheduler.run")));
    }

    @Test
    @DisplayName("뒤로는 옮기지 못한다 — 409 TIME_TRAVEL_BACKWARD")
    void 뒤로는_옮기지_못한다() throws Exception {
        Instant past = clock.instant().minus(Duration.ofHours(1));

        mvc.perform(as(post("/api/v1/dev/clock")).content("{\"to\":\"" + OffsetDateTime.ofInstant(past, SEOUL) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TIME_TRAVEL_BACKWARD"));
    }

    @Test
    @DisplayName("to 와 by 가운데 하나만 준다 — 둘 다 없거나 둘 다 있으면 400")
    void to_와_by_가운데_하나만_준다() throws Exception {
        mvc.perform(as(post("/api/v1/dev/clock")).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TIME_TRAVEL_TARGET"));
        mvc.perform(as(post("/api/v1/dev/clock"))
                        .content("{\"by\":\"PT1H\",\"to\":\"" + OffsetDateTime.ofInstant(clock.instant(), SEOUL)
                                + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TIME_TRAVEL_TARGET"));
    }

    @Test
    @DisplayName("한 번에 400일보다 멀리는 옮기지 못한다")
    void 한_번에_400일보다_멀리는_옮기지_못한다() throws Exception {
        mvc.perform(as(post("/api/v1/dev/clock")).content("{\"by\":\"P401D\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TIME_TRAVEL_TOO_FAR"));
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        return request.header("X-Dev-User-Id", PARENT).contentType(MediaType.APPLICATION_JSON);
    }

    private static List<Instant> at0730Between(Instant fromExclusive, Instant toInclusive) {
        List<Instant> out = new ArrayList<>();
        LocalDate day = LocalDate.ofInstant(fromExclusive, SEOUL);
        while (true) {
            Instant at = day.atTime(LocalTime.of(7, 30)).atZone(SEOUL).toInstant();
            if (at.isAfter(toInclusive)) return out;
            if (at.isAfter(fromExclusive)) out.add(at);
            day = day.plusDays(1);
        }
    }

    /** 매일 07:30(KST) 정시 작업 — 돈 때의 서버 시각을 적어 둔다. */
    static class Recorder {
        final List<Instant> ran = new CopyOnWriteArrayList<>();
        private final Clock clock;

        Recorder(Clock clock) {
            this.clock = clock;
        }

        @Scheduled(cron = "0 30 7 * * *", zone = "Asia/Seoul")
        void at0730() {
            ran.add(clock.instant());
        }
    }
}
