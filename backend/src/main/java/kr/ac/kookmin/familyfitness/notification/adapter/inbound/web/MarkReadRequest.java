package kr.ac.kookmin.familyfitness.notification.adapter.inbound.web;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * POST /notifications/read 본문. {@code upTo}(ISO-8601 시각)는 선택 — 화면이 받은 목록의 가장 새 createdAt 을 보내면 그때까지 만든
 * 알림만 읽음이 된다. 없으면 그 사람 알림 전부(FE 목 · ASKS 4장 {@code { profileId }} 와 같다).
 */
public record MarkReadRequest(
        @NotNull @Nullable UUID profileId, @Nullable Instant upTo) {}
