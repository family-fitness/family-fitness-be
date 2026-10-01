package kr.ac.kookmin.familyfitness.progress.domain;

/**
 * 업적 열두 개. 코드 · 이름 · 설명 · 차례는 FE 목(fe:src/mocks/progress.ts:226-299) 그대로다(결정 27).
 * 이름과 설명은 서버가 정한 문구라 화면이 고쳐 쓰지 않는다(FE 요청서 7장). 코드는 배지 그림 파일 이름이 된다.
 * 개수를 채우는 칭찬 업적은 두지 않는다 — 첫 스티커 하나만 기념한다(FE 규칙 12).
 *
 * <pre>
 * FIRST_STEP     움직인 첫날(칸을 처음 끝낸 날)
 * STREAK_3/7     이어서 한 날이 처음 3 · 7 이 된 날 — 연속 날과 같은 셈({@link Streak})
 * FULL_SET       하루에 준비 · 본 · 정리 칸을 다 끝낸 첫날
 * MIN_30/100/300 서버가 잰 분의 합이 처음 그 값을 넘은 날
 * WEEKEND        토 · 일에 움직인 첫날
 * TOGETHER       다른 보호자(PARENT)가 같은 날 움직인 첫날
 * REMEASURE      두 번째 측정을 등록한 날
 * FIRST_STICKER  칭찬 스티커를 처음 받은 날
 * SIX_POWERS     목에서도 늘 비어 있다 — 조건이 정해지지 않았다
 * </pre>
 */
public enum Achievement {
    FIRST_STEP("첫걸음", "운동 1개를 처음 완료해요"),
    STREAK_3("3일 연속", "3일 연속 운동해요"),
    FULL_SET("준비운동부터 정리운동까지", "준비운동, 본운동, 정리운동을 한 번에 다 해요"),
    MIN_30("30분", "모두 합쳐 30분 운동해요"),
    MIN_100("100분", "모두 합쳐 100분 운동해요"),
    WEEKEND("주말에도", "토요일이나 일요일에 운동해요"),
    TOGETHER("가족과 함께", "보호자와 같은 날 운동해요"),
    STREAK_7("7일 연속", "7일 연속 운동해요"),
    REMEASURE("다시 측정", "키와 몸무게를 다시 측정해요"),
    FIRST_STICKER("첫 스티커", "칭찬 스티커를 처음 받아요"),
    MIN_300("300분", "모두 합쳐 300분 운동해요"),
    SIX_POWERS("여섯 가지 체력 요인", "여섯 가지 체력 요인을 기르는 운동을 다 해 봐요");

    private final String title;
    private final String description;

    Achievement(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String code() {
        return name();
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }
}
