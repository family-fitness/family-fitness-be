package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.MissionLookup;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.api.ReviewFamilyCreated;
import kr.ac.kookmin.familyfitness.identity.api.ReviewFamilyDays;
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 체험 가족이 지난 2주 동안 주고받은 응원을 넣는다. 엄마가 {@link ReviewFamilyDays#PRAISE_DAYS} 저녁에 아이에게 칭찬 스티커를
 * 보내고, 아이들이 몇 번은 고마워요 스티커로 답하거나 「다 했어요」 를 알린다.
 *
 * <p>화면이 부르는 것과 같은 {@link CheerService#cheer} 를 거친다. 그날 그 시각을 가리키는 시계로 서비스를 새로 만들어 부르므로
 * 보낸 시각이 그날 저녁으로 남는다. 칭찬 스티커 경험치, 업적 「첫 스티커」, 응원 알림은 같은 이벤트를 듣는 모듈이 저절로 만든다.
 * 운동 기록(coaching)보다 늦게 돈다({@link Order}).
 */
@Component
public class ReviewFamilyCheers {
    static final LocalTime DONE_AT = LocalTime.of(19, 40);
    static final LocalTime PRAISE_AT = LocalTime.of(20, 30);
    static final LocalTime THANKS_AT = LocalTime.of(20, 45);

    private final FamilyRepository families;
    private final CheerRepository cheers;
    private final MissionLookup missions;
    private final ProfileQuery profiles;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId zone;

    public ReviewFamilyCheers(
            FamilyRepository families,
            CheerRepository cheers,
            MissionLookup missions,
            ProfileQuery profiles,
            ApplicationEventPublisher events,
            Clock clock,
            ZoneId appZone) {
        this.families = families;
        this.cheers = cheers;
        this.missions = missions;
        this.profiles = profiles;
        this.events = events;
        this.clock = clock;
        this.zone = appZone;
    }

    @EventListener
    @Order(40)
    public void on(ReviewFamilyCreated created) {
        UUID user = created.guardianUserId();
        UUID familyId = created.familyId();
        UUID mom = guardianProfile(profiles.summariesOfFamily(familyId));
        UUID hayun = created.youthProfileId();
        UUID seojun = created.toddlerProfileId();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Integer> days = ReviewFamilyDays.PRAISE_DAYS;

        LocalDate first = today.minusDays(days.get(0));
        Cheer star = send(at(first, PRAISE_AT), user, familyId, praise(mom, hayun, "star", "끝까지 해낸 거 정말 멋져!"));
        send(at(first, THANKS_AT), user, familyId, thanks(hayun, mom, "heart", star.id()));

        LocalDate second = today.minusDays(days.get(1));
        Cheer clap = send(at(second, PRAISE_AT), user, familyId, praise(mom, seojun, "clap", "오늘도 신나게 잘했어"));
        send(at(second, THANKS_AT), user, familyId, thanks(seojun, mom, "heart", clap.id()));

        LocalDate third = today.minusDays(days.get(2));
        send(at(third, PRAISE_AT), user, familyId, praise(mom, hayun, "medal", "다시 재 보니 멀리뛰기가 늘었네!"));

        LocalDate fourth = today.minusDays(days.get(3));
        send(at(fourth, DONE_AT), user, familyId, done(hayun, mom, "엄마, 오늘 운동 다 했어요"));
        send(at(fourth, PRAISE_AT), user, familyId, praise(mom, seojun, "sprout", "쑥쑥 크는 게 보여"));
    }

    /** 이 계정의 보호자 프로필(엄마). 체험 가족을 만든 계정에 붙은 보호자는 엄마 하나다. */
    private static UUID guardianProfile(List<ProfileSummary> family) {
        return family.stream()
                .filter(it -> it.isParent() && it.hasAccount())
                .map(ProfileSummary::profileId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("체험 가족에 계정 있는 보호자가 없다"));
    }

    private Instant at(LocalDate day, LocalTime time) {
        return day.atTime(time).atZone(zone).toInstant();
    }

    private Cheer send(Instant at, UUID user, UUID familyId, SendCheerCommand command) {
        CheerService service =
                new CheerService(families, cheers, missions, events, new IdentityClock(Clock.fixed(at, zone), zone));
        return service.cheer(user, familyId, command);
    }

    private static SendCheerCommand praise(UUID from, UUID to, String sticker, String message) {
        return command(from, to, CheerKind.PRAISE, message, sticker, null);
    }

    private static SendCheerCommand thanks(UUID from, UUID to, String sticker, UUID replyTo) {
        return command(from, to, CheerKind.THANKS, null, sticker, replyTo);
    }

    private static SendCheerCommand done(UUID from, UUID to, String message) {
        return command(from, to, CheerKind.DONE, message, null, null);
    }

    private static SendCheerCommand command(
            UUID from,
            UUID to,
            CheerKind kind,
            @Nullable String message,
            @Nullable String sticker,
            @Nullable UUID replyTo) {
        return new SendCheerCommand(from, to, kind, message, sticker, null, replyTo);
    }
}
