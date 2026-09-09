package kr.ac.kookmin.familyfitness.activity.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals
import kr.ac.kookmin.familyfitness.activity.application.port.ActivityDailyRepository
import kr.ac.kookmin.familyfitness.activity.domain.DailyActivityRecord
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** `activity_daily` — unique(profile_id, activity_date, source). */
@Entity
@Table(name = "activity_daily")
class ActivityDailyEntity(
    @Id
    @Column(name = "id")
    var id: UUID,
    @Column(name = "profile_id", nullable = false)
    var profileId: UUID,
    @Column(name = "activity_date", nullable = false)
    var activityDate: LocalDate,
    @Column(name = "source", nullable = false, length = 10)
    var source: String,
    @Column(name = "steps", nullable = false)
    var steps: Int,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "active_minutes", nullable = false)
    var activeMinutes: Int,
    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Instant,
)

/** JPQL 합계 결과. 행이 없으면 sum 이 null 이라 nullable 로 받는다. */
data class ActivitySums(
    val steps: Long?,
    val activeMinutes: Long?,
    val verifiedMinutes: Long?,
)

interface ActivityDailyJpaRepository : JpaRepository<ActivityDailyEntity, UUID> {
    fun findByProfileIdAndActivityDateAndSource(
        profileId: UUID,
        activityDate: LocalDate,
        source: String,
    ): ActivityDailyEntity?

    @Query(
        """
        select new kr.ac.kookmin.familyfitness.activity.adapter.out.persistence.ActivitySums(
            sum(a.steps),
            sum(a.activeMinutes),
            sum(case when a.source in :verifiedSources then a.activeMinutes else 0 end))
        from ActivityDailyEntity a
        where a.profileId = :profileId and a.activityDate between :from and :to
        """,
    )
    fun sumBetween(
        profileId: UUID,
        from: LocalDate,
        to: LocalDate,
        verifiedSources: Collection<String>,
    ): ActivitySums

    @Query("select coalesce(sum(a.activeMinutes), 0) from ActivityDailyEntity a where a.profileId = :profileId and a.activityDate = :date")
    fun sumActiveMinutesOn(
        profileId: UUID,
        date: LocalDate,
    ): Long
}

@Repository
@Transactional(readOnly = true)
class ActivityDailyRepositoryAdapter(
    private val jpa: ActivityDailyJpaRepository,
) : ActivityDailyRepository {
    private val verifiedSources = ActivitySource.entries.filter { it.serverVerified }.map { it.name }

    override fun find(
        profileId: UUID,
        activityDate: LocalDate,
        source: ActivitySource,
    ): DailyActivityRecord? = jpa.findByProfileIdAndActivityDateAndSource(profileId, activityDate, source.name)?.toDomain()

    @Transactional
    override fun save(record: DailyActivityRecord): DailyActivityRecord {
        val entity =
            jpa.findById(record.id).orElse(null)?.apply {
                steps = record.steps
                activeMinutes = record.activeMinutes
                recordedAt = record.recordedAt
            } ?: record.toEntity()
        jpa.save(entity)
        return record
    }

    override fun totals(
        profileId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): ActivityTotals {
        val sums = jpa.sumBetween(profileId, from, to, verifiedSources)
        return ActivityTotals(
            steps = (sums.steps ?: 0L).toInt(),
            activeMinutes = (sums.activeMinutes ?: 0L).toInt(),
            verifiedMinutes = (sums.verifiedMinutes ?: 0L).toInt(),
        )
    }

    override fun activeMinutesOn(
        profileId: UUID,
        activityDate: LocalDate,
    ): Int = jpa.sumActiveMinutesOn(profileId, activityDate).toInt()

    private fun ActivityDailyEntity.toDomain() =
        DailyActivityRecord(
            id = id,
            profileId = profileId,
            activityDate = activityDate,
            source = ActivitySource.valueOf(source),
            steps = steps,
            activeMinutes = activeMinutes,
            recordedAt = recordedAt,
        )

    private fun DailyActivityRecord.toEntity() =
        ActivityDailyEntity(
            id = id,
            profileId = profileId,
            activityDate = activityDate,
            source = source.name,
            steps = steps,
            activeMinutes = activeMinutes,
            recordedAt = recordedAt,
        )
}
