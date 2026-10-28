package kr.ac.kookmin.familyfitness.fitness.application;

import java.math.BigDecimal;
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

    /** 조회 결과. {@code test} 가 null 이면 측정 이력이 없다. {@code estimate} 는 성인 나이일 때만 채워진다(아니면 빈 값). */
    public record View(Status status, @Nullable FitnessTest test, int ageAtTest, FitnessAgeTable.Estimate estimate) {}

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
        FitnessAgeTable.Estimate empty = new FitnessAgeTable.Estimate(null, List.of(), List.of());
        if (test == null) return new View(Status.NO_TEST, null, 0, empty);
        int age = test.getAgeAtTest();
        if (age < FitnessAgeTable.MIN_AGE || age > FitnessAgeTable.MAX_AGE) {
            return new View(Status.AGE_OUT_OF_RANGE, test, age, empty);
        }
        Map<FitnessItem, BigDecimal> measured = new LinkedHashMap<>();
        for (FitnessTestItem item : test.getItems()) measured.put(item.item(), item.value());
        FitnessAgeTable.Estimate estimate = peers.fitnessAge().estimate(details.sex(), measured);
        Status status = estimate.fitnessAge() != null ? Status.OK : Status.NOT_ENOUGH_ITEMS;
        return new View(status, test, age, estimate);
    }
}
