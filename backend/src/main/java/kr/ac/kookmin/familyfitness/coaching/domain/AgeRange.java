package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;

/** 연령대의 만 나이 범위. 영상 라벨의 age_from~age_to 와 교차 여부를 판단할 때 쓴다. */
public record AgeRange(int from, int to) {
    public boolean intersects(int otherFrom, int otherTo) {
        return otherFrom <= to && otherTo >= from;
    }

    public static AgeRange of(AgeGroup ageGroup) {
        return switch (ageGroup) {
            case TODDLER -> new AgeRange(0, 6);
            case YOUTH -> new AgeRange(7, 12);
            case ADOLESCENT -> new AgeRange(13, 18);
            case ADULT -> new AgeRange(19, 64);
            case SENIOR -> new AgeRange(65, 120);
        };
    }

    /** 라벨 없는 영상을 내보내지 않는 「아이 연령대」. */
    public static boolean isChildGroup(AgeGroup ageGroup) {
        return ageGroup == AgeGroup.TODDLER || ageGroup == AgeGroup.YOUTH || ageGroup == AgeGroup.ADOLESCENT;
    }
}
