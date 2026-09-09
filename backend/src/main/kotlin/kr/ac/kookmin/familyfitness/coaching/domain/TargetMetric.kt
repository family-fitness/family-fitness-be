package kr.ac.kookmin.familyfitness.coaching.domain

/**
 * 미션 목표 지표. 서버가 스스로 완료를 판정할 수 있는지([serverVerifiable])가 갈린다.
 * `STEPS` 만 사람이 말한 값이라 목표에 도달해도 보호자 확인 전까지 완료가 아니다.
 */
enum class TargetMetric(
    val serverVerifiable: Boolean,
) {
    VIDEO_DONE(true),
    TIMER_MINUTES(true),
    STEPS(false),
}
