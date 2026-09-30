package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 편성 요청의 recent_video_ids 를 채우는 쿼리({@link MissionSessionJpaRepository#findVideoIdsOf})를 H2 의 실제 미션 · 참여자 · 칸
 * 행으로 확인한다. 편성 날 앞 14일(CoachRunPipeline 이 from = 편성 날 − 14, to = 편성 날 − 1 로 부른다) 안에 시작한 미션만,
 * 최근 미션부터, 대상이 참여자인 미션만 센다. 같은 id 를 한 번만 남기는 일은 어댑터({@link MissionRepository#recentVideoIds})가 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecentVideoIdsPersistenceTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    MissionSessionJpaRepository sessions;

    @Autowired
    MissionRepository missions;

    @Autowired
    ProfileRows rows;

    private final LocalDate runDate = LocalDate.now(KST);
    private final LocalDate from = runDate.minusDays(14);
    private final LocalDate to = runDate.minusDays(1);

    private UUID familyId;
    private UUID momId;
    private UUID kidId;
    private UUID sisterId;

    @BeforeEach
    void setUp() {
        familyId = rows.family();
        momId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        kidId = rows.profile(familyId, LocalDate.of(2015, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        sisterId = rows.profile(familyId, LocalDate.of(2017, 5, 1), Sex.F, ProfileRole.CHILD, "서아");

        // 14일 밖(하루 앞)과 편성 날 당일은 세지 않는다
        mission(runDate.minusDays(15), 1, List.of(kidId), "v-too-old");
        mission(runDate, 1, List.of(kidId), "v-today");

        // 14일 안: 첫날(양끝 포함) · 중간 · 어제
        mission(from, 1, List.of(kidId), "v-a", "v-b");
        mission(runDate.minusDays(10), 1, List.of(kidId), "v-c", "v-a");
        mission(runDate.minusDays(1), 1, List.of(kidId), null, "v-d");

        // 같은 날 시작한 미션 둘은 나중에 만든 것이 앞이다
        mission(runDate.minusDays(5), 1, List.of(kidId), "v-e");
        mission(runDate.minusDays(5), 2, List.of(kidId), "v-f");

        // 동생 혼자 받은 미션은 빠지고, 둘이 같이 받은 미션은 한 번만 들어간다
        mission(runDate.minusDays(8), 1, List.of(sisterId), "v-sister");
        mission(runDate.minusDays(3), 1, List.of(kidId, sisterId), "v-g");
    }

    @Test
    @DisplayName("findVideoIdsOf 는 14일 안에 시작한 대상의 미션 칸 영상을 최근 미션부터 칸 차례로 준다 — 다른 사람 미션 · 영상 없는 칸은 빠진다")
    void 최근_14일_대상_미션의_칸_영상을_최근_순으로_준다() {
        List<String> ids = sessions.findVideoIdsOf(kidId, from, to);

        assertThat(ids).containsExactly("v-d", "v-g", "v-f", "v-e", "v-c", "v-a", "v-a", "v-b");
        assertThat(ids).doesNotContain("v-too-old", "v-today", "v-sister");
    }

    @Test
    @DisplayName("recentVideoIds 는 같은 영상을 한 번만, 최근 것부터 limit 개까지 준다")
    void recentVideoIds_는_중복_없이_최근_순으로_limit_개() {
        assertThat(missions.recentVideoIds(kidId, from, to, 60))
                .containsExactly("v-d", "v-g", "v-f", "v-e", "v-c", "v-a", "v-b");
        assertThat(missions.recentVideoIds(kidId, from, to, 3)).containsExactly("v-d", "v-g", "v-f");
        assertThat(missions.recentVideoIds(sisterId, from, to, 60)).containsExactly("v-g", "v-sister");
    }

    /** 칸마다 1분짜리 미션. videoId 가 null 이면 영상 없는 준비운동 칸이다. {@code order} 가 클수록 그날 나중에 만든 미션이다. */
    private void mission(LocalDate startsOn, int order, List<UUID> participants, @Nullable String... videoIds) {
        List<MissionSession> list = new ArrayList<>();
        for (int i = 0; i < videoIds.length; i++) {
            String videoId = videoIds[i];
            list.add(new MissionSession(
                    i + 1,
                    videoId == null ? SessionPhase.WARMUP : SessionPhase.MAIN,
                    "칸 " + (i + 1),
                    null,
                    1,
                    videoId == null ? null : new SessionClip(videoId, 0, 60, "동작")));
        }
        Instant createdAt = startsOn.atTime(8, 0).plusMinutes(order).atZone(KST).toInstant();
        missions.save(Mission.manual(
                UUID.randomUUID(),
                familyId,
                "미션",
                TargetMetric.TIMER_MINUTES,
                list.size(),
                null,
                startsOn,
                startsOn,
                participants,
                list,
                momId,
                createdAt));
    }
}
