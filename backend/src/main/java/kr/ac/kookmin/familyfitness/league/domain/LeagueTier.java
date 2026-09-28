package kr.ac.kookmin.familyfitness.league.domain;

/** 리그 티어. 아래부터 차례대로다(fe:src/lib/league.ts TIERS). 마스터는 없다(FE 요청서 0장 수정 요청 1). */
public enum LeagueTier {
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM,
    DIAMOND;

    /** 처음 리그에 들어오는 가족의 티어(fe:src/mocks/handlers.ts 새 가족 BRONZE). */
    public static final LeagueTier START = BRONZE;

    public boolean isTop() {
        return this == DIAMOND;
    }

    public boolean isBottom() {
        return this == BRONZE;
    }

    /** 정산 뒤의 티어. 맨 위에서 더 오르거나 맨 아래에서 더 내려가지 않는다. */
    public LeagueTier after(LeagueMove move) {
        return switch (move) {
            case UP -> isTop() ? this : values()[ordinal() + 1];
            case DOWN -> isBottom() ? this : values()[ordinal() - 1];
            case STAY -> this;
        };
    }
}
