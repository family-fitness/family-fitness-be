package kr.ac.kookmin.familyfitness.fitness.domain;

import java.util.List;

/** 한 (항목, 성별, 나이 구간)의 백분위 포인트들. 백분위 오름차순. */
public record NormBucket(int ageFrom, int ageTo, int sourceYear, List<Point> points) {
    public record Point(int percentile, double value) {}

    public boolean covers(int age) {
        return age >= ageFrom && age <= ageTo;
    }
}
