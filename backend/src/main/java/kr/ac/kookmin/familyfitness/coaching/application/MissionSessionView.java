package kr.ac.kookmin.familyfitness.coaching.application;

import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/** 미션의 칸 하나. 끝냈는지는 싣지 않는다 — 끝냄은 칸이 아니라 사람마다다. */
public record MissionSessionView(
        int position,
        SessionPhase phase,
        String title,
        @Nullable FitnessFactor factor,
        int minutes,
        @Nullable SessionClipView clip) {}
