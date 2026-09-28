package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 구간 찜 본문. profileId 가 비면 서비스가 400 PROFILE_REQUIRED 를 낸다(FE 목과 같은 코드라 여기서 막지 않는다).
 * favorited 가 비면 400 BAD_REQUEST — 빠진 값을 「끄기」 로 읽어 찜을 잘못 지우지 않게 한다.
 */
public record ExerciseFavoriteRequest(
        @Nullable UUID profileId, @NotNull @Nullable Boolean favorited) {}
