package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.Map;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoMedia;
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

    /**
     * @param videoById 칸 영상을 영상 표에서 미리 한 번에 읽어 둔 것. 공단 영상이면 여기서 mp4 · 첫 장면 주소를 붙인다. 표에 없는 영상은
     *     유튜브로 본다
     */
    public static MissionSessionView of(MissionSession session, Map<String, ExerciseVideo> videoById) {
        return new MissionSessionView(
                session.position(),
                session.phase(),
                session.title(),
                session.factor(),
                session.minutes(),
                clipOf(session, videoById));
    }

    static @Nullable SessionClipView clipOf(MissionSession session, Map<String, ExerciseVideo> videoById) {
        SessionClip clip = session.clip();
        return clip == null ? null : SessionClipView.of(clip, VideoMedia.of(videoById.get(clip.videoId())));
    }
}
