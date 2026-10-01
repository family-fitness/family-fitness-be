package kr.ac.kookmin.familyfitness.league.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.league.application.LeagueSettlementScheduler;
import kr.ac.kookmin.familyfitness.progress.api.PlannedDaysSinceCreated;
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * H2 + Flyway(V146 league) 위에서 GET /families/{familyId}/league 를 끝까지 돈다.
 * 잡힌 날은 coaching 의 실제 구현(미션 표), 움직인 날 · 쉬는 날은 activity 의 실제 구현이다.
 * identity 는 목: 같은 가족 판단 · 가족 구성원 · 가족 이름만 흉내 낸다. 「오늘」 은 실제 KST 날짜다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LeagueWebTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    MockMvc mvc;

    @Autowired
    TestAuth auth;

    @Autowired
    ProfileRows rows;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @Autowired
    ActivityRecorder activity;

    @Autowired
    MissionRepository missions;

    @Autowired
    PlannedDaysSinceCreated plannedDays;

    @Autowired
    LeagueSettlementScheduler scheduler;

    @MockitoBean
    FamilyAccess familyAccess;

    @MockitoBean
    ProfileQuery profileQuery;

    @MockitoBean
    CheerQuery cheerQuery;

    @MockitoBean
    AiGateway ai;

    private final UUID parentUser = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(KST);
    private final YearMonth thisMonth = YearMonth.from(today);
    private final Map<UUID, String> names = new HashMap<>();
    private UUID familyId;
    private UUID momId;
    private UUID kidId;

    @BeforeEach
    void setUp() {
        familyId = family("서준이네");
        momId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        kidId = rows.profile(familyId, LocalDate.of(2016, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        when(profileQuery.summariesOfFamily(familyId))
                .thenReturn(List.of(summary(momId, ProfileRole.PARENT), summary(kidId, ProfileRole.CHILD)));
        when(profileQuery.familyName(any())).thenAnswer(call -> names.get(call.<UUID>getArgument(0)));
        // 여러 가족을 한 번에 읽는 기본 구현이 위 두 흉내를 가족마다 부른다
        when(profileQuery.summariesOfFamilies(any())).thenCallRealMethod();
        when(profileQuery.familyNames(any())).thenCallRealMethod();
    }

    private UUID family(String name) {
        UUID id = rows.family(name);
        names.put(id, name);
        return id;
    }

    private ProfileSummary summary(UUID profileId, ProfileRole role) {
        return new ProfileSummary(
                profileId,
                familyId,
                role == ProfileRole.PARENT ? "엄마" : "서준",
                role,
                role == ProfileRole.PARENT ? AgeGroup.ADULT : AgeGroup.YOUTH,
                Sex.F,
                true,
                InviteStatus.CLAIMED,
                null,
                true,
                role == ProfileRole.CHILD,
                true,
                false);
    }

    private ResultActions league(UUID userId, String query) throws Exception {
        return mvc.perform(get("/api/v1/families/" + familyId + "/league" + query)
                .header(HttpHeaders.AUTHORIZATION, auth.bearer(userId)));
    }

    /** 아이 한 명이 참여자인 하루짜리 타이머 미션 — {@code createdAt} 에 만들었다. */
    private void mission(UUID profileId, LocalDate on, TargetMetric metric, Instant createdAt) {
        missions.save(Mission.manual(
                UUID.randomUUID(),
                familyId,
                "스쿼트",
                metric,
                10,
                null,
                on,
                on,
                List.of(profileId),
                List.of(),
                momId,
                createdAt));
        em.flush();
    }

    @Test
    @DisplayName("처음 여는 가족 — 200 · 브론즈 · 셀 날이 없으면 rate · rank 가 null 로 나간다(칸이 빠지지 않는다)")
    void 처음_여는_가족() throws Exception {
        league(parentUser, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(thisMonth.toString()))
                .andExpect(jsonPath("$.tier").value("BRONZE"))
                .andExpect(content().string(containsString("\"rate\":null")))
                .andExpect(content().string(containsString("\"rank\":null")))
                .andExpect(jsonPath("$.groupSize").value(1))
                .andExpect(jsonPath("$.promote").value(0))
                .andExpect(jsonPath("$.demote").value(0))
                .andExpect(jsonPath("$.daysLeft").value(thisMonth.lengthOfMonth() - today.getDayOfMonth()))
                .andExpect(jsonPath("$.standings", hasSize(1)))
                .andExpect(jsonPath("$.standings[0].familyName").value("서준이네"))
                .andExpect(jsonPath("$.standings[0].me").value(true))
                .andExpect(content().string(not(containsString(familyId.toString()))));

        em.flush();
        assertThat(jdbc.queryForObject(
                        "select count(*) from league_members where family_id = ? and round_month = ?",
                        Integer.class,
                        familyId,
                        thisMonth.atDay(1)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("오늘 잡힌 운동을 해내면 100% · 1등. 지난 날짜로 오늘 만든 미션 · 걸음수 미션은 잡힌 날로 세지 않는다")
    void 해낸_날() throws Exception {
        Instant now = Instant.now();
        mission(kidId, today, TargetMetric.TIMER_MINUTES, now);
        mission(kidId, today.minusDays(1), TargetMetric.TIMER_MINUTES, now);
        mission(kidId, today.minusDays(1), TargetMetric.STEPS, now);
        league(parentUser, "").andExpect(content().string(containsString("\"rate\":null")));

        activity.addActiveMinutes(kidId, today, ActivitySource.TIMER, 5);

        league(parentUser, "?month=" + thisMonth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(100))
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.standings[0].rate").value(100));
    }

    @Test
    @DisplayName("잡힌 날은 여러 아이를 쿼리 한 번으로 읽고, 미션을 만든 날(KST) 전의 날은 뺀다")
    void 잡힌_날_여러_아이() {
        UUID sister = rows.profile(familyId, LocalDate.of(2018, 1, 1), Sex.F, ProfileRole.CHILD, "서아");
        LocalDate march10 = LocalDate.of(2026, 3, 10);
        // 3/9 10:00 KST 에 만든 3/10 미션 — 센다
        mission(kidId, march10, TargetMetric.TIMER_MINUTES, Instant.parse("2026-03-09T01:00:00Z"));
        // 3/12 에 만든 3/11 미션 — 뺀다
        mission(kidId, march10.plusDays(1), TargetMetric.TIMER_MINUTES, Instant.parse("2026-03-12T01:00:00Z"));
        // 3/10 00:30 KST(UTC 로는 3/9)에 만든 3/10 미션 — 만든 날 그날이라 센다
        mission(sister, march10, TargetMetric.TIMER_MINUTES, Instant.parse("2026-03-09T15:30:00Z"));

        Map<UUID, Set<LocalDate>> days = plannedDays.plannedDaysSinceCreated(
                List.of(kidId, sister, momId), march10.withDayOfMonth(1), march10.withDayOfMonth(31));

        assertThat(days).containsOnlyKeys(kidId, sister);
        assertThat(days.get(kidId)).containsExactly(march10);
        assertThat(days.get(sister)).containsExactly(march10);
    }

    @Test
    @DisplayName("지난달은 정산 기록으로 답하고(정산 전이면 먼저 정산), 월초 스케줄러가 그 가족들을 이번 달 방에 옮긴다")
    void 지난달_정산() throws Exception {
        YearMonth lastMonth = thisMonth.minusMonths(1);
        LocalDate day = lastMonth.atDay(1);
        UUID roundId = UUID.randomUUID();
        jdbc.update(
                "insert into league_rounds (id, round_month, tier, group_no, created_at) values (?, ?, 'GOLD', 1, ?)",
                roundId,
                day,
                Instant.parse("2026-01-01T00:00:00Z"));
        List<UUID> familyIds = new ArrayList<>(List.of(familyId));
        for (int i = 1; i < 8; i++) familyIds.add(family("이웃" + i));
        for (int i = 0; i < familyIds.size(); i++) {
            jdbc.update(
                    "insert into league_members (round_id, family_id, round_month, seat_no, joined_at) values (?, ?, ?, ?, ?)",
                    roundId,
                    familyIds.get(i),
                    day,
                    i + 1,
                    Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
        }
        mission(kidId, day, TargetMetric.TIMER_MINUTES, day.atStartOfDay(KST).toInstant());
        activity.addActiveMinutes(kidId, day, ActivitySource.VIDEO, 3);

        league(parentUser, "?month=" + lastMonth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(lastMonth.toString()))
                .andExpect(jsonPath("$.tier").value("GOLD"))
                .andExpect(jsonPath("$.rate").value(100))
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.groupSize").value(8))
                .andExpect(jsonPath("$.promote").value(3))
                .andExpect(jsonPath("$.demote").value(3))
                .andExpect(jsonPath("$.daysLeft").value(0))
                .andExpect(jsonPath("$.standings", hasSize(8)))
                .andExpect(jsonPath("$.standings[0].familyName").value("서준이네"))
                .andExpect(jsonPath("$.standings[1].familyName").value("이웃1"));
        em.flush();
        assertThat(jdbc.queryForObject(
                        "select count(*) from league_rounds where id = ? and settled_at is not null",
                        Integer.class,
                        roundId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select final_rate from league_members where round_id = ? and family_id = ?",
                        Integer.class,
                        roundId,
                        familyId))
                .isEqualTo(100);

        // 셀 날이 있는 집이 하나뿐이라 절반(0)까지만 오르내린다 — 모두 골드에 머문다
        LeagueSettlementScheduler.MonthClose close = scheduler.run();
        assertThat(close.settledRounds()).isZero();
        assertThat(close.placedFamilies()).isEqualTo(8);
        league(parentUser, "")
                .andExpect(jsonPath("$.tier").value("GOLD"))
                .andExpect(jsonPath("$.groupSize").value(8));
    }

    @Test
    @DisplayName("앞 달 422 INVALID_DATE · 방에 없던 지난달 404 LEAGUE_NOT_FOUND · 형식이 틀린 달 400 · 다른 가족 403")
    void 잘못된_요청() throws Exception {
        league(parentUser, "?month=" + thisMonth.plusMonths(1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE"));
        league(parentUser, "?month=" + thisMonth.minusMonths(1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LEAGUE_NOT_FOUND"));
        league(parentUser, "?month=2026-13").andExpect(status().isBadRequest());

        UUID stranger = UUID.randomUUID();
        when(familyAccess.requireMember(stranger, familyId)).thenThrow(new NotSameFamilyException());
        league(stranger, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
    }
}
