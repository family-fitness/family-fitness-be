package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.activity.api.ActivitySource
import kr.ac.kookmin.familyfitness.coaching.domain.NotParticipantException
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy
import kr.ac.kookmin.familyfitness.coaching.domain.VideoNotFoundException
import kr.ac.kookmin.familyfitness.coaching.support.FakeActivity
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity
import kr.ac.kookmin.familyfitness.coaching.support.Family
import kr.ac.kookmin.familyfitness.coaching.support.Fixed
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryVideoInteractionRepository
import kr.ac.kookmin.familyfitness.coaching.support.Videos
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VideoServiceTest {
    private val family = Family()
    private val other = Family()
    private val identity = FakeIdentity(family, other)
    private val activity = FakeActivity()
    private val missions = InMemoryMissionRepository()
    private val interactions = InMemoryVideoInteractionRepository()
    private val videos = InMemoryExerciseVideoRepository(Videos.seed() + Videos.video("zzz_unlabeled", null, null, "근력"))
    private val policy = MissionCompletionPolicy(activity, interactions, missions)
    private val service = VideoService(videos, interactions, missions, identity, activity, policy, Fixed.time())
    private val childId = family.child.profileId

    private fun query(
        list: VideoListType = VideoListType.ALL,
        ageGroup: AgeGroup? = null,
        factor: String? = null,
        cursor: String? = null,
        size: Int = 20,
        withProfile: Boolean = true,
    ) = VideoListQuery(list, if (withProfile) childId else null, ageGroup, factor, cursor, size)

    @Test
    fun `전체 목록은 videoId 오름차순 커서 페이지이고 연령·요인 필터가 붙는다`() {
        val page1 = service.list(family.childUser, query(size = 2, withProfile = false))
        assertThat(page1.videos.map { it.videoId }).containsExactly("IdpXx2gm90o", "sample00002")
        assertThat(page1.nextCursor).isEqualTo("sample00002")

        val page2 = service.list(family.childUser, query(size = 2, cursor = page1.nextCursor, withProfile = false))
        assertThat(page2.videos.map { it.videoId }).containsExactly("sample00003", "sample00004")

        val youth = service.list(family.childUser, query(ageGroup = AgeGroup.YOUTH, withProfile = false))
        assertThat(youth.videos.map { it.videoId }).containsExactly("IdpXx2gm90o", "sample00002", "sample00003")
        assertThat(youth.nextCursor).isNull()

        val adultStrength = service.list(family.childUser, query(ageGroup = AgeGroup.ADULT, factor = "근력", withProfile = false))
        assertThat(adultStrength.videos.map { it.videoId }).containsExactly("sample00004", "zzz_unlabeled")

        val first = youth.videos.first()
        assertThat(first.url).endsWith("IdpXx2gm90o")
        assertThat(first.badges).contains("조용함")
        assertThat(first.favorited).isFalse()
        assertThat(first.maxProgress).isNull()
        assertThat(first.label.factors).containsExactly("유연성", "근지구력")
    }

    @Test
    fun `즐겨찾기·최근 목록은 profileId 가 필요하고 프로필의 상호작용만 본다`() {
        assertThrows<IllegalArgumentException> { service.list(family.childUser, query(VideoListType.FAVORITES, withProfile = false)) }
        assertThrows<NotSameFamilyException> { service.list(other.parentUser, query(VideoListType.FAVORITES)) }

        service.favorite(family.childUser, "sample00003", FavoriteCommand(childId, true))
        val fav = service.favorite(family.parentUser, "IdpXx2gm90o", FavoriteCommand(childId, true))
        assertThat(fav.favorited).isTrue()
        assertThat(fav.favoritedAt).isEqualTo(Fixed.NOW)
        assertThat(assertThrows<VideoNotFoundException> { service.favorite(family.childUser, "nope", FavoriteCommand(childId, true)) }.code)
            .isEqualTo("VIDEO_NOT_FOUND")

        val favorites = service.list(family.childUser, query(VideoListType.FAVORITES, size = 1))
        assertThat(favorites.videos.map { it.videoId }).containsExactly("IdpXx2gm90o")
        assertThat(favorites.videos.single().favorited).isTrue()
        assertThat(favorites.nextCursor).isEqualTo("IdpXx2gm90o")
        assertThat(service.list(family.childUser, query(VideoListType.FAVORITES, cursor = "IdpXx2gm90o")).videos.map { it.videoId })
            .containsExactly("sample00003")

        assertThat(service.list(family.childUser, query(VideoListType.RECENT)).videos).isEmpty()
        service.progress(family.childUser, "sample00002", VideoProgressCommand(childId, 0.2, 60, null))
        val later = VideoService(videos, interactions, missions, identity, activity, policy, Fixed.time(Fixed.NOW.plusSeconds(60)))
        later.progress(family.childUser, "sample00003", VideoProgressCommand(childId, 0.1, 30, null))
        val recent = service.list(family.childUser, query(VideoListType.RECENT))
        assertThat(recent.videos.map { it.videoId }).containsExactly("sample00003", "sample00002")
        assertThat(recent.videos.first().maxProgress).isEqualTo(0.1)
        assertThat(recent.nextCursor).isNull()

        service.favorite(family.childUser, "sample00003", FavoriteCommand(childId, false))
        assertThat(service.list(family.childUser, query(VideoListType.FAVORITES)).videos.map { it.videoId }).containsExactly("IdpXx2gm90o")
    }

    @Test
    fun `완주하면 영상 길이만큼 한 번만 적립하고 이후 보고는 0분이다`() {
        val half = service.progress(family.childUser, "IdpXx2gm90o", VideoProgressCommand(childId, 0.5, 300, null))
        assertThat(half.maxProgress).isEqualTo(0.5)
        assertThat(half.completed).isFalse()
        assertThat(half.creditedMinutes).isEqualTo(0)
        assertThat(half.verifiedBy).isNull()

        val done = service.progress(family.childUser, "IdpXx2gm90o", VideoProgressCommand(childId, 0.92, 560, null))
        assertThat(done.completed).isTrue()
        assertThat(done.creditedMinutes).isEqualTo(10)
        assertThat(done.verifiedBy).isEqualTo(VerifiedBy.VIDEO_PROGRESS)
        assertThat(activity.rows[FakeActivity.Key(childId, Fixed.TODAY, ActivitySource.VIDEO)]!!.activeMinutes).isEqualTo(10)

        val again = service.progress(family.childUser, "IdpXx2gm90o", VideoProgressCommand(childId, 1.0, 600, null))
        assertThat(again.creditedMinutes).isEqualTo(0)
        assertThat(again.maxProgress).isEqualTo(1.0)
        assertThat(activity.rows[FakeActivity.Key(childId, Fixed.TODAY, ActivitySource.VIDEO)]!!.activeMinutes).isEqualTo(10)
        assertThat(interactions.find(childId, "IdpXx2gm90o")!!.creditedAt).isEqualTo(Fixed.NOW)
    }

    @Test
    fun `missionId 가 있으면 VIDEO_DONE 미션의 참여자 진행도를 갱신한다`() {
        val missionService = MissionService(missions, videos, identity, identity, policy, Fixed.time())
        val mission =
            missionService.create(
                family.parentUser,
                family.familyId,
                CreateMissionCommand(
                    "영상 보기",
                    Fixed.WEEK_START,
                    Fixed.WEEK_START.plusDays(6),
                    TargetMetric.VIDEO_DONE,
                    1,
                    "IdpXx2gm90o",
                    listOf(childId),
                ),
            )

        val partial = service.progress(family.childUser, "IdpXx2gm90o", VideoProgressCommand(childId, 0.3, 100, mission.missionId))
        assertThat(partial.missionProgress).isEqualTo(0.0)

        val done = service.progress(family.childUser, "IdpXx2gm90o", VideoProgressCommand(childId, 0.95, 580, mission.missionId))
        assertThat(done.missionProgress).isEqualTo(1.0)
        val participant = missions.findById(mission.missionId)!!.participantOf(childId)
        assertThat(participant.completed).isTrue()
        assertThat(participant.verifiedBy).isEqualTo(VerifiedBy.VIDEO_PROGRESS)

        assertThat(
            assertThrows<NotParticipantException> {
                service.progress(
                    family.parentUser,
                    "IdpXx2gm90o",
                    VideoProgressCommand(family.parent.profileId, 1.0, 600, mission.missionId),
                )
            }.code,
        ).isEqualTo("NOT_PARTICIPANT")
    }
}
