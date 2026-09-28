package kr.ac.kookmin.familyfitness.identity.application;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.identity.api.CheerView;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;

/** 응원 → {@link CheerView}. 보낸 사람 이름은 가족 프로필의 표시 이름이다. */
final class CheerViews {
    private CheerViews() {}

    static Map<UUID, String> namesOf(Family family) {
        return family.getProfiles().stream().collect(Collectors.toMap(Profile::getId, Profile::getDisplayName));
    }

    /** 응원은 같은 가족 프로필 사이에만 저장되므로(외래 키 · validateCheer) 이름이 없으면 저장 상태가 깨진 것이다. */
    static CheerView of(Cheer cheer, Map<UUID, String> names) {
        String fromName = names.get(cheer.fromProfileId());
        if (fromName == null) throw new IllegalStateException("가족에 없는 프로필이 보낸 응원: " + cheer.id());
        return new CheerView(
                cheer.id(),
                cheer.fromProfileId(),
                fromName,
                cheer.toProfileId(),
                cheer.kind(),
                cheer.message(),
                cheer.stickerId(),
                cheer.missionId(),
                cheer.replyToCheerId(),
                cheer.createdAt());
    }
}
