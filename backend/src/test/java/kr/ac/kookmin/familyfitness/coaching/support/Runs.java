package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachPlace;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import org.jspecify.annotations.Nullable;

/** 하루 편성 실행을 만드는 시험 도우미. 기본 조건은 20분 · 조용히 · 집 · 요인 고르지 않음. */
public final class Runs {
    private Runs() {}

    public static CoachRunConditions conditions(boolean withParent) {
        return new CoachRunConditions(20, true, CoachPlace.HOME, null, withParent);
    }

    public static CoachRun running(
            UUID familyId, UUID subjectProfileId, LocalDate runDate, @Nullable UUID requestedBy, Instant at) {
        return CoachRun.start(
                UUID.randomUUID(), familyId, subjectProfileId, runDate, conditions(false), requestedBy, at);
    }

    /** RUNNING 으로 시작해 제안을 붙인 승인 대기 실행. */
    public static CoachRun awaiting(
            UUID familyId,
            UUID subjectProfileId,
            LocalDate runDate,
            @Nullable UUID requestedBy,
            Instant at,
            List<CoachProposalItem> proposals) {
        CoachRun run = running(familyId, subjectProfileId, runDate, requestedBy, at);
        run.complete(List.of(), proposals, null, "부모 요약", null, at);
        return run;
    }
}
