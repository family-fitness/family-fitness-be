package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSpan;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantSpan;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/** {@link MissionRepository} 의 JPA 구현. 미션 본문과 칸은 처음 저장할 때만 쓰고(불변), 참여자 행만 갱신된다. */
@Repository
public class MissionPersistenceAdapter implements MissionRepository {
    private final MissionJpaRepository missions;
    private final MissionParticipantJpaRepository participants;
    private final MissionSessionJpaRepository sessions;
    private final MissionSessionCompletionJpaRepository completions;
    private final ZoneId zone;

    public MissionPersistenceAdapter(
            MissionJpaRepository missions,
            MissionParticipantJpaRepository participants,
            MissionSessionJpaRepository sessions,
            MissionSessionCompletionJpaRepository completions,
            ZoneId appZone) {
        this.missions = missions;
        this.participants = participants;
        this.sessions = sessions;
        this.completions = completions;
        this.zone = appZone;
    }

    @Override
    public Mission save(Mission mission) {
        if (!missions.existsById(mission.getId())) {
            missions.save(toEntity(mission));
            sessions.saveAll(mission.getSessions().stream()
                    .map(it -> toEntity(it, mission.getId()))
                    .toList());
        }
        Map<UUID, MissionParticipantEntity> existing = new LinkedHashMap<>();
        participants
                .findByIdMissionId(mission.getId())
                .forEach(it -> existing.put(it.getId().getProfileId(), it));
        List<MissionParticipantEntity> rows = new ArrayList<>();
        for (MissionParticipant p : mission.getParticipants()) {
            MissionParticipantEntity entity = existing.get(p.getProfileId());
            if (entity == null) {
                rows.add(toEntity(p, mission.getId()));
            } else {
                applyFrom(entity, p);
                rows.add(entity);
            }
        }
        participants.saveAll(rows);
        return mission;
    }

    @Override
    public @Nullable Mission findById(UUID id) {
        MissionEntity entity = missions.findById(id).orElse(null);
        return entity == null
                ? null
                : toDomain(entity, participants.findByIdMissionId(id), sessions.findByIdMissionId(id));
    }

    @Override
    public @Nullable Mission findByIdForUpdate(UUID id) {
        MissionEntity entity = missions.findForUpdate(id).orElse(null);
        return entity == null
                ? null
                : toDomain(entity, participants.findByIdMissionId(id), sessions.findByIdMissionId(id));
    }

    /** 참여자 → 칸 → 미션 차례로 지운다(자식 행이 먼저). 칸 끝 행은 건드리지 않는다. */
    @Override
    public void delete(UUID id) {
        participants.deleteByMission(id);
        sessions.deleteByMission(id);
        missions.deleteOne(id);
    }

    @Override
    public List<Mission> findByFamily(UUID familyId) {
        return assemble(missions.findByFamilyId(familyId));
    }

    @Override
    public List<Mission> findOverlapping(UUID familyId, LocalDate from, LocalDate to) {
        return assemble(missions.findByFamilyIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(familyId, to, from));
    }

    @Override
    public int countByCoachRun(UUID coachRunId) {
        return (int) missions.countByCoachRunId(coachRunId);
    }

    /** 참여 행과 그 사람이 칸을 끝낸 날(completed_on)을 한 번씩 읽어 붙인다. */
    @Override
    public List<MissionSpan> spansOf(UUID profileId, LocalDate from, LocalDate to) {
        List<MissionSpanRow> rows = participants.findSpans(profileId, from, to);
        if (rows.isEmpty()) return List.of();
        Map<UUID, Set<LocalDate>> doneOnByMission = new HashMap<>();
        completions
                .findByIdProfileIdAndIdMissionIdIn(
                        profileId, rows.stream().map(MissionSpanRow::missionId).toList())
                .forEach(it -> doneOnByMission
                        .computeIfAbsent(it.getId().getMissionId(), k -> new HashSet<>())
                        .add(it.getCompletedOn()));
        return rows.stream()
                .map(it -> span(
                        it.startsOn(),
                        it.endsOn(),
                        it.targetMetric(),
                        it.status(),
                        it.progress(),
                        it.verifiedAt(),
                        doneOnByMission.getOrDefault(it.missionId(), Set.of())))
                .toList();
    }

    /** {@link #spansOf} 를 여러 프로필에 한 번에. 칸 끝낸 날도 (프로필 IN, 미션 IN) 한 번에 읽는다. */
    @Override
    public List<ParticipantSpan> participantSpansOf(Collection<UUID> profileIds, LocalDate from, LocalDate to) {
        if (profileIds.isEmpty()) return List.of();
        List<ParticipantSpanRow> rows = participants.findParticipantSpans(profileIds, from, to);
        if (rows.isEmpty()) return List.of();
        Map<UUID, Map<UUID, Set<LocalDate>>> doneOnByProfile = new HashMap<>();
        completions
                .findByIdProfileIdInAndIdMissionIdIn(
                        profileIds,
                        rows.stream()
                                .map(ParticipantSpanRow::missionId)
                                .distinct()
                                .toList())
                .forEach(it -> doneOnByProfile
                        .computeIfAbsent(it.getId().getProfileId(), k -> new HashMap<>())
                        .computeIfAbsent(it.getId().getMissionId(), k -> new HashSet<>())
                        .add(it.getCompletedOn()));
        return rows.stream()
                .map(it -> new ParticipantSpan(
                        it.profileId(),
                        span(
                                it.startsOn(),
                                it.endsOn(),
                                it.targetMetric(),
                                it.status(),
                                it.progress(),
                                it.verifiedAt(),
                                doneOnByProfile
                                        .getOrDefault(it.profileId(), Map.of())
                                        .getOrDefault(it.missionId(), Set.of())),
                        it.missionCreatedAt()))
                .toList();
    }

    /** 참여 행 하나 + 그 사람이 칸을 끝낸 날 → 그 사람 입장의 미션 기간 · 진행({@link MissionSpan#of}). */
    private MissionSpan span(
            LocalDate startsOn,
            LocalDate endsOn,
            String targetMetric,
            String status,
            BigDecimal progress,
            @Nullable Instant verifiedAt,
            Set<LocalDate> doneOn) {
        return MissionSpan.of(
                startsOn,
                endsOn,
                TargetMetric.valueOf(targetMetric),
                ParticipantStatus.valueOf(status) == ParticipantStatus.COMPLETED,
                progress.signum() > 0,
                verifiedAt,
                doneOn,
                zone);
    }

    /** 참여자 · 칸을 missionId IN 으로 한 번씩만 읽는다(미션마다 따로 읽지 않는다). */
    private List<Mission> assemble(List<MissionEntity> entities) {
        if (entities.isEmpty()) return List.of();
        List<UUID> ids = entities.stream().map(MissionEntity::getId).toList();
        Map<UUID, List<MissionParticipantEntity>> participantsByMission = new LinkedHashMap<>();
        participants.findByIdMissionIdIn(ids).forEach(it -> participantsByMission
                .computeIfAbsent(it.getId().getMissionId(), k -> new ArrayList<>())
                .add(it));
        Map<UUID, List<MissionSessionEntity>> sessionsByMission = new LinkedHashMap<>();
        sessions.findByIdMissionIdIn(ids).forEach(it -> sessionsByMission
                .computeIfAbsent(it.getId().getMissionId(), k -> new ArrayList<>())
                .add(it));
        return entities.stream()
                .map(it -> toDomain(
                        it,
                        participantsByMission.getOrDefault(it.getId(), List.of()),
                        sessionsByMission.getOrDefault(it.getId(), List.of())))
                .toList();
    }

    private static MissionEntity toEntity(Mission mission) {
        MissionVideo video = mission.getVideo();
        return new MissionEntity(
                mission.getId(),
                mission.getFamilyId(),
                mission.getCoachRunId(),
                mission.getTitle(),
                mission.getDescription(),
                mission.getOrigin().name(),
                mission.getTargetMetric().name(),
                mission.getTargetValue(),
                video == null ? null : video.videoId(),
                video == null ? null : video.startSec(),
                mission.getRationale(),
                mission.getStartsOn(),
                mission.getEndsOn(),
                mission.getCreatedBy(),
                mission.getCreatedAt());
    }

    private static MissionParticipantEntity toEntity(MissionParticipant participant, UUID missionId) {
        VerifiedBy verifiedBy = participant.getVerifiedBy();
        return new MissionParticipantEntity(
                new MissionParticipantId(missionId, participant.getProfileId()),
                participant.getStatus().name(),
                ratio(participant.getProgress()),
                verifiedBy == null ? null : verifiedBy.name(),
                participant.getVerifiedAt(),
                participant.getConfirmedBy(),
                participant.getCoachRole(),
                participant.getUpdatedAt());
    }

    private static MissionSessionEntity toEntity(MissionSession session, UUID missionId) {
        SessionClip clip = session.clip();
        FitnessFactor factor = session.factor();
        return new MissionSessionEntity(
                new MissionSessionId(missionId, session.position()),
                session.phase().name(),
                session.title(),
                factor == null ? null : factor.getLabel(),
                session.minutes(),
                clip == null ? null : clip.videoId(),
                clip == null ? null : clip.startSec(),
                clip == null ? null : clip.endSec(),
                clip == null ? null : clip.title());
    }

    private static MissionSession toSession(MissionSessionEntity e) {
        String videoId = e.getVideoId();
        Integer startSec = e.getStartSec();
        String factor = e.getFactor();
        return new MissionSession(
                e.getId().getPosition(),
                SessionPhase.valueOf(e.getPhase()),
                e.getTitle(),
                factor == null ? null : FitnessFactor.fromLabel(factor),
                e.getMinutes(),
                videoId == null || startSec == null
                        ? null
                        : new SessionClip(videoId, startSec, e.getEndSec(), e.getClipTitle()));
    }

    private static void applyFrom(MissionParticipantEntity entity, MissionParticipant p) {
        VerifiedBy verifiedBy = p.getVerifiedBy();
        entity.setStatus(p.getStatus().name());
        entity.setProgress(ratio(p.getProgress()));
        entity.setVerifiedBy(verifiedBy == null ? null : verifiedBy.name());
        entity.setVerifiedAt(p.getVerifiedAt());
        entity.setConfirmedByProfileId(p.getConfirmedBy());
        entity.setUpdatedAt(p.getUpdatedAt());
    }

    private static Mission toDomain(MissionEntity e, List<MissionParticipantEntity> ps, List<MissionSessionEntity> ss) {
        String videoId = e.getVideoId();
        return Mission.reconstitute(
                e.getId(),
                e.getFamilyId(),
                e.getCoachRunId(),
                e.getTitle(),
                e.getDescription(),
                MissionOrigin.valueOf(e.getOrigin()),
                TargetMetric.valueOf(e.getTargetMetric()),
                e.getTargetValue(),
                videoId == null ? null : new MissionVideo(videoId, e.getVideoStartSec()),
                e.getRationale(),
                e.getStartsOn(),
                e.getEndsOn(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                ps.stream()
                        .map(p -> MissionParticipant.reconstitute(
                                p.getId().getProfileId(),
                                p.getCoachRole(),
                                p.getProgress().doubleValue(),
                                ParticipantStatus.valueOf(p.getStatus()),
                                p.getVerifiedBy() == null ? null : VerifiedBy.valueOf(p.getVerifiedBy()),
                                p.getVerifiedAt(),
                                p.getConfirmedByProfileId(),
                                p.getUpdatedAt()))
                        .toList(),
                ss.stream().map(MissionPersistenceAdapter::toSession).toList());
    }

    /** 도메인이 비교에 쓰는 정밀도와 같은 규칙으로 쓴다(MissionParticipant.storedProgress). */
    private static BigDecimal ratio(double value) {
        return MissionParticipant.storedProgress(value);
    }
}
