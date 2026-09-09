package kr.ac.kookmin.familyfitness.coaching.domain

import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import java.time.Instant
import kotlin.math.ceil

/** 연령대의 만 나이 범위. 영상 라벨의 age_from~age_to 와 교차 여부를 판단할 때 쓴다. */
data class AgeRange(
    val from: Int,
    val to: Int,
) {
    fun intersects(
        otherFrom: Int,
        otherTo: Int,
    ): Boolean = otherFrom <= to && otherTo >= from

    companion object {
        fun of(ageGroup: AgeGroup): AgeRange =
            when (ageGroup) {
                AgeGroup.TODDLER -> AgeRange(0, 6)
                AgeGroup.YOUTH -> AgeRange(7, 12)
                AgeGroup.ADOLESCENT -> AgeRange(13, 18)
                AgeGroup.ADULT -> AgeRange(19, 64)
                AgeGroup.SENIOR -> AgeRange(65, 120)
            }

        /** 라벨 없는 영상을 내보내지 않는 「아이 연령대」. */
        fun isChildGroup(ageGroup: AgeGroup): Boolean =
            ageGroup == AgeGroup.TODDLER || ageGroup == AgeGroup.YOUTH || ageGroup == AgeGroup.ADOLESCENT
    }
}

/** 영상 라벨(AI 라벨링 결과). 값 객체. */
data class VideoLabel(
    val ageFrom: Int?,
    val ageTo: Int?,
    val factors: List<String>,
    val intensity: String?,
    val space: String?,
    val noise: String?,
    val model: String?,
) {
    /** 안전 필터: 라벨 연령 범위와 교차하는 영상만. 라벨 없는 영상은 아이 연령대에 나가지 않는다. */
    fun suitableFor(ageGroup: AgeGroup): Boolean {
        if (ageFrom == null || ageTo == null) return !AgeRange.isChildGroup(ageGroup)
        return AgeRange.of(ageGroup).intersects(ageFrom, ageTo)
    }

    fun hasFactor(factorLabel: String): Boolean = factors.contains(factorLabel)

    companion object {
        const val NOISE_QUIET = "QUIET"
        const val SPACE_SMALL_ROOM = "SMALL_ROOM"

        fun parseFactors(csv: String?): List<String> = csv?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }
}

/** 운동 영상 카탈로그 항목. 식별자는 YouTube 영상 id. 읽기 전용(수집 배치가 채운다). */
class ExerciseVideo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelType: String,
    val durationSec: Int?,
    val label: VideoLabel,
    val equipment: String?,
    val labeledBy: String,
    val collectedAt: Instant,
) {
    val url: String get() = "https://www.youtube.com/watch?v=$videoId"
    val thumbnailUrl: String get() = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    /** 화면 배지. 코드값 대신 문구로 내보낸다: 조용함 · 좁은 공간 OK · 준비물 없음. */
    val badges: List<String>
        get() =
            buildList {
                if (label.noise == VideoLabel.NOISE_QUIET) add(BADGE_QUIET)
                if (label.space == VideoLabel.SPACE_SMALL_ROOM) add(BADGE_SMALL_ROOM)
                if (equipment == null) add(BADGE_NO_EQUIPMENT)
            }

    /** 완주 시 적립하는 활동 분(영상 길이, 분 올림). 길이를 모르면 0. */
    val creditMinutes: Int get() = durationSec?.let { ceil(it / 60.0).toInt() } ?: 0

    fun matches(
        ageGroup: AgeGroup?,
        factor: String?,
    ): Boolean = (ageGroup == null || label.suitableFor(ageGroup)) && (factor == null || label.hasFactor(factor))

    companion object {
        const val BADGE_QUIET = "조용함"
        const val BADGE_SMALL_ROOM = "좁은 공간 OK"
        const val BADGE_NO_EQUIPMENT = "준비물 없음"
    }
}

/** 커서 페이지. 정렬된 후보 목록에서 `size` 개를 자르고 다음 커서(마지막 id)를 낸다. */
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
) {
    companion object {
        fun <T> of(
            sorted: List<T>,
            size: Int,
            idOf: (T) -> String,
        ): CursorPage<T> {
            require(size > 0) { "size 는 1 이상이어야 한다" }
            val page = sorted.take(size)
            val next = if (sorted.size > size) idOf(page.last()) else null
            return CursorPage(page, next)
        }
    }
}
