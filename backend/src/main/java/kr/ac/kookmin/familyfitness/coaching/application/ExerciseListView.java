package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;

/**
 * 운동 구간 목록. FE 의 ClipList 와 같다.
 *
 * @param clips 앞에서부터 {@link ExerciseService#PAGE} 개까지.
 * @param total 조건에 맞는 구간 수(같은 제목은 하나로 센다). 자르기 전 수라 clips 보다 클 수 있다.
 */
public record ExerciseListView(List<ExerciseView> clips, int total) {
    static final ExerciseListView EMPTY = new ExerciseListView(List.of(), 0);
}
