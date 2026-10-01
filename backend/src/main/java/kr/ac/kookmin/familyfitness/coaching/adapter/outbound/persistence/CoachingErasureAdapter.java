package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachingErasureRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class CoachingErasureAdapter implements CoachingErasureRepository {
    private static final TypeReference<List<ProposalParticipant>> PARTICIPANTS = new TypeReference<>() {};

    private final CoachingErasureJpaRepository rows;
    private final JsonMapper jsonMapper;

    public CoachingErasureAdapter(CoachingErasureJpaRepository rows, JsonMapper jsonMapper) {
        this.rows = rows;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public List<UUID> soloMissionsOf(UUID familyId, UUID profileId) {
        return rows.findSoloMissions(familyId, profileId);
    }

    @Override
    public void deleteMissions(Collection<UUID> missionIds) {
        if (missionIds.isEmpty()) return;
        rows.deleteFeedbackOfMissions(missionIds);
        rows.deleteCompletionsOfMissions(missionIds);
        rows.deleteParticipantsOfMissions(missionIds);
        rows.deleteSessionsOfMissions(missionIds);
        rows.deleteMissions(missionIds);
    }

    @Override
    public void leaveMissions(UUID profileId, UUID heirProfileId) {
        dropParticipation(profileId);
        rows.handOverConfirmations(profileId, heirProfileId);
        rows.handOverMissions(profileId, heirProfileId);
    }

    @Override
    public void dropParticipation(UUID profileId) {
        rows.deleteFeedbackOf(profileId);
        rows.deleteCompletionsOf(profileId);
        rows.deleteParticipantRowsOf(profileId);
    }

    @Override
    public void deleteRunsAbout(UUID profileId) {
        deleteRuns(rows.findRunsAbout(profileId));
    }

    @Override
    public void forgetInRuns(UUID familyId, UUID profileId, UUID heirProfileId) {
        rows.forgetRequester(profileId);
        rows.handOverApprovals(profileId, heirProfileId);
        dropFromProposals(familyId, profileId);
    }

    @Override
    public void dropFromProposals(UUID familyId, UUID profileId) {
        for (CoachRunProposalItemEntity item : rows.findProposalItemsOfFamily(familyId)) {
            List<ProposalParticipant> participants = jsonMapper.readValue(item.getParticipantsJson(), PARTICIPANTS);
            List<ProposalParticipant> kept = participants.stream()
                    .filter(it -> !it.profileId().equals(profileId))
                    .toList();
            if (kept.size() == participants.size()) continue;
            rows.rewriteParticipants(
                    item.getId().getCoachRunId(), item.getId().getPosition(), jsonMapper.writeValueAsString(kept));
        }
    }

    @Override
    public void erasePersonal(Collection<UUID> profileIds) {
        if (profileIds.isEmpty()) return;
        rows.deleteCitations(profileIds);
        rows.deleteMessages(profileIds);
        rows.deleteVideoInteractions(profileIds);
        rows.deleteExerciseFavorites(profileIds);
    }

    @Override
    public void eraseFamily(UUID familyId) {
        deleteMissions(rows.findMissionsOfFamily(familyId));
        deleteRuns(rows.findRunsOfFamily(familyId));
    }

    /** 미션이 편성을 가리키므로(fk_missions_coach_run) 남은 미션의 연결을 먼저 끊고, 제안 칸, 제안 항목, 편성 차례로 지운다. */
    private void deleteRuns(List<UUID> runIds) {
        if (runIds.isEmpty()) return;
        rows.detachMissionsFromRuns(runIds);
        rows.deleteProposalSessions(runIds);
        rows.deleteProposalItems(runIds);
        rows.deleteRuns(runIds);
    }
}
