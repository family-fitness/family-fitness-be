package kr.ac.kookmin.familyfitness.coaching.domain

import kr.ac.kookmin.familyfitness.coaching.support.Videos
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class ExerciseVideoTest {
    @Test
    fun `연령 필터는 라벨 범위와 교차하는 영상만 통과시킨다`() {
        val youth = Videos.video("a", 7, 12)
        val family = Videos.video("b", 4, 64)
        val adult = Videos.video("c", 19, 64)

        assertThat(youth.matches(AgeGroup.YOUTH, null)).isTrue()
        assertThat(family.matches(AgeGroup.YOUTH, null)).isTrue()
        assertThat(adult.matches(AgeGroup.YOUTH, null)).isFalse()
        assertThat(adult.matches(AgeGroup.ADULT, null)).isTrue()
        assertThat(youth.matches(AgeGroup.TODDLER, null)).isFalse()
    }

    @Test
    fun `라벨 없는 영상은 아이 연령대에는 나가지 않고 성인·어르신에게는 나간다`() {
        val unlabeled = Videos.video("u", null, null)

        assertThat(unlabeled.matches(AgeGroup.TODDLER, null)).isFalse()
        assertThat(unlabeled.matches(AgeGroup.YOUTH, null)).isFalse()
        assertThat(unlabeled.matches(AgeGroup.ADOLESCENT, null)).isFalse()
        assertThat(unlabeled.matches(AgeGroup.ADULT, null)).isTrue()
        assertThat(unlabeled.matches(AgeGroup.SENIOR, null)).isTrue()
        assertThat(unlabeled.matches(null, null)).isTrue()
    }

    @Test
    fun `요인 필터는 CSV 라벨을 정확히 나눠 비교한다`() {
        val v = Videos.video("f", factors = "유연성,근지구력")

        assertThat(v.matches(null, "유연성")).isTrue()
        assertThat(v.matches(null, "근지구력")).isTrue()
        assertThat(v.matches(null, "근력")).isFalse()
        assertThat(VideoLabel.parseFactors(" 심폐지구력 , 순발력 ,")).containsExactly("심폐지구력", "순발력")
        assertThat(VideoLabel.parseFactors(null)).isEmpty()
    }

    @Test
    fun `배지는 조용함·좁은 공간 OK·준비물 없음 순서로 문구가 된다`() {
        assertThat(Videos.video("q").badges).containsExactly("조용함", "좁은 공간 OK", "준비물 없음")
        assertThat(Videos.video("n", noise = "NORMAL", space = "OUTDOOR", equipment = "매트").badges).isEmpty()
        assertThat(Videos.video("m", noise = "NORMAL", equipment = "매트").badges).containsExactly("좁은 공간 OK")
    }

    @Test
    fun `URL·썸네일·적립 분은 영상 id 와 길이에서 나온다`() {
        val v = Videos.video("IdpXx2gm90o", durationSec = 601)

        assertThat(v.url).isEqualTo("https://www.youtube.com/watch?v=IdpXx2gm90o")
        assertThat(v.thumbnailUrl).isEqualTo("https://i.ytimg.com/vi/IdpXx2gm90o/hqdefault.jpg")
        assertThat(v.creditMinutes).isEqualTo(11)
        assertThat(Videos.video("x", durationSec = null).creditMinutes).isEqualTo(0)
    }

    @Test
    fun `커서 페이지는 size 개를 자르고 더 있으면 마지막 id 를 다음 커서로 낸다`() {
        val ids = listOf("a", "b", "c", "d", "e")

        val first = CursorPage.of(ids, 2) { it }
        assertThat(first.items).containsExactly("a", "b")
        assertThat(first.nextCursor).isEqualTo("b")

        val last = CursorPage.of(ids.filter { it > "d" }, 2) { it }
        assertThat(last.items).containsExactly("e")
        assertThat(last.nextCursor).isNull()

        val exact = CursorPage.of(listOf("a", "b"), 2) { it }
        assertThat(exact.nextCursor).isNull()
        assertThrows<IllegalArgumentException> { CursorPage.of(ids, 0) { it } }
    }

    @Test
    fun `시청 진행률은 최대값만 남고 0_9 에 닿으면 한 번만 적립 대상이 된다`() {
        val at = Instant.parse("2026-09-09T01:00:00Z")
        val i = VideoInteraction.start(UUID.randomUUID(), UUID.randomUUID(), "v", at)

        i.watch(0.5, 300, at)
        assertThat(i.completed).isFalse()
        assertThat(i.creditable).isFalse()

        i.watch(0.95, 570, at.plusSeconds(1))
        assertThat(i.completed).isTrue()
        assertThat(i.creditable).isTrue()
        i.markCredited(at.plusSeconds(1))
        assertThat(i.creditable).isFalse()

        i.watch(0.3, 100, at.plusSeconds(2))
        assertThat(i.maxProgress).isEqualTo(0.95)
        assertThat(i.watchedSec).isEqualTo(570)
        assertThat(i.lastWatchedAt).isEqualTo(at.plusSeconds(2))
    }

    @Test
    fun `인용 없는 답변은 서버가 no_citation_generated 거부로 바꾼다`() {
        val at = Instant.parse("2026-09-09T01:00:00Z")
        val conv = UUID.randomUUID()
        val profile = UUID.randomUUID()

        val uncited =
            CoachMessage.assistant(
                UUID.randomUUID(),
                conv,
                profile,
                "답",
                emptyList(),
                refused = false,
                refusalReason = null,
                at = at,
            )
        assertThat(uncited.refused).isTrue()
        assertThat(uncited.refusalReason).isEqualTo("no_citation_generated")

        val cited =
            CoachMessage.assistant(
                UUID.randomUUID(),
                conv,
                profile,
                "답 [1]",
                listOf(MessageCitation(1, "라벨", "c", "라벨", null)),
                false,
                null,
                at,
            )
        assertThat(cited.refused).isFalse()
        assertThat(cited.citations).hasSize(1)

        val refused = CoachMessage.assistant(UUID.randomUUID(), conv, profile, "", emptyList(), true, "medical_query", at)
        assertThat(refused.refused).isTrue()
        assertThat(refused.refusalReason).isEqualTo("medical_query")
    }
}
