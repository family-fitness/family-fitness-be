package kr.ac.kookmin.familyfitness.fitness.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessAgeTable;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessItem;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestItem;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 성인 체력 나이. 가장 최근 측정 회차를 성인 또래 분포 표({@link FitnessAgeTable})에 대 보고 셈한다. 저장하지 않는다.
 * <ul>
 *   <li>권한: 보호자만(아이 계정은 403 NOT_A_PARENT). 아이에게는 보여 주지 않기로 했다 — 유소년은 만 11~12세 측정뿐이고
 *       유아기는 개월 단위라 나이로 바꿔 말할 표가 없다.
 *   <li>대상: 측정일 기준 만 19~64세. 밖이면 {@link Status#AGE_OUT_OF_RANGE}.
 *   <li>쓸 수 있는 항목이 2개 미만이면 {@link Status#NOT_ENOUGH_ITEMS} 와 함께 쓴 항목 · 뺀 항목을 그대로 준다.
 *   <li>재지 않은 항목(missingItems)을 함께 준다. 보호자가 무엇을 더 재면 되는지 알 수 있게 한다(13주차 의견 반영).
 * </ul>
 */
@Service
public class FitnessAgeService {
    public enum Status {
        OK,
        NO_TEST,
        AGE_OUT_OF_RANGE,
        NOT_ENOUGH_ITEMS
    }

    /**
     * 조회 결과. {@code test} 가 null 이면 측정 이력이 없다. {@code estimate} 는 성인 나이일 때만 채워진다(아니면 빈 값).
     * {@code missingItems} 는 체력 나이에 쓰는 항목 가운데 이 회차에 재지 않은 것이다(측정 센터 기기가 필요한 항목은 뺀다). 더 재면 체력 나이가 나오거나(항목이 모자랄 때)
     * 더 고르게 나온다. 측정 이력이 없으면 쓰는 항목 전부, 성인 나이가 아니면 빈 목록이다.
     */
    public record View(
            Status status,
            @Nullable FitnessTest test,
            int ageAtTest,
            FitnessAgeTable.Estimate estimate,
            List<FitnessItem> missingItems) {}

    /** 체력 나이 이력의 한 회차. {@code fitnessAge} 는 status 가 OK 일 때만 있다. */
    public record HistoryEntry(
            LocalDate testedOn,
            int ageAtTest,
            Status status,
            @Nullable Integer fitnessAge,
            int itemCount) {}

    /** 이력에 싣는 회차 수(최근 것부터). */
    public static final int HISTORY_LIMIT = 12;

    private final FitnessTestRepository tests;
    private final PeerCatalog peers;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profileQuery;

    public FitnessAgeService(
            FitnessTestRepository tests, PeerCatalog peers, FamilyAccess familyAccess, ProfileQuery profileQuery) {
        this.tests = tests;
        this.peers = peers;
        this.familyAccess = familyAccess;
        this.profileQuery = profileQuery;
    }

    @Transactional(readOnly = true)
    public View estimate(UUID actorId, UUID profileId) {
        familyAccess.requireParentOfProfile(actorId, profileId);
        ProfileDetails details = profileQuery.findDetails(profileId);
        if (details == null) throw new ProfileNotFoundException(profileId);
        FitnessTest test = tests.findLatestByProfileId(profileId);
        FitnessAgeTable table = peers.fitnessAge();
        if (test == null) return new View(Status.NO_TEST, null, 0, EMPTY, suggestions(table, details.sex()));
        int age = test.getAgeAtTest();
        if (!adult(age)) return new View(Status.AGE_OUT_OF_RANGE, test, age, EMPTY, List.of());
        Map<FitnessItem, BigDecimal> measured = measuredOf(test);
        FitnessAgeTable.Estimate estimate = table.estimate(details.sex(), measured);
        List<FitnessItem> missing = suggestions(table, details.sex()).stream()
                .filter(it -> !measured.containsKey(it))
                .toList();
        return new View(statusOf(estimate), test, age, estimate, missing);
    }

    /**
     * 체력 나이 이력. 최근 {@link #HISTORY_LIMIT} 회차를 늦은 것부터, 회차마다 그때의 나이와 체력 나이를 셈한다.
     * 다시 잰 뒤 체력 나이가 어떻게 바뀌었는지 보기 위해서다. 권한은 {@link #estimate} 와 같다(보호자만).
     */
    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID actorId, UUID profileId) {
        familyAccess.requireParentOfProfile(actorId, profileId);
        ProfileDetails details = profileQuery.findDetails(profileId);
        if (details == null) throw new ProfileNotFoundException(profileId);
        FitnessAgeTable table = peers.fitnessAge();
        List<HistoryEntry> out = new ArrayList<>();
        for (FitnessTest test : tests.findRecentByProfileId(profileId, HISTORY_LIMIT)) {
            int age = test.getAgeAtTest();
            if (!adult(age)) {
                out.add(new HistoryEntry(test.getTestedOn(), age, Status.AGE_OUT_OF_RANGE, null, 0));
                continue;
            }
            FitnessAgeTable.Estimate estimate = table.estimate(details.sex(), measuredOf(test));
            out.add(new HistoryEntry(
                    test.getTestedOn(),
                    age,
                    statusOf(estimate),
                    estimate.fitnessAge(),
                    estimate.items().size()));
        }
        return List.copyOf(out);
    }

    private static final FitnessAgeTable.Estimate EMPTY = new FitnessAgeTable.Estimate(null, List.of(), List.of());

    /** 더 재라고 권할 항목. 체력 나이에 쓰는 항목 가운데 측정 센터 기기가 필요한 것(VO2max · 반응시간)은 뺀다. */
    private static List<FitnessItem> suggestions(FitnessAgeTable table, Sex sex) {
        return table.usableItems(sex).stream()
                .filter(it -> !it.needsCenterDevice())
                .toList();
    }

    private static boolean adult(int age) {
        return age >= FitnessAgeTable.MIN_AGE && age <= FitnessAgeTable.MAX_AGE;
    }

    private static Status statusOf(FitnessAgeTable.Estimate estimate) {
        return estimate.fitnessAge() != null ? Status.OK : Status.NOT_ENOUGH_ITEMS;
    }

    private static Map<FitnessItem, BigDecimal> measuredOf(FitnessTest test) {
        Map<FitnessItem, BigDecimal> measured = new LinkedHashMap<>();
        for (FitnessTestItem item : test.getItems()) measured.put(item.item(), item.value());
        return measured;
    }
}
