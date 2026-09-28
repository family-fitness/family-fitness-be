package kr.ac.kookmin.familyfitness.coaching.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 한 사람이 미션의 칸 하나를 처음 끝냈다(결정 42). 아이가 끝낸 칸이 같이 하기로 한 보호자에게 번지면 그 보호자 몫도 따로 낸다.
 * 같은 칸을 다시 보내면(이미 끝낸 칸) 내지 않는다.
 *
 * @param completedOn 서버가 받은 날(KST)
 * @param missionCompleted 이 칸으로 이 사람의 미션이 끝났는가(참여자가 완료 상태)
 */
public record SessionCompleted(
        UUID familyId,
        UUID missionId,
        int position,
        UUID profileId,
        LocalDate completedOn,
        Instant completedAt,
        boolean missionCompleted) {}
