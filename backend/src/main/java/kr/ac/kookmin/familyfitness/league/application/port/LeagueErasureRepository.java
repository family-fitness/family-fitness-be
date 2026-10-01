package kr.ac.kookmin.familyfitness.league.application.port;

import java.util.UUID;

/** 가족을 지울 때 그 가족의 리그 참가 기록(league_members)을 지운다. 방(league_rounds)은 다른 가족도 있어 남긴다. */
public interface LeagueErasureRepository {
    void eraseFamily(UUID familyId);
}
