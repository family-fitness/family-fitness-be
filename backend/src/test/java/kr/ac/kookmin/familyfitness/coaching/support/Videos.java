package kr.ac.kookmin.familyfitness.coaching.support;

import java.util.List;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoLabel;
import org.jspecify.annotations.Nullable;

public final class Videos {
    private Videos() {}

    public static ExerciseVideo video(String id) {
        return video(id, 7, 12, "유연성", 600, "QUIET", "SMALL_ROOM", null);
    }

    public static ExerciseVideo video(String id, @Nullable Integer ageFrom, @Nullable Integer ageTo) {
        return video(id, ageFrom, ageTo, "유연성", 600, "QUIET", "SMALL_ROOM", null);
    }

    public static ExerciseVideo video(
            String id,
            @Nullable Integer ageFrom,
            @Nullable Integer ageTo,
            String factors,
            @Nullable Integer durationSec) {
        return video(id, ageFrom, ageTo, factors, durationSec, "QUIET", "SMALL_ROOM", null);
    }

    public static ExerciseVideo video(
            String id,
            @Nullable Integer ageFrom,
            @Nullable Integer ageTo,
            String factors,
            @Nullable Integer durationSec,
            @Nullable String noise,
            @Nullable String space,
            @Nullable String equipment) {
        return new ExerciseVideo(
                id,
                "영상 " + id,
                "국민체력100",
                "PUBLIC",
                durationSec,
                new VideoLabel(ageFrom, ageTo, VideoLabel.parseFactors(factors), "LOW", space, noise, null),
                equipment,
                "SEED",
                Fixed.NOW);
    }

    /** 메모리 저장소용 다섯 편. 옛 시드 값이다 — DB 의 IdpXx2gm90o 는 이제 V132 가 AI 값으로 넣는다. */
    public static List<ExerciseVideo> seed() {
        return List.of(
                video("IdpXx2gm90o", 7, 12, "유연성,근지구력", 600),
                video("sample00002", 4, 64, "유연성", 300),
                video("sample00003", 7, 12, "심폐지구력,순발력", 480, "NORMAL", "SMALL_ROOM", null),
                video("sample00004", 19, 64, "근력,근지구력", 720),
                video("sample00005", 4, 6, "평형성,순발력", 420, "NORMAL", "SMALL_ROOM", null));
    }
}
