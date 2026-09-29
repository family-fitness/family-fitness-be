package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 한 회차의 국민체력100 인증 등급 — 인증서처럼 한 사람에게 하나다({@link Certifier}). 저장하지 않고 읽을 때 셈한다.
 *
 * @param grade 1등급 · 2등급 · 3등급 · 참가. 판정하지 못했거나 기준이 없으면 null
 * @param missingItems 더 재면 판정할 수 있는 것. NEEDS_ITEMS 면 모자란 것이 가장 적은 등급의 것(같으면 높은 등급),
 *     GRADED 인데 1등급을 판정하지 못해 나온 결과면 1등급에 모자란 것, 그 밖에는 빈 목록
 * @param peers 같은 연령대 · 성별 · 나이 참가자의 등급별 비율(1등급 · 2등급 · 3등급 · 참가 차례). 표에 없으면 빈 목록
 */
public record Certification(
        @Nullable Grade grade, CertificationStatus status, List<MissingItem> missingItems, List<PeerShare> peers) {
    public Certification {
        missingItems = List.copyOf(missingItems);
        peers = List.copyOf(peers);
    }

    /**
     * 사람이 한 번에 재는 것 하나. 035 · 037 은 둘 중 하나만 재면 돼서 한 칸이고, BMI(018)는 「키 · 몸무게」,
     * 허리둘레-신장비(042)는 「허리둘레」(키도 없으면 「키 · 허리둘레」)로 적는다.
     */
    public record MissingItem(List<String> itemCodes, String label) {
        public MissingItem {
            itemCodes = List.copyOf(itemCodes);
        }
    }

    /** 한 등급에 든 사람의 비율(0~1). */
    public record PeerShare(Grade grade, BigDecimal ratio) {}
}
