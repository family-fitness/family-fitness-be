package kr.ac.kookmin.familyfitness.fitness.domain;

/** 예측 시나리오. IMPROVE 는 AI 가 내지 않는다(횡단면 자료) → MAINTAIN 만 저장 (▲ AI-13 §3.7). */
public enum PredictionScenario {
    MAINTAIN,
    IMPROVE
}
