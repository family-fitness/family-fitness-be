package kr.ac.kookmin.familyfitness.fitness.domain;

import java.util.Arrays;

/** 등급 기준표 한 줄의 나이 단위. 유아기 줄은 개월(48~83개월) 단위다. */
public enum NormAgeUnit {
    YEARS("세"),
    MONTHS("개월");

    private final String wire;

    NormAgeUnit(String wire) {
        this.wire = wire;
    }

    public String getWire() {
        return wire;
    }

    public static NormAgeUnit of(String wire) {
        return Arrays.stream(values())
                .filter(it -> it.wire.equals(wire))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 나이 단위: " + wire));
    }
}
