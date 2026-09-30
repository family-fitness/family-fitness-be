package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 운동 구간 목록 한 쪽. FE 의 ClipList 와 같다.
 *
 * @param clips 이 쪽의 구간. 많아야 요청한 size 개다.
 * @param total 조건에 맞는 구간 수. 쪽으로 자르기 전 수라 clips 보다 클 수 있다. 쪽을 끝까지 넘기면 모두 이만큼 받는다.
 * @param nextCursor 다음 쪽을 받을 때 cursor 로 보낼 값(이 쪽 마지막 구간의 clipId). 마지막 쪽이면 null.
 */
public record ExerciseListView(
        List<ExerciseView> clips, int total, @Nullable String nextCursor) {
    static final ExerciseListView EMPTY = new ExerciseListView(List.of(), 0, null);
}
