package kr.ac.kookmin.familyfitness.activity

import jakarta.persistence.EntityManager
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.support.ProfileRows
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/** H2 + Flyway 위에서 `activity_daily` upsert 와 기간 합계를 실제 저장소로 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ActivityServiceTest {
    @Autowired lateinit var recorder: ActivityRecorder

    @Autowired lateinit var query: ActivityQuery

    @Autowired lateinit var rows: ProfileRows

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var em: EntityManager

    @MockitoBean lateinit var familyAccess: FamilyAccess

    @MockitoBean lateinit var profileQuery: ProfileQuery

    @MockitoBean lateinit var cheerQuery: CheerQuery

    @MockitoBean lateinit var ai: AiGateway

    private lateinit var profileId: UUID
    private val monday = LocalDate.of(2026, 9, 7)

    @BeforeEach
    fun setUp() {
        profileId = rows.profile(rows.family(), LocalDate.of(2017, 5, 1), Sex.M)
    }

    /** JPA 가 미룬 insert 를 내보낸 뒤 JDBC 로 실제 행 수를 센다. */
    private fun rowCount(): Int {
        em.flush()
        return jdbc.queryForObject("select count(*) from activity_daily where profile_id = ?", Int::class.java, profileId)!!
    }

    @Test
    fun `걸음수는 그날 MANUAL 한 행을 총량으로 덮어쓴다`() {
        val first = recorder.overwriteSteps(profileId, monday, 3000)
        assertThat(first.steps).isEqualTo(3000)
        assertThat(first.source).isEqualTo(ActivitySource.MANUAL)
        assertThat(first.activeMinutes).isEqualTo(0)

        val second = recorder.overwriteSteps(profileId, monday, 5000)
        assertThat(second.steps).isEqualTo(5000)
        assertThat(rowCount()).isEqualTo(1)
        assertThat(query.totals(profileId, monday, monday).steps).isEqualTo(5000)
    }

    @Test
    fun `분은 (날짜, 출처) 행에 누적되고 출처별로 행이 갈린다`() {
        assertThat(recorder.addActiveMinutes(profileId, monday, ActivitySource.TIMER, 10).activeMinutes).isEqualTo(10)
        assertThat(recorder.addActiveMinutes(profileId, monday, ActivitySource.TIMER, 15).activeMinutes).isEqualTo(25)
        val video = recorder.addActiveMinutes(profileId, monday, ActivitySource.VIDEO, 7)
        assertThat(video.activeMinutes).isEqualTo(7)
        assertThat(video.steps).isEqualTo(0)
        assertThat(rowCount()).isEqualTo(2)
        assertThat(query.activeMinutesOn(profileId, monday)).isEqualTo(32)
        assertThat(query.activeMinutesOn(profileId, monday.plusDays(1))).isEqualTo(0)
    }

    @Test
    fun `기간 합계는 양끝 포함이고 verifiedMinutes 는 TIMER·VIDEO 만 센다`() {
        recorder.overwriteSteps(profileId, monday, 4000)
        recorder.overwriteSteps(profileId, monday.plusDays(6), 6000)
        recorder.overwriteSteps(profileId, monday.plusDays(7), 9999)
        recorder.addActiveMinutes(profileId, monday.plusDays(2), ActivitySource.TIMER, 20)
        recorder.addActiveMinutes(profileId, monday.plusDays(6), ActivitySource.VIDEO, 12)
        recorder.addActiveMinutes(profileId, monday.minusDays(1), ActivitySource.VIDEO, 30)
        jdbc.update(
            "insert into activity_daily (id, profile_id, activity_date, source, steps, active_minutes, recorded_at) values (?, ?, ?, 'MANUAL', 0, 40, ?)",
            UUID.randomUUID(),
            profileId,
            monday.plusDays(3),
            java.time.Instant.parse("2026-09-10T00:00:00Z"),
        )

        val week = query.totals(profileId, monday, monday.plusDays(6))
        assertThat(week.steps).isEqualTo(10000)
        assertThat(week.activeMinutes).isEqualTo(72)
        assertThat(week.verifiedMinutes).isEqualTo(32)

        val empty = query.totals(UUID.randomUUID(), monday, monday.plusDays(6))
        assertThat(empty.steps).isEqualTo(0)
        assertThat(empty.activeMinutes).isEqualTo(0)
        assertThat(empty.verifiedMinutes).isEqualTo(0)
    }

    @Test
    fun `값 규칙 — 걸음 0~100000, 분은 양수, 분 출처는 TIMER·VIDEO 만`() {
        assertThatThrownBy { recorder.overwriteSteps(profileId, monday, -1) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { recorder.overwriteSteps(profileId, monday, 100_001) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { recorder.addActiveMinutes(profileId, monday, ActivitySource.TIMER, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { recorder.addActiveMinutes(profileId, monday, ActivitySource.MANUAL, 10) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { query.totals(profileId, monday, monday.minusDays(1)) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(rowCount()).isEqualTo(0)
        recorder.overwriteSteps(profileId, monday, 100_000)
        recorder.overwriteSteps(profileId, monday, 0)
    }
}
