package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.CheerService;
import kr.ac.kookmin.familyfitness.identity.application.CreatedFamily;
import kr.ac.kookmin.familyfitness.identity.application.FamilyProfiles;
import kr.ac.kookmin.familyfitness.identity.application.FamilyService;
import kr.ac.kookmin.familyfitness.identity.application.SendCheerCommand;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 가족 생성·구성원·응원. actor 는 토큰의 계정이고 대상은 경로·본문의 profileId 다. */
@RestController
@RequestMapping("/api/v1/families")
public class FamilyController {
    private final FamilyService families;
    private final CheerService cheers;

    public FamilyController(FamilyService families, CheerService cheers) {
        this.families = families;
        this.cheers = cheers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyCreatedResponse create(CurrentUser user, @Valid @RequestBody CreateFamilyRequest request) {
        OwnerRequest owner = Objects.requireNonNull(request.owner());
        CreatedFamily created = families.createFamily(
                user.userId(),
                request.familyName(),
                owner.name(),
                Objects.requireNonNull(owner.birthDate()),
                Objects.requireNonNull(owner.sex()));
        return new FamilyCreatedResponse(created.familyId(), created.familyName(), created.ownerProfile());
    }

    @PostMapping("/{familyId}/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileSummary addMember(
            CurrentUser user, @PathVariable UUID familyId, @Valid @RequestBody AddMemberRequest request) {
        return families.addMember(
                user.userId(),
                familyId,
                request.name(),
                Objects.requireNonNull(request.birthDate()),
                Objects.requireNonNull(request.sex()),
                Objects.requireNonNull(request.role()),
                request.heightCm(),
                request.weightKg(),
                request.guardianConsent() == null
                        ? null
                        : request.guardianConsent().toDomain());
    }

    @GetMapping("/{familyId}/profiles")
    public FamilyProfilesResponse profiles(CurrentUser user, @PathVariable UUID familyId) {
        FamilyProfiles result = families.profilesOf(user.userId(), familyId);
        return new FamilyProfilesResponse(result.familyId(), result.familyName(), result.profiles());
    }

    @PostMapping("/{familyId}/cheers")
    @ResponseStatus(HttpStatus.CREATED)
    public CheerResponse cheer(
            CurrentUser user, @PathVariable UUID familyId, @Valid @RequestBody CheerRequest request) {
        return CheerResponse.of(cheers.cheer(
                user.userId(),
                familyId,
                new SendCheerCommand(
                        Objects.requireNonNull(request.fromProfileId()),
                        Objects.requireNonNull(request.toProfileId()),
                        request.kind(),
                        request.message(),
                        request.effectiveStickerId(),
                        request.missionId(),
                        request.replyToCheerId())));
    }

    /**
     * 받은 응원. 최근 것부터 size 건(기본 20 · 1~100). 거르기 값은 모두 선택이다.
     * 「벌써 알렸나 · 칭찬했나」 는 missionId 로, 「고마워요 보냈나」 는 fromProfileId 와 응답의 replyToCheerId 로 좁혀 읽는다.
     */
    @GetMapping("/{familyId}/cheers")
    public CheerListResponse cheers(
            CurrentUser user,
            @PathVariable UUID familyId,
            @RequestParam(required = false) @Nullable UUID toProfileId,
            @RequestParam(required = false) @Nullable UUID fromProfileId,
            @RequestParam(required = false) @Nullable UUID missionId,
            @RequestParam(defaultValue = "20") int size) {
        return new CheerListResponse(cheers.list(user.userId(), familyId, toProfileId, fromProfileId, missionId, size));
    }
}
