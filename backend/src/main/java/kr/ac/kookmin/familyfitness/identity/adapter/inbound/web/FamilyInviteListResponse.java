package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.time.Instant;
import java.util.List;
import kr.ac.kookmin.familyfitness.identity.application.LiveFamilyInvite;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;

/** 아직 쓰지 않았고 만료되지 않은 가족 초대, 최근 것부터. 없으면 빈 목록이다. */
public record FamilyInviteListResponse(List<FamilyInviteItem> invites) {
    /**
     * 가족 초대 하나. OpenAPI 스키마 이름이 다른 응답의 Item 과 겹치지 않게 이름을 길게 둔다. issuedByName 은 초대를 낸 보호자
     * 이름이고, 그 사람이 가족에서 빠졌으면 오너 이름이다.
     */
    public record FamilyInviteItem(
            String code, ProfileRole role, Instant expiresAt, Instant createdAt, String issuedByName) {}

    static FamilyInviteListResponse of(List<LiveFamilyInvite> invites) {
        return new FamilyInviteListResponse(invites.stream()
                .map(it ->
                        new FamilyInviteItem(it.code(), it.role(), it.expiresAt(), it.createdAt(), it.issuedByName()))
                .toList());
    }
}
