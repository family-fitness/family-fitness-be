package kr.ac.kookmin.familyfitness.identity.domain;

import org.jspecify.annotations.Nullable;

/**
 * 계정이 어디서 왔는지(V174). 광고나 공유 링크에 붙인 utm 값(utm_source, utm_medium, utm_campaign)을 FE 가 기억했다가 구글 로그인
 * 요청에 실어 보낸다. 캠페인 이름이지 개인정보가 아니어서 계정 행에 두고, 처음 가입할 때만 남긴다.
 *
 * <p>값은 {@link #of} 로만 만든다. 앞뒤 공백과 제어 문자를 걷고, 비었으면 null, {@value #MAX_LENGTH}자를 넘으면 자른다. 셋 다 null 이면
 * {@link #isEmpty()} 다.
 */
public record SignupSource(
        @Nullable String source,
        @Nullable String medium,
        @Nullable String campaign) {
    public static final int MAX_LENGTH = 64;

    public static final SignupSource NONE = new SignupSource(null, null, null);

    public static SignupSource of(@Nullable String source, @Nullable String medium, @Nullable String campaign) {
        return new SignupSource(clean(source), clean(medium), clean(campaign));
    }

    public boolean isEmpty() {
        return source == null && medium == null && campaign == null;
    }

    private static @Nullable String clean(@Nullable String value) {
        if (value == null) return null;
        // 줄바꿈 같은 제어 문자는 집계 결과를 깨뜨리므로 걷는다
        String cleaned = value.replaceAll("\\p{Cntrl}", "").strip();
        if (cleaned.isEmpty()) return null;
        return cleaned.length() <= MAX_LENGTH ? cleaned : cleaned.substring(0, MAX_LENGTH);
    }
}
