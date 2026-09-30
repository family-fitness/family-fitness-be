package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.CheerSent;
import kr.ac.kookmin.familyfitness.identity.api.CheerView;
import kr.ac.kookmin.familyfitness.identity.api.FamilyNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.MissionLookup;
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyThankedException;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import kr.ac.kookmin.familyfitness.identity.domain.CheerMissionNotFoundException;
import kr.ac.kookmin.familyfitness.identity.domain.CheerNotFoundException;
import kr.ac.kookmin.familyfitness.identity.domain.CheerRules;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.TooManyCheersException;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가족 안의 응원 보내기 · 받은 응원 목록.
 * 보내기 판정 순서: 가족 → 보낼 수 있는 프로필({@link Family#validateCheer}) → kind 모양 · 방향({@link CheerRules})
 * → THANKS 가 답할 스티커 → missionId 가 이 가족 미션인지 → 분당 횟수. 저장한 뒤 {@link CheerSent} 를 발행한다.
 */
@Service
@Transactional
public class CheerService {
    /** 받은 응원 목록 한 번에 주는 최대 건수. 영상 목록 · 측정 이력과 같다(기본 20 은 컨트롤러). */
    public static final int MAX_LIST_SIZE = 100;

    private final FamilyRepository families;
    private final CheerRepository cheers;
    private final MissionLookup missions;
    private final ApplicationEventPublisher events;
    private final IdentityClock clock;

    public CheerService(
            FamilyRepository families,
            CheerRepository cheers,
            MissionLookup missions,
            ApplicationEventPublisher events,
            IdentityClock clock) {
        this.families = families;
        this.cheers = cheers;
        this.missions = missions;
        this.events = events;
        this.clock = clock;
    }

    public Cheer cheer(UUID userId, UUID familyId, SendCheerCommand command) {
        Family family = requireFamily(familyId);
        family.validateCheer(userId, command.fromProfileId(), command.toProfileId());
        String stickerId = blankToNull(command.stickerId());
        CheerKind kind = resolveKind(family, command, stickerId != null);
        UUID replyTo = command.replyToCheerId();
        if (replyTo != null) checkReplyTarget(familyId, replyTo, command);
        UUID missionId = command.missionId();
        if (missionId != null && !missions.isFamilyMission(familyId, missionId)) {
            throw new CheerMissionNotFoundException(missionId);
        }
        Instant now = clock.now();
        if (cheers.countFromTo(command.fromProfileId(), command.toProfileId(), now.minus(Cheer.WINDOW))
                >= Cheer.MAX_PER_WINDOW) {
            throw new TooManyCheersException();
        }
        Cheer saved = cheers.save(new Cheer(
                UUID.randomUUID(),
                familyId,
                command.fromProfileId(),
                command.toProfileId(),
                kind,
                blankToNull(command.message()),
                stickerId,
                missionId,
                replyTo,
                now));
        events.publishEvent(sentOf(saved));
        return saved;
    }

    /**
     * 받은 응원 최근 것부터. 거르기 값(받은 사람 · 보낸 사람 · 미션)은 모두 선택이고, 다른 가족 프로필이면 빈 목록이다.
     * size 는 1~{@link #MAX_LIST_SIZE}, 밖이면 400. 가족 구성원이 아니면 403 NOT_SAME_FAMILY.
     */
    @Transactional(readOnly = true)
    public List<CheerView> list(
            UUID userId,
            UUID familyId,
            @Nullable UUID toProfileId,
            @Nullable UUID fromProfileId,
            @Nullable UUID missionId,
            int size) {
        if (size < 1 || size > MAX_LIST_SIZE) {
            throw new IllegalArgumentException("size 는 1~" + MAX_LIST_SIZE + " 이어야 합니다");
        }
        Family family = requireFamily(familyId);
        family.requireMember(userId);
        Map<UUID, String> names = CheerViews.namesOf(family);
        return cheers.findInFamily(familyId, toProfileId, fromProfileId, missionId, size).stream()
                .map(it -> CheerViews.of(it, names))
                .toList();
    }

    private Family requireFamily(UUID familyId) {
        Family family = families.findById(familyId);
        if (family == null) throw new FamilyNotFoundException(familyId);
        return family;
    }

    private static CheerKind resolveKind(Family family, SendCheerCommand command, boolean hasSticker) {
        ProfileRole from = family.profile(command.fromProfileId()).getRole();
        ProfileRole to = family.profile(command.toProfileId()).getRole();
        CheerKind kind = CheerRules.resolveKind(command.kind(), from, to, hasSticker);
        CheerRules.checkShape(kind, command.kind() != null, hasSticker, command.replyToCheerId());
        CheerRules.checkDirection(kind, from, to);
        return kind;
    }

    /** 원래 응원이 이 가족에 없으면 404, 답할 대상이 아니면 422, 벌써 답했으면 409. */
    private void checkReplyTarget(UUID familyId, UUID replyTo, SendCheerCommand command) {
        Cheer original = cheers.findById(replyTo);
        if (original == null || !original.familyId().equals(familyId)) throw new CheerNotFoundException(replyTo);
        CheerRules.checkReplyTarget(original, command.fromProfileId(), command.toProfileId());
        if (cheers.existsReplyTo(replyTo)) throw new AlreadyThankedException();
    }

    private static CheerSent sentOf(Cheer cheer) {
        return new CheerSent(
                cheer.id(),
                cheer.familyId(),
                cheer.fromProfileId(),
                cheer.toProfileId(),
                cheer.kind(),
                cheer.stickerId(),
                cheer.message(),
                cheer.missionId(),
                cheer.replyToCheerId(),
                cheer.createdAt());
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
