package kr.ac.kookmin.familyfitness.notification.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 스티커 코드 → 이름(스티커와 같이 가는 한마디). FE 스티커 표(fe:src/lib/stickers.ts STICKERS) 열두 장 그대로다.
 * 고마워요 알림의 본문과 「스티커를 붙여 줬어요」 / 「칭찬을 보냈어요」 를 가르는 데 쓴다. 모르는 코드는 지어내지 않고 null 이다.
 */
public final class StickerLabels {
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("star", "최고야"),
            Map.entry("thumb", "엄지척"),
            Map.entry("medal", "멋져"),
            Map.entry("heart", "사랑해"),
            Map.entry("crown", "대단해"),
            Map.entry("flag", "끝까지 했네"),
            Map.entry("sprout", "쑥쑥 자라라"),
            Map.entry("sparkle", "반짝반짝"),
            Map.entry("clap", "짝짝짝"),
            Map.entry("rocket", "슝 빨라졌어"),
            Map.entry("sun", "오늘도 맑음"),
            Map.entry("kiumi", "꼭 안아 줄게"));

    private StickerLabels() {}

    /** 아는 스티커면 그 이름, 모르거나 없으면 null(fe stickerOf 와 같다). */
    public static @Nullable String labelOf(@Nullable String stickerId) {
        return stickerId == null ? null : LABELS.get(stickerId);
    }
}
