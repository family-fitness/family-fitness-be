package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import org.jspecify.annotations.Nullable;

/**
 * 칸의 영상 구간. 영상 id 는 카탈로그로 확인하지 않고 그대로 저장한다(사본).
 * 글자는 유튜브 영상 id 에 쓰이는 영문 · 숫자 · {@code -} · {@code _} 만, 길이는 저장 칸(32자)까지 받는다.
 *
 * @param endSec 시작보다 뒤여야 한다(아니면 400)
 */
public record SessionClipRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") @Nullable
        String videoId,

        @NotNull @Min(0) @Nullable Integer startSec,
        @NotNull @Nullable Integer endSec,
        @Size(max = 120) @Nullable String title) {

    SessionClip toDomain() {
        return new SessionClip(
                Objects.requireNonNull(videoId),
                Objects.requireNonNull(startSec),
                Objects.requireNonNull(endSec),
                title);
    }
}
