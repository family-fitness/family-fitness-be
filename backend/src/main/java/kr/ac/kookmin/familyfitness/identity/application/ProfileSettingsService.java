package kr.ac.kookmin.familyfitness.identity.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import kr.ac.kookmin.familyfitness.identity.domain.ProfileEdit;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여 수준(PARENT 본인 프로필) · 보호자 동의(가족의 PARENT, 자기 프로필 제외) · 이름 · 생년월일 · 성별 고치기(가족의 PARENT).
 * 보호자 동의를 거두면 같은 트랜잭션에서 그 아이의 기록을 지운다({@link ProfileRecordsEraser}).
 */
@Service
@Transactional
public class ProfileSettingsService {
    private final FamilyRepository families;
    private final ProfileSummaries summaries;
    private final IdentityClock clock;
    private final ProfileRecordsEraser records;

    public ProfileSettingsService(
            FamilyRepository families, ProfileSummaries summaries, IdentityClock clock, ProfileRecordsEraser records) {
        this.families = families;
        this.summaries = summaries;
        this.clock = clock;
        this.records = records;
    }

    public ProfileSummary changeSupportMode(UUID userId, UUID profileId, SupportMode mode) {
        Family family = load(profileId);
        Profile profile = family.changeSupportMode(userId, profileId, mode);
        families.save(family);
        return summaries.summary(profile);
    }

    /**
     * 바꿀 때마다 동의 이력(consent_events)에 한 줄 남는다. 저장소가 가족을 저장할 때 같이 넣는다. 거두면(하나라도 false) 같은
     * 트랜잭션에서 그 아이의 측정, 운동 기록 같은 기록을 지운다({@link ProfileRecordsEraser}). 개인정보 동의만 거둬도 같다. 그 아이를
     * 처리할 근거가 없어지고, 거둔 채인 프로필은 어느 기록도 새로 쌓지 못해서다. 프로필(이름, 생년월일, 성별, 가족)과 동의 이력은
     * 남는다. 이미 거둔 채로 다시 거둬도 같은 일을 한다.
     */
    public ConsentState updateConsent(UUID userId, UUID profileId, GuardianConsent decision) {
        Family family = load(profileId);
        Profile profile = family.updateConsent(userId, profileId, decision, clock.now(), clock.today());
        families.save(family);
        if (!decision.isComplete()) records.erase(family.getId(), profileId);
        boolean given = profile.getConsent().isGiven();
        return new ConsentState(
                profile.consentGiven(clock.today()),
                given ? profile.getConsent().personalAt() : null,
                given ? profile.getConsent().byUserId() : null,
                profile.measurable(clock.today()));
    }

    /**
     * 이름 · 생년월일 · 성별 고치기. 응답의 연령대 · consentRequired · consentGiven · measurable 은 고친 생년월일로 바로 셈한다.
     * 지난 측정은 측정 때 굳힌 나이 · 백분위를 그대로 둔다(fitness 가 저장해 둔 값을 다시 셈하지 않는다).
     */
    public ProfileSummary editProfile(UUID userId, UUID profileId, ProfileEdit edit) {
        Family family = load(profileId);
        Profile profile = family.editProfile(userId, profileId, edit, clock.today());
        families.save(family);
        return summaries.summary(profile);
    }

    private Family load(UUID profileId) {
        Family family = families.findByProfileId(profileId);
        if (family == null) throw new ProfileNotFoundException(profileId);
        return family;
    }
}
