package kr.ac.kookmin.familyfitness.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * 프로필은 남기고 그 사람의 기록만 지우기 바로 전이다. 보호자가 아이의 동의를 거둘 때 낸다(PATCH /profiles/{profileId}/consent 에
 * false 가 하나라도 있음). 개인정보처리방침이 동의를 철회하면 지체 없이 파기한다고 약속해서다(개인정보 보호법 제37조).
 *
 * <p>{@link ProfileDeleting} 과 같은 트랜잭션 규칙과 같은 듣는 차례를 따른다. identity 가 동의를 거두는 트랜잭션 안에서 발행하고,
 * 아래 모듈이 같은 트랜잭션에서 동기로 듣고 그 사람의 행을 지운다. 하나라도 실패하면 동의를 거둔 것까지 모두 되돌아간다.
 *
 * <pre>
 * 10 activity      그 사람의 하루 활동
 * 20 fitness       그 사람의 측정과 항목
 * 30 coaching      그 사람만 참여한 미션은 통째로, 함께 한 미션은 그 사람 몫(참여, 칸 끝, 느낌)만. 그 사람을 대상으로 짠 편성과
 *                  제안. 그 편성으로 만든 미션에 남는 사람이 있으면 미션은 직접 만든 미션으로 남는다. 코치 대화, 영상 기록, 찜
 * 40 progress      그 사람의 경험치와 업적. 남는 사람의 경험치 줄에서는 보낸 사람 칸만 비운다
 * 60 notification  그 사람이 받은 알림, 그 사람에 관한 알림, 그 사람이 보낸 알림, 지울 응원으로 만든 알림
 * </pre>
 *
 * league 는 들을 것이 없다. 이번 달 달성률은 조회 때 남은 기록으로 다시 세고, 정산한 달은 정산 때 굳힌 값을 그대로 둔다. 이벤트가
 * 돌아오면 identity 가 응원과 그 사람의 운동할 수 있는 시간을 지운다. 프로필 행(이름, 생년월일, 성별, 가족)과 동의 이력
 * (consent_events)은 남긴다. 프로필에 적어 둔 키와 몸무게는 동의를 거둘 때 비운다.
 *
 * <p>아이 프로필만 대상이라(보호자 동의는 아이에게만 있다) 미션을 만든 사람, 편성을 승인한 사람처럼 보호자만 하는 일을 적은 칸은
 * 돌릴 것이 없다.
 *
 * @param cheerIds identity 가 지울 응원(그 사람이 보내거나 받은 응원과 그 응원에 단 답장). 알림이 이 응원을 가리키므로 notification 이
 *     먼저 지운다
 */
public record ProfileRecordsDeleting(UUID familyId, UUID profileId, List<UUID> cheerIds) {
    public ProfileRecordsDeleting {
        cheerIds = List.copyOf(cheerIds);
    }
}
