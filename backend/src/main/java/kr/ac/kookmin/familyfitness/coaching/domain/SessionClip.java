package kr.ac.kookmin.familyfitness.coaching.domain;

import org.jspecify.annotations.Nullable;

/**
 * 칸에 붙은 영상 구간의 사본. 클립 표를 가리키지 않고 값을 그대로 든다 —
 * 클립 표를 다시 적재해도 이미 만든 미션의 칸이 바뀌지 않게.
 *
 * @param endSec 없으면 구간이 아니라 영상 한 편이다
 */
public record SessionClip(
        String videoId,
        int startSec,
        @Nullable Integer endSec,
        @Nullable String title) {
    public SessionClip {
        if (videoId.isBlank()) throw new IllegalArgumentException("영상 id 가 비었다");
        if (startSec < 0) throw new IllegalArgumentException("구간 시작은 0초 이상이어야 한다");
        if (endSec != null && endSec <= startSec) {
            throw new IllegalArgumentException("구간 끝(endSec)은 시작(startSec)보다 뒤여야 한다");
        }
    }
}
