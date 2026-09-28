package kr.ac.kookmin.familyfitness.coaching.domain;

import static kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase.COOLDOWN;
import static kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase.MAIN;
import static kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase.WARMUP;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 기댓값은 FE 목(fe:src/mocks/db.ts sessionsFor 의 배분 줄, develop 86df3c6)을 글자 그대로 잘라 node 로 돌린 결과다.
 * 입력: 준비 · 본 · 정리 칸 수와 요청 분. 칸은 준비 → 본 → 정리 차례다.
 */
class SessionMinutesAllocatorTest {
    private static List<SessionPhase> phases(int warmups, int mains, int cooldowns) {
        List<SessionPhase> phases = new ArrayList<>();
        phases.addAll(Collections.nCopies(warmups, WARMUP));
        phases.addAll(Collections.nCopies(mains, MAIN));
        phases.addAll(Collections.nCopies(cooldowns, COOLDOWN));
        return phases;
    }

    private static List<Integer> minutesOf(String csv) {
        return Arrays.stream(csv.split(" ")).map(Integer::valueOf).toList();
    }

    @ParameterizedTest(name = "준비 {0} · 본 {1} · 정리 {2}, {3}분 → {4}")
    @CsvSource({
        // AI 가짓수 규칙(ai:video/catalog.py)대로 나오는 칸 — 합이 요청 분과 같다
        "1, 3, 1, 10, 1 3 3 2 1",
        "2, 4, 1, 15, 1 1 3 3 3 3 1",
        "2, 4, 1, 20, 1 1 5 4 4 4 1",
        "2, 5, 2, 30, 1 1 6 5 5 5 5 1 1",
        "3, 6, 3, 40, 1 1 1 6 6 6 6 5 5 1 1 1",
        // 목의 시드 미션(유연성 12분, 준비 2 · 본 2 · 정리 2)
        "2, 2, 2, 12, 1 1 4 4 1 1",
        "0, 1, 0, 7, 7"
    })
    @DisplayName("준비 · 정리는 1분씩, 남는 분을 본운동이 나누고 나머지는 앞 칸부터 1분씩 — FE 목과 같은 값이다")
    void 준비_정리는_1분씩_남는_분을_본운동이_나누고_나머지는_앞_칸부터(int warmups, int mains, int cooldowns, int minutes, String expected) {
        List<Integer> allocated = SessionMinutesAllocator.allocate(phases(warmups, mains, cooldowns), minutes);

        assertThat(allocated).isEqualTo(minutesOf(expected));
        assertThat(allocated.stream().mapToInt(Integer::intValue).sum()).isEqualTo(minutes);
    }

    @ParameterizedTest(name = "준비 {0} · 본 {1} · 정리 {2}, {3}분 → {4}")
    @CsvSource({
        // 칸 수가 요청 분보다 많으면 칸마다 1분이라 합이 요청보다 크다
        "1, 3, 1, 4, 1 1 1 1 1",
        "1, 3, 1, 1, 1 1 1 1 1",
        // 본운동 칸이 없으면 준비 · 정리 1분씩만 남는다
        "2, 0, 1, 20, 1 1 1"
    })
    @DisplayName("목과 같이, 칸 수가 요청 분보다 많거나 본운동 칸이 없으면 합이 요청 분과 다르다 — 칸마다 1분 이상은 지킨다")
    void 칸이_요청_분보다_많거나_본운동이_없으면_합이_요청_분과_다르다(int warmups, int mains, int cooldowns, int minutes, String expected) {
        assertThat(SessionMinutesAllocator.allocate(phases(warmups, mains, cooldowns), minutes))
                .isEqualTo(minutesOf(expected))
                .allMatch(it -> it >= 1);
    }

    @Test
    @DisplayName("본운동 칸의 나머지는 칸 차례에서 앞선 본운동 칸부터 더한다 — 단계가 섞여 있어도 본운동끼리의 차례를 본다")
    void 단계가_섞여_있어도_본운동끼리의_차례로_나머지를_더한다() {
        List<SessionPhase> mixed = List.of(MAIN, WARMUP, MAIN, COOLDOWN, MAIN, MAIN, WARMUP);

        assertThat(SessionMinutesAllocator.allocate(mixed, 20)).containsExactly(5, 1, 4, 1, 4, 4, 1);
    }
}
