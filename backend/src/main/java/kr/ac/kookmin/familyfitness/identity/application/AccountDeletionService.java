package kr.ac.kookmin.familyfitness.identity.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.FamilyDeleting;
import kr.ac.kookmin.familyfitness.identity.api.FamilyNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.MissionLookup;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDeleting;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.IdentityErasureRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository;
import kr.ac.kookmin.familyfitness.identity.domain.AccountNotFoundException;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 계정 탈퇴(DELETE /me)와 오너의 구성원 내보내기(DELETE /families/{familyId}/profiles/{profileId}).
 *
 * <p>외래 키에 ON DELETE CASCADE 가 없다. 그래서 한 트랜잭션 안에서 {@link ProfileDeleting} 이나 {@link FamilyDeleting} 을 발행해
 * 다른 모듈이 자기 행을 먼저 지우게 하고, 이벤트가 돌아오면 identity 가 응원, 운동할 수 있는 시간, 동의 이력, 가족 초대, 프로필,
 * 가족, 계정을 지운다. 어느 단계에서든 실패하면 트랜잭션 전체가 되돌아가 아무것도 지워지지 않는다.
 *
 * <p>가족 초대(family_invites)는 가족을 지울 때 함께 지운다. 한 사람만 빠질 때는 그 사람이 낸 초대를 오너가 낸 것으로 돌리고, 그
 * 사람 계정이 미리 한 동의와 쓴 초대에서 계정 칸만 비운다(초대는 살아 있고, 쓴 코드는 다시 쓰지 못한다).
 *
 * <p>탈퇴는 계정에 붙은 프로필로 갈린다.
 * <ul>
 *   <li>프로필이 없는 계정(가족 없음): 계정만 지운다
 *   <li>오너가 아닌 프로필(보호자, 아이 본인 계정): 그 사람과 그 사람의 기록, 계정을 지운다. 가족과 다른 식구의 기록은 남는다
 *   <li>오너: 혼자 남았으면 가족까지 지운다. 다른 프로필이 하나라도 있으면 409 FAMILY_NOT_EMPTY 이고 아무것도 지우지 않는다
 * </ul>
 * 계정의 리프레시 토큰 기록도 지운다. 액세스 토큰은 상태 없는 JWT 라 만료까지 서명 검사를 지나지만, 계정을 찾는 곳(/me 등)이
 * 401 UNAUTHORIZED 로 돌린다({@link AccountNotFoundException}).
 *
 * <p>내보내기는 오너만 한다. 그 프로필과 그 사람의 기록을 지우고, 프로필에 붙은 계정은 남긴다. 그 계정은 가족 없는 계정이 되어
 * /me 의 nextStep 이 CREATE_FAMILY 가 되고, 리프레시 토큰도 그대로다.
 */
@Service
@Transactional
public class AccountDeletionService {
    private final FamilyRepository families;
    private final UserRepository users;
    private final IdentityErasureRepository erasure;
    private final MissionLookup missions;
    private final ReviewLoginLimiter reviewLogins;
    private final ApplicationEventPublisher events;

    public AccountDeletionService(
            FamilyRepository families,
            UserRepository users,
            IdentityErasureRepository erasure,
            MissionLookup missions,
            ReviewLoginLimiter reviewLogins,
            ApplicationEventPublisher events) {
        this.families = families;
        this.users = users;
        this.erasure = erasure;
        this.missions = missions;
        this.reviewLogins = reviewLogins;
        this.events = events;
    }

    /** 탈퇴. 없는 계정(이미 탈퇴함)이면 401 UNAUTHORIZED. */
    public void withdraw(UUID userId) {
        if (users.findById(userId) == null) throw new AccountNotFoundException();
        List<Profile> own = families.profilesOfUser(userId);
        if (!own.isEmpty()) {
            Family family = lockedFamily(own.getFirst().getFamilyId());
            Profile self = family.profile(own.getFirst().getId());
            if (family.leavesNothingBehind(self)) eraseFamily(family);
            else eraseMember(family, self);
        }
        erasure.eraseAccount(userId);
        afterCommit(() -> reviewLogins.forget(userId));
    }

    /**
     * 구성원 내보내기. 판정 차례: 가족 없음 404 FAMILY_NOT_FOUND → 구성원 아님 403 NOT_SAME_FAMILY → 오너 아님 403 NOT_FAMILY_OWNER
     * → 이 가족 프로필 아님 404 PROFILE_NOT_FOUND → 자기 프로필 409 CANNOT_REMOVE_SELF({@link Family#memberToRemove}).
     */
    public void removeMember(UUID userId, UUID familyId, UUID profileId) {
        if (families.findById(familyId) == null) throw new FamilyNotFoundException(familyId);
        Family family = lockedFamily(familyId);
        eraseMember(family, family.memberToRemove(userId, profileId));
    }

    /** 가족 행을 잠근 뒤 다시 읽는다. 잠그기 전에 읽은 식구 목록은 그 사이 붙은 구성원을 빠뜨릴 수 있다. */
    private Family lockedFamily(UUID familyId) {
        erasure.lockFamily(familyId);
        Family family = families.findById(familyId);
        if (family == null) throw new FamilyNotFoundException(familyId);
        return family;
    }

    private void eraseMember(Family family, Profile leaving) {
        UUID heir = family.owner().getId();
        List<UUID> cheers = erasure.cheersOf(leaving.getId());
        events.publishEvent(new ProfileDeleting(family.getId(), leaving.getId(), leaving.getUserId(), heir, cheers));
        erasure.eraseProfile(leaving.getId(), heir, cheers);
        UUID account = leaving.getUserId();
        if (account != null) {
            erasure.forgetConsentActor(account);
            erasure.forgetInviteClaimer(account);
        }
        DeletedMissionsOnCheers.forget(erasure, missions, family.getId());
    }

    private void eraseFamily(Family family) {
        List<UUID> profileIds =
                family.getProfiles().stream().map(Profile::getId).toList();
        events.publishEvent(new FamilyDeleting(family.getId(), profileIds));
        erasure.eraseFamily(family.getId(), profileIds);
    }

    /** 커밋된 뒤에 돌린다. 되돌린 탈퇴의 계정을 심사용 계정 후보에서 빼지 않게. 트랜잭션 밖이면 곧바로. */
    private static void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
