package kr.ac.kookmin.familyfitness.identity.api;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 가족에서 프로필 하나를 지우기 바로 전이다(오너가 아닌 사람의 탈퇴, 오너의 구성원 내보내기). 가족은 남는다.
 *
 * <p>identity 가 지우는 트랜잭션 안에서 발행하고, 아래 모듈이 같은 트랜잭션에서 동기로 듣고 자기 표의 행을 먼저 지운다. 외래 키에
 * ON DELETE CASCADE 가 없어서, 이 사람을 가리키는 행이 남아 있으면 identity 가 프로필 행을 지울 때 실패한다. 듣는 쪽이 하나라도
 * 실패하면 트랜잭션 전체가 되돌아가 아무것도 지워지지 않는다. 듣는 차례는 {@code @Order} 로 정했다. 서로 가리키는 표가 없어
 * 차례가 맞물리지는 않지만, 같은 차례로 돌게 둔다.
 *
 * <pre>
 * 10 activity      그 사람의 하루 활동. 그 사람이 쓴 쉬는 날 카드는 오너가 쓴 것으로
 * 20 fitness       그 사람의 측정과 항목
 * 30 coaching      그 사람만 참여한 미션은 통째로, 함께 한 미션은 그 사람 몫만. 그 사람을 대상으로 짠 편성,
 *                  대화, 찜, 영상 기록. 미션을 만든 사람, 편성을 승인한 사람은 오너로
 * 40 progress      그 사람의 경험치와 업적. 남는 사람의 경험치 줄에서는 보낸 사람 칸만 비운다
 * 60 notification  그 사람이 받은 알림, 그 사람에 관한 알림, 그 사람이 보낸 알림, 지울 응원으로 만든 알림
 * </pre>
 *
 * 이벤트가 돌아오면 identity 가 응원, 운동할 수 있는 시간, 동의 이력, 프로필 행을 지운다. 그 사람이 낸 가족 초대는 오너가 낸
 * 것으로 돌린다.
 *
 * @param userId 그 프로필에 붙은 계정. 없으면 null. 탈퇴면 이 계정도 지우고, 내보내기면 계정은 남는다
 * @param ownerProfileId 이 가족의 오너 프로필. 남는 가족이 계속 쓰는 기록(미션, 쉬는 날 카드, 편성 승인 등)에서 지우는 사람을
 *     가리키던 칸을 이 프로필로 돌린다
 * @param cheerIds identity 가 지울 응원(그 사람이 보내거나 받은 응원과 그 응원에 단 답장). 알림이 이 응원을 가리키므로 notification 이
 *     먼저 지운다
 */
public record ProfileDeleting(
        UUID familyId, UUID profileId, @Nullable UUID userId, UUID ownerProfileId, List<UUID> cheerIds) {
    public ProfileDeleting {
        cheerIds = List.copyOf(cheerIds);
    }
}
