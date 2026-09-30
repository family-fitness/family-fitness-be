package kr.ac.kookmin.familyfitness.activity.api;

/** 활동 출처. TIMER·VIDEO 만 서버가 진짜로 안다. 웹(PWA)이라 WATCH 는 존재하지 않는다. */
public enum ActivitySource {
    MANUAL(false),
    TIMER(true),
    VIDEO(true);

    private final boolean serverVerified;

    ActivitySource(boolean serverVerified) {
        this.serverVerified = serverVerified;
    }

    public boolean isServerVerified() {
        return serverVerified;
    }
}
