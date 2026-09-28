package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 직접 만들기의 칸 하나. 화면이 같이 보내는 {@code completed} · {@code verifiedBy} 는 받지 않는다(버린다) —
 * 끝냈는지는 서버가 사람마다 든다.
 *
 * @param position 1부터 n 까지 겹치지 않게. 이 차례 그대로 저장하고 단계로 다시 세우지 않는다
 * @param factor 한글 요인 이름(예: 유연성). 없으면 null
 * @param minutes 1~60분. 상한은 코치 편성의 한 회 상한(minutesPerSession ≤ 60)과 같다
 */
public record MissionSessionRequest(
        @NotNull @Min(1) @Nullable Integer position,
        @NotNull @Nullable SessionPhase phase,
        @NotBlank @Size(max = 120) String title,
        @Nullable FitnessFactor factor,
        @NotNull @Min(1) @Max(60) @Nullable Integer minutes,
        @Valid @Nullable SessionClipRequest clip) {

    MissionSession toDomain() {
        return new MissionSession(
                Objects.requireNonNull(position),
                Objects.requireNonNull(phase),
                title,
                factor,
                Objects.requireNonNull(minutes),
                clip == null ? null : clip.toDomain());
    }
}
