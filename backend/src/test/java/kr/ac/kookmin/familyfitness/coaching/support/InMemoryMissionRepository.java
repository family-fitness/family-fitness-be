package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSpan;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantSpan;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;

/** 미션 메모리 저장소. 칸 끝 저장소를 넘기면 잡힌 날 셈(spansOf)이 칸을 끝낸 날을 읽는다. */
public class InMemoryMissionRepository implements MissionRepository {
    public final ConcurrentHashMap<UUID, Mission> missions = new ConcurrentHashMap<>();
    public int saveCount = 0;
    private final InMemorySessionCompletionRepository completions;

    public InMemoryMissionRepository() {
        this(new InMemorySessionCompletionRepository());
    }

    public InMemoryMissionRepository(InMemorySessionCompletionRepository completions) {
        this.completions = completions;
    }

    @Override
    public Mission save(Mission mission) {
        saveCount++;
        missions.put(mission.getId(), mission);
        return mission;
    }

    @Override
    public @Nullable Mission findById(UUID id) {
        return missions.get(id);
    }

    @Override
    public @Nullable Mission findByIdForUpdate(UUID id) {
        return missions.get(id);
    }

    /** DB 처럼 칸 끝 기록이 남아 있으면 외래 키 위반을 던진다. */
    @Override
    public void delete(UUID id) {
        if (!completions.findByMission(id).isEmpty()) {
            throw new DataIntegrityViolationException("fk_mission_session_completions_mission: " + id);
        }
        missions.remove(id);
    }

    @Override
    public List<Mission> findByFamily(UUID familyId) {
        return missions.values().stream()
                .filter(it -> it.getFamilyId().equals(familyId))
                .toList();
    }

    @Override
    public List<Mission> findOverlapping(UUID familyId, LocalDate from, LocalDate to) {
        return findByFamily(familyId).stream()
                .filter(it -> it.overlaps(from, to))
                .toList();
    }

    @Override
    public int countByCoachRun(UUID coachRunId) {
        return (int) missions.values().stream()
                .filter(it -> coachRunId.equals(it.getCoachRunId()))
                .count();
    }

    @Override
    public List<MissionSpan> spansOf(UUID profileId, LocalDate from, LocalDate to) {
        return missions.values().stream()
                .filter(it -> it.isParticipant(profileId) && it.overlaps(from, to))
                .map(it -> spanOf(it, profileId))
                .toList();
    }

    @Override
    public List<ParticipantSpan> participantSpansOf(Collection<UUID> profileIds, LocalDate from, LocalDate to) {
        return profileIds.stream()
                .distinct()
                .flatMap(profileId -> missions.values().stream()
                        .filter(it -> it.isParticipant(profileId) && it.overlaps(from, to))
                        .map(it -> new ParticipantSpan(profileId, spanOf(it, profileId), it.getCreatedAt())))
                .toList();
    }

    private MissionSpan spanOf(Mission mission, UUID profileId) {
        MissionParticipant me = mission.participantOf(profileId);
        Set<LocalDate> doneOn = completions.findByMission(mission.getId()).stream()
                .filter(c -> c.profileId().equals(profileId))
                .map(c -> c.completedOn())
                .collect(Collectors.toSet());
        if (doneOn.isEmpty() && me.isCompleted() && me.getVerifiedAt() != null) {
            doneOn = Set.of(LocalDate.ofInstant(me.getVerifiedAt(), ZoneId.of("Asia/Seoul")));
        }
        return new MissionSpan(
                mission.getStartsOn(),
                mission.getEndsOn(),
                mission.getTargetMetric(),
                !doneOn.isEmpty() || me.isCompleted() || me.getProgress() > 0,
                doneOn);
    }
}
