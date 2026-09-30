package kr.ac.kookmin.familyfitness.activity.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import kr.ac.kookmin.familyfitness.activity.application.port.ActivityDailyRepository;
import kr.ac.kookmin.familyfitness.activity.application.port.RestCardRepository;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ReviewFamilyCreated;
import kr.ac.kookmin.familyfitness.identity.api.ReviewFamilyDays;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 체험 가족이 {@link ReviewFamilyDays#REST_DAY} 에 쉬는 날 카드를 쓴 것으로 둔다. 보호자가 화면에서 쓰는 것과 같은
 * {@link RestCardService#use} 를 거치되, 그날 아침 시각을 가리키는 시계로 만든 것을 쓴다. 카드는 오늘부터만 쓸 수 있어서다.
 *
 * <p>운동 기록(coaching)보다 먼저 돈다({@link Order}). 칸을 끝낼 때 연속 기록과 업적을 판정하는데, 그때 이미 쉬는 날이 있어야
 * 그날을 빠진 날로 세지 않는다.
 */
@Component
public class ReviewFamilyRestDay {
    static final LocalTime USED_AT = LocalTime.of(8, 0);

    private final RestCardRepository cards;
    private final ActivityDailyRepository activities;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profiles;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ZoneId zone;

    public ReviewFamilyRestDay(
            RestCardRepository cards,
            ActivityDailyRepository activities,
            FamilyAccess familyAccess,
            ProfileQuery profiles,
            TransactionTemplate tx,
            Clock clock,
            ZoneId appZone) {
        this.cards = cards;
        this.activities = activities;
        this.familyAccess = familyAccess;
        this.profiles = profiles;
        this.tx = tx;
        this.clock = clock;
        this.zone = appZone;
    }

    @EventListener
    @Order(10)
    public void on(ReviewFamilyCreated created) {
        LocalDate restDay = LocalDate.now(clock.withZone(zone)).minusDays(ReviewFamilyDays.REST_DAY);
        Clock thatMorning = Clock.fixed(restDay.atTime(USED_AT).atZone(zone).toInstant(), zone);
        new RestCardService(cards, activities, familyAccess, profiles, tx, thatMorning, zone)
                .use(created.guardianUserId(), created.familyId(), restDay.toString());
    }
}
