package kr.ac.kookmin.familyfitness.coaching.domain;

/** AI 편성 파이프라인의 단계 기록(assess · retrieve · compose · verify). */
public record CoachStep(int seq, String name, String status, String summary) {}
