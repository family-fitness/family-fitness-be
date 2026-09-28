package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSpan;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantSpan;
import org.jspecify.annotations.Nullable;

public class InMemoryMissionRepository implements MissionRepository {
    public final ConcurrentHashMap<UUID, Mission> missions = new ConcurrentHashMap<>();
    public int saveCount = 0;

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
                .map(it -> {
                    MissionParticipant me = it.participantOf(profileId);
                    return new MissionSpan(
                            it.getStartsOn(),
                            it.getEndsOn(),
                            it.getTargetMetric(),
                            me.isCompleted() || me.getProgress() > 0,
                            me.isCompleted() ? me.getVerifiedAt() : null);
                })
                .toList();
    }

    @Override
    public List<ParticipantSpan> participantSpansOf(Collection<UUID> profileIds, LocalDate from, LocalDate to) {
        return profileIds.stream()
                .distinct()
                .flatMap(profileId -> missions.values().stream()
                        .filter(it -> it.isParticipant(profileId) && it.overlaps(from, to))
                        .map(it -> {
                            MissionParticipant me = it.participantOf(profileId);
                            MissionSpan span = new MissionSpan(
                                    it.getStartsOn(),
                                    it.getEndsOn(),
                                    it.getTargetMetric(),
                                    me.isCompleted() || me.getProgress() > 0,
                                    me.isCompleted() ? me.getVerifiedAt() : null);
                            return new ParticipantSpan(profileId, span, it.getCreatedAt());
                        }))
                .toList();
    }
}
