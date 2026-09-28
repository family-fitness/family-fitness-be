package kr.ac.kookmin.familyfitness.progress.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * GET /profiles/{profileId}/progress 응답. 모양은 FE 요청서 3장 · fe:src/lib/api/types.ts ProgressView 그대로다.
 *
 * @param levelFloorXp 이 레벨이 시작된 경험치
 * @param nextLevelXp 다음 레벨이 되는 경험치. 마지막 레벨이면 null
 * @param streakDays 이어서 한 날. 오늘 아직이면 어제까지로 센다
 * @param activeDays 지금까지 운동한 날(서버가 잰 분이 있는 날). 기간 제한 없이 세며 줄지 않는다
 * @param achievements 업적 열두 개 전부, 정해진 차례로. 아직이면 earnedAt 이 null
 * @param recentXp 최근에 경험치가 들어온 까닭 다섯 줄까지, 최근 것부터
 */
public record ProgressView(
        UUID profileId,
        int level,
        int xp,
        int levelFloorXp,
        @Nullable Integer nextLevelXp,
        int streakDays,
        int activeDays,
        List<AchievementView> achievements,
        List<XpLineView> recentXp) {}
