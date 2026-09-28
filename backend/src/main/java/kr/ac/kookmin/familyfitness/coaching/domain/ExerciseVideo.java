package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import org.jspecify.annotations.Nullable;

/**
 * 운동 영상 카탈로그 항목. 식별자는 YouTube 영상 id. 읽기 전용(AI 영상은 V132 적재 마이그레이션이 채운다).
 * AI 영상은 영상 단위 길이 · 공간 · 소음 · 준비물 자료가 없어 그 칸이 null 이다.
 */
public class ExerciseVideo {
    public static final String BADGE_QUIET = "조용함";
    public static final String BADGE_SMALL_ROOM = "좁은 공간 OK";
    public static final String BADGE_NO_EQUIPMENT = "준비물 없음";

    private final String videoId;
    private final String title;
    private final String channelName;
    private final String channelType;
    private final @Nullable Integer durationSec;
    private final VideoLabel label;
    private final @Nullable String equipment;
    private final String labeledBy;
    private final Instant collectedAt;

    public ExerciseVideo(
            String videoId,
            String title,
            String channelName,
            String channelType,
            @Nullable Integer durationSec,
            VideoLabel label,
            @Nullable String equipment,
            String labeledBy,
            Instant collectedAt) {
        this.videoId = videoId;
        this.title = title;
        this.channelName = channelName;
        this.channelType = channelType;
        this.durationSec = durationSec;
        this.label = label;
        this.equipment = equipment;
        this.labeledBy = labeledBy;
        this.collectedAt = collectedAt;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getTitle() {
        return title;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getChannelType() {
        return channelType;
    }

    public @Nullable Integer getDurationSec() {
        return durationSec;
    }

    public VideoLabel getLabel() {
        return label;
    }

    public @Nullable String getEquipment() {
        return equipment;
    }

    public String getLabeledBy() {
        return labeledBy;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public String getUrl() {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    public String getThumbnailUrl() {
        return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
    }

    /** 화면 배지. 코드값 대신 문구로 내보낸다: 조용함 · 좁은 공간 OK · 준비물 없음. */
    public List<String> getBadges() {
        List<String> badges = new ArrayList<>();
        if (VideoLabel.NOISE_QUIET.equals(label.noise())) badges.add(BADGE_QUIET);
        if (VideoLabel.SPACE_SMALL_ROOM.equals(label.space())) badges.add(BADGE_SMALL_ROOM);
        if (equipment == null) badges.add(BADGE_NO_EQUIPMENT);
        return List.copyOf(badges);
    }

    /** 완주 시 적립하는 활동 분(영상 길이, 분 올림). 길이를 모르면 0. */
    public int getCreditMinutes() {
        return durationSec == null ? 0 : (int) Math.ceil(durationSec / 60.0);
    }

    public boolean matches(@Nullable AgeGroup ageGroup, @Nullable String factor) {
        return (ageGroup == null || label.suitableFor(ageGroup)) && (factor == null || label.hasFactor(factor));
    }
}
