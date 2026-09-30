package kr.ac.kookmin.familyfitness.coaching.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/** 시험용 클립 카탈로그. 기본은 비어 있다(적재 전 운영 DB 처럼). */
public class InMemoryExerciseClipRepository implements ExerciseClipRepository {
    public final Map<String, ExerciseClip> clips = new LinkedHashMap<>();

    public InMemoryExerciseClipRepository(ExerciseClip... clips) {
        for (ExerciseClip clip : clips) this.clips.put(clip.clipId(), clip);
    }

    /** 조용하고 집에서 되고 도구 없는 운동 클립. 처방 어휘 이름은 없다. */
    public static ExerciseClip clip(
            String videoId,
            int startSec,
            int endSec,
            String title,
            SessionPhase phase,
            @Nullable FitnessFactor factor,
            AgeGroup ageGroup) {
        return new ExerciseClip(
                ExerciseClip.idOf(videoId, startSec),
                videoId,
                1,
                title,
                null,
                title,
                factor,
                phase,
                startSec,
                endSec,
                true,
                true,
                false,
                true,
                ageGroup,
                "llm",
                true);
    }

    @Override
    public List<ExerciseClip> findAllActive() {
        return clips.values().stream()
                .filter(ExerciseClip::active)
                .sorted(Comparator.comparing(ExerciseClip::videoId).thenComparingInt(ExerciseClip::startSec))
                .toList();
    }

    @Override
    public @Nullable ExerciseClip findById(String clipId) {
        return clips.get(clipId);
    }

    @Override
    public List<ExerciseClip> findAllByIds(Collection<String> clipIds) {
        List<ExerciseClip> found = new ArrayList<>();
        for (String clipId : clipIds) {
            ExerciseClip clip = clips.get(clipId);
            if (clip != null) found.add(clip);
        }
        return List.copyOf(found);
    }
}
