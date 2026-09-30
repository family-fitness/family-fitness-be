package kr.ac.kookmin.familyfitness.shared.domain;

/** 고쳐 쓰지 않는 고정 문구. 응답에 그대로 실어 보내고 화면도 그대로 노출한다. */
public final class Copy {
    public static final String FITNESS_DISCLAIMER =
            "국민체력100 측정 데이터를 바탕으로 한 참고 정보예요. 질병을 진단하거나 치료하려는 것이 아니니, 건강에 관한 판단은 전문가와 상담하세요.";

    private Copy() {}

    /**
     * 백분위 70 → 「상위 30%」. 문장으로 바꾸는 것도 서버 책임이다. 백분위는 AI 처럼 100 도 나오는데 「상위 0%」 는 말이 안 돼
     * 1% 아래로 내리지 않는다.
     */
    public static String topPercentText(int percentile) {
        return "상위 " + Math.max(1, 100 - percentile) + "%";
    }
}
