package kr.ac.kookmin.familyfitness.activity.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.RestDayQuery;
import kr.ac.kookmin.familyfitness.activity.application.port.ActivityDailyRepository;
import kr.ac.kookmin.familyfitness.activity.application.port.RestCardRepository;
import kr.ac.kookmin.familyfitness.activity.domain.AlreadyMovedException;
import kr.ac.kookmin.familyfitness.activity.domain.AlreadyRestDayException;
import kr.ac.kookmin.familyfitness.activity.domain.InvalidRestDateException;
import kr.ac.kookmin.familyfitness.activity.domain.NotRestDayException;
import kr.ac.kookmin.familyfitness.activity.domain.RestCard;
import kr.ac.kookmin.familyfitness.activity.domain.RestCardConflictException;
import kr.ac.kookmin.familyfitness.activity.domain.RestCardMonth;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 쉬는 날 카드 — 가족 단위로 한 달 두 장. 보기는 같은 가족 누구나, 쓰기 · 되돌리기는 보호자만.
 * 검사 차례와 오류 코드는 FE 목(fe:src/mocks/league.ts)과 같다. 「오늘」 은 앱 시간대(KST) 기준이다.
 */
@Service
public class RestCardService implements RestDayQuery {
    /** 다른 요청이 먼저 카드를 넣어 자리를 뺏겼을 때 다시 해 보는 횟수(첫 시도 포함) */
    static final int MAX_ATTEMPTS = 3;

    private final RestCardRepository cards;
    private final ActivityDailyRepository activities;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profiles;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ZoneId zone;

    public RestCardService(
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

    /** 그달 카드. {@code month} 가 없으면 이번 달. */
    @Transactional(readOnly = true)
    public RestCardsView month(UUID userId, UUID familyId, @Nullable YearMonth month) {
        familyAccess.requireMember(userId, familyId);
        return view(familyId, month != null ? month : YearMonth.from(today()));
    }

    /**
     * 카드를 쓴다. 검사 차례: 보호자 → 날짜(형식 · 오늘부터 이번 달 안) → 이미 쉬는 날 → 남은 카드 → 그날 운동한 아이.
     * 두 보호자가 동시에 쓰면 늦은 쪽의 insert 가 유니크 인덱스에 걸린다. 그때는 그 트랜잭션을 되돌리고 새로 읽어
     * 다시 검사한다 — 그래야 같은 날이면 ALREADY_REST_DAY, 카드가 다 찼으면 NO_REST_CARD_LEFT 로 정확히 답한다.
     */
    public RestCardsView use(UUID userId, UUID familyId, @Nullable String restDate) {
        UUID parentId = familyAccess.requireParent(userId, familyId).profileId();
        LocalDate date = parse(restDate);
        RestCard.requireUsable(date, today());
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            RestCardsView view = tx.execute(status -> tryUse(familyId, date, parentId, status));
            if (view != null) return view;
        }
        throw new RestCardConflictException();
    }

    /** 쉬는 날을 되돌린다(카드가 돌아온다). 오늘과 앞날만. 그날 이미 운동했는지는 보지 않는다(FE 목과 같다). */
    @Transactional
    public RestCardsView cancel(UUID userId, UUID familyId, LocalDate restDate) {
        familyAccess.requireParent(userId, familyId);
        RestCard.requireCancellable(restDate, today());
        if (!cards.delete(familyId, restDate)) throw new NotRestDayException();
        return view(familyId, YearMonth.from(restDate));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalDate> restDaysBetween(UUID familyId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("기간의 끝이 시작보다 앞섭니다: " + from + " ~ " + to);
        }
        return cards.restDatesBetween(familyId, from, to);
    }

    /** 한 번 써 본다. 다른 요청에 자리를 뺏겼으면 이 트랜잭션을 되돌리고 null — 다음 시도가 새로 읽는다. */
    private @Nullable RestCardsView tryUse(UUID familyId, LocalDate date, UUID parentId, TransactionStatus status) {
        RestCardMonth month = monthOf(familyId, YearMonth.from(date));
        if (month.isRestDay(date)) throw new AlreadyRestDayException();
        int cardNo = month.nextCardNo();
        if (anyChildMoved(familyId, date)) throw new AlreadyMovedException();
        if (!cards.insert(RestCard.use(familyId, date, cardNo, parentId, clock.instant()))) {
            status.setRollbackOnly();
            return null;
        }
        return view(familyId, month.month());
    }

    /** 그날 운동한 아이가 있는가. 운동한 것 = 그날 TIMER · VIDEO 분이 0 보다 크다(FE 목: 그날 기록 minutes &gt; 0). */
    private boolean anyChildMoved(UUID familyId, LocalDate date) {
        List<UUID> children = profiles.summariesOfFamily(familyId).stream()
                .filter(profile -> profile.role() == ProfileRole.CHILD)
                .map(ProfileSummary::profileId)
                .toList();
        return activities.anyActiveOn(children, date);
    }

    /** 요청 본문의 날짜. 없거나 형식이 틀려도 400 이 아니라 422 INVALID_DATE 다(FE 목과 같다). */
    private static LocalDate parse(@Nullable String restDate) {
        if (restDate == null || restDate.isBlank()) throw new InvalidRestDateException("날짜가 없습니다");
        try {
            return LocalDate.parse(restDate.strip());
        } catch (DateTimeParseException e) {
            throw new InvalidRestDateException("날짜 형식은 YYYY-MM-DD 입니다: " + restDate);
        }
    }

    private RestCardsView view(UUID familyId, YearMonth month) {
        return RestCardsView.of(monthOf(familyId, month));
    }

    private RestCardMonth monthOf(UUID familyId, YearMonth month) {
        return new RestCardMonth(month, cards.findByMonth(familyId, month));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }
}
