package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import org.jspecify.annotations.Nullable;

/**
 * 운동 영상 카탈로그 항목. 읽기 전용이다. 유튜브 영상(식별자 = YouTube 영상 id)은 V132 가, 공단 「국민체력100 동영상 정보」
 * 오픈API 영상(식별자 = 파일 이름, 예 0AUDLJ08S_00455)은 V161 이 채운다.
 * 유튜브 AI 영상은 영상 단위 길이 · 공간 · 소음 · 준비물 자료가 없어 그 칸이 null 이다. 공단 영상은 mp4 주소({@link #getMedia})로 튼다.
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
    private final VideoMedia media;
    private final @Nullable String citationLabel;

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
        this(
                videoId,
                title,
                channelName,
                channelType,
                durationSec,
                label,
                equipment,
                labeledBy,
                collectedAt,
                VideoMedia.NONE);
    }

    public ExerciseVideo(
            String videoId,
            String title,
            String channelName,
            String channelType,
            @Nullable Integer durationSec,
            VideoLabel label,
            @Nullable String equipment,
            String labeledBy,
            Instant collectedAt,
            VideoMedia media) {
        this(
                videoId,
                title,
                channelName,
                channelType,
                durationSec,
                label,
                equipment,
                labeledBy,
                collectedAt,
                media,
                null);
    }

    /**
     * @param citationLabel 근거로 들 때의 이름. 공단 영상만 AI 표 값(예: 「국민체력100 운동처방동영상 · 걷기」, V165)이 있고, 없으면 null
     */
    public ExerciseVideo(
            String videoId,
            String title,
            String channelName,
            String channelType,
            @Nullable Integer durationSec,
            VideoLabel label,
            @Nullable String equipment,
            String labeledBy,
            Instant collectedAt,
            VideoMedia media,
            @Nullable String citationLabel) {
        this.videoId = videoId;
        this.title = title;
        this.channelName = channelName;
        this.channelType = channelType;
        this.durationSec = durationSec;
        this.label = label;
        this.equipment = equipment;
        this.labeledBy = labeledBy;
        this.collectedAt = collectedAt;
        this.media = media;
        this.citationLabel = citationLabel;
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

    /** 공단 영상은 mp4 주소, 유튜브 영상은 보기 주소. */
    public String getUrl() {
        String mediaUrl = media.mediaUrl();
        return mediaUrl != null ? mediaUrl : youtubeUrl(videoId);
    }

    /** 공단 영상은 첫 장면 이미지, 유튜브 영상은 유튜브 썸네일. */
    public String getThumbnailUrl() {
        String thumbnailUrl = media.thumbnailUrl();
        return thumbnailUrl != null ? thumbnailUrl : "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
    }

    /** AI 가 이 영상을 근거로 들 때 쓰는 이름. 공단 영상만 있다(V165). */
    public @Nullable String getCitationLabel() {
        return citationLabel;
    }

    /** 유튜브가 아닌 영상의 주소. 유튜브 영상은 {@link VideoMedia#NONE}. */
    public VideoMedia getMedia() {
        return media;
    }

    /** 영상 표에 없는 영상의 주소. 표에 없으면 유튜브 영상으로 본다. */
    public static String youtubeUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
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
