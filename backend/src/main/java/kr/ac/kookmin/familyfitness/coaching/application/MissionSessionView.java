package kr.ac.kookmin.familyfitness.coaching.application;

import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/** 미션 · 코치 제안의 칸 하나. 끝냈는지는 싣지 않는다 — 끝냄은 칸이 아니라 사람마다다. */
public record MissionSessionView(
        int position,
        SessionPhase phase,
        String title,
        @Nullable FitnessFactor factor,
        int minutes,
        @Nullable SessionClipView clip) {
    public static MissionSessionView of(MissionSession session) {
        SessionClip clip = session.clip();
        return new MissionSessionView(
                session.position(),
                session.phase(),
                session.title(),
                session.factor(),
                session.minutes(),
                clip == null
                        ? null
                        : new SessionClipView(clip.videoId(), clip.startSec(), clip.endSec(), clip.title()));
    }
}
