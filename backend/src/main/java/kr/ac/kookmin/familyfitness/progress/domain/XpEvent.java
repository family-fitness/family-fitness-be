package kr.ac.kookmin.familyfitness.progress.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.api.SessionDone;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 경험치 원장 한 줄. 넣기만 하고 고치거나 지우지 않는다(결정 23 — 한 번 받은 경험치는 줄지 않는다).
 * (profileId, kind, sourceKey) 가 같으면 같은 적립이라 한 번만 들어간다 — 같은 칸을 두 번 끝내도 +5 는 한 번이다.
 *
 * @param fromProfileId 스티커를 붙인 사람(STICKER 만)
 * @param phase 끝낸 칸의 단계(SESSION_DONE 만)
 * @param factor 끝낸 칸의 체력 요인(SESSION_DONE 만, 없을 수 있다)
 * @param occurredOn 그 일이 있었던 날(KST) — 칸을 끝낸 날 · 스티커를 받은 날 · 잰 날
 * @param createdAt 원장에 적은 시각. 최근 경험치 줄을 이 차례로 세운다
 */
public record XpEvent(
        UUID id,
        UUID profileId,
        XpKind kind,
        String sourceKey,
        int amount,
        @Nullable UUID fromProfileId,
        @Nullable UUID missionId,
        SessionDone.@Nullable Phase phase,
        @Nullable FitnessFactor factor,
        LocalDate occurredOn,
        Instant createdAt) {
    /** 원장 키 칸의 길이(progress_xp_events.source_key). */
    public static final int MAX_SOURCE_KEY = 80;

    public XpEvent {
        if (amount <= 0) throw new IllegalArgumentException("경험치는 0보다 커야 한다");
        if (sourceKey.isBlank() || sourceKey.length() > MAX_SOURCE_KEY) {
            throw new IllegalArgumentException("원장 키는 1~" + MAX_SOURCE_KEY + "자여야 한다");
        }
    }

    /** 칸 하나를 처음 끝냄 +5. 키 "missionId:position". */
    public static XpEvent sessionDone(SessionDone done, Instant now) {
        return new XpEvent(
                UUID.randomUUID(),
                done.profileId(),
                XpKind.SESSION_DONE,
                done.missionId() + ":" + done.position(),
                XpKind.SESSION_DONE.amount(),
                null,
                done.missionId(),
                done.phase(),
                done.factor(),
                done.completedOn(),
                now);
    }

    /** 미션 하나를 끝까지 함 +20. 키 "missionId". */
    public static XpEvent missionDone(SessionDone done, Instant now) {
        return new XpEvent(
                UUID.randomUUID(),
                done.profileId(),
                XpKind.MISSION_DONE,
                done.missionId().toString(),
                XpKind.MISSION_DONE.amount(),
                null,
                done.missionId(),
                null,
                null,
                done.completedOn(),
                now);
    }

    /**
     * 칭찬 스티커 +10. 같은 운동(missionId)에는 한 번이라 키는 missionId, 운동에 붙지 않은 스티커는 응원 한 건(cheerId)이 키다(결정 24).
     */
    public static XpEvent sticker(
            UUID toProfileId,
            UUID fromProfileId,
            @Nullable UUID missionId,
            UUID cheerId,
            LocalDate receivedOn,
            Instant now) {
        return new XpEvent(
                UUID.randomUUID(),
                toProfileId,
                XpKind.STICKER,
                (missionId != null ? missionId : cheerId).toString(),
                XpKind.STICKER.amount(),
                fromProfileId,
                missionId,
                null,
                null,
                receivedOn,
                now);
    }

    /** 키 · 몸무게를 다시 잼 +20. 키 "fitnessTestId". 첫 회차는 부르는 쪽이 거른다. */
    public static XpEvent remeasure(UUID profileId, UUID fitnessTestId, LocalDate testedOn, Instant now) {
        return new XpEvent(
                UUID.randomUUID(),
                profileId,
                XpKind.REMEASURE,
                fitnessTestId.toString(),
                XpKind.REMEASURE.amount(),
                null,
                null,
                null,
                null,
                testedOn,
                now);
    }
}
