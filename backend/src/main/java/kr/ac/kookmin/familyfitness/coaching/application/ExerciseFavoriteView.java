package kr.ac.kookmin.familyfitness.coaching.application;

/** 찜을 바꾼 결과. FE 목 응답과 같은 모양이다. */
public record ExerciseFavoriteView(String clipId, boolean favorited) {}
