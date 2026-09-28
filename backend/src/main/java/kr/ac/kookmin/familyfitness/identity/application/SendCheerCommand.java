package kr.ac.kookmin.familyfitness.identity.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import org.jspecify.annotations.Nullable;

/** 응원 보내기 입력. kind 가 null 이면 서버가 역할 · 스티커로 정한다(전환 기간). */
public record SendCheerCommand(
        UUID fromProfileId,
        UUID toProfileId,
        @Nullable CheerKind kind,
        @Nullable String message,
        @Nullable String stickerId,
        @Nullable UUID missionId,
        @Nullable UUID replyToCheerId) {}
