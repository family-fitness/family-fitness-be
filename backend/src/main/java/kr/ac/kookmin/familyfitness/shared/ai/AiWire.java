package kr.ac.kookmin.familyfitness.shared.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * AI 서비스(FastAPI) 와이어 형식. 필드명은 계약 §5 의 snake_case 그대로다.
 * 도메인 DTO({@link AiGateway} 의 것)와는 {@link HttpAiGateway} 안에서만 오간다.
 */
final class AiWire {
    private AiWire() {}

    record ErrorEnvelope(@Nullable ErrorBody error) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record ErrorBody(@Nullable String code, @Nullable String message) {}
    }

    record ProfileBody(
            @JsonProperty("profile_ref") String profileRef,
            int age,
            @JsonProperty("age_unit") String ageUnit,
            String sex,
            @JsonProperty("height_cm") @Nullable Double heightCm,
            @JsonProperty("weight_kg") @Nullable Double weightKg,
            Map<String, Double> measurements) {
        static ProfileBody of(AiProfile p) {
            return new ProfileBody(
                    p.profileRef(), p.age(), p.ageUnit(), p.sex(), p.heightCm(), p.weightKg(), p.measurements());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CitationBody(
            int index,
            String label,
            @JsonProperty("chunk_id") String chunkId,
            @Nullable String url) {
        Citation toDomain() {
            return new Citation(index, label, chunkId, url);
        }
    }

    // ---- assessment ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AssessmentBody(
            @JsonProperty("input_level") String inputLevel,
            @JsonProperty("age_group") String ageGroup,
            @JsonProperty("child_scope") @Nullable ChildScope childScope,
            @JsonProperty("parent_scope") @Nullable ParentScope parentScope,
            @JsonProperty("low_sample") boolean lowSample,
            String disclaimer) {
        AssessmentBody {
            disclaimer = disclaimer == null ? "" : disclaimer;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record ChildScope(
                @JsonProperty("focus_one") @Nullable FocusOne focusOne) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record FocusOne(String factor, String copy) {
            FocusOne {
                copy = copy == null ? "" : copy;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record ParentScope(
                @Nullable String grade,
                @JsonProperty("peer_distribution") List<GradeRatio> peerDistribution,
                List<FactorScore> factors,
                Map<String, String> copy) {
            ParentScope {
                peerDistribution = peerDistribution == null ? List.of() : peerDistribution;
                factors = factors == null ? List.of() : factors;
                copy = copy == null ? Map.of() : copy;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record GradeRatio(String grade, double ratio) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record FactorScore(
                String factor,
                @JsonProperty("item_code") String itemCode,
                @JsonProperty("item_name") String itemName,
                @JsonProperty("item_label") String itemLabel,
                String unit,
                @Nullable Double value,
                @Nullable Double score,
                @Nullable Integer percentile,
                @Nullable String band,
                int n) {
            FactorScore {
                itemName = itemName == null ? "" : itemName;
                itemLabel = itemLabel == null ? "" : itemLabel;
                unit = unit == null ? "" : unit;
            }
        }

        AssessmentResponse toDomain() {
            FocusOne focusOne = childScope == null ? null : childScope.focusOne();
            return new AssessmentResponse(
                    inputLevel,
                    ageGroup,
                    new AssessmentResponse.ChildScope(
                            focusOne == null
                                    ? null
                                    : new AssessmentResponse.FocusOne(focusOne.factor(), focusOne.copy())),
                    new AssessmentResponse.ParentScope(
                            parentScope == null ? null : parentScope.grade(),
                            parentScope == null
                                    ? List.of()
                                    : parentScope.peerDistribution().stream()
                                            .map(it -> new AssessmentResponse.GradeRatio(it.grade(), it.ratio()))
                                            .toList(),
                            parentScope == null
                                    ? List.of()
                                    : parentScope.factors().stream()
                                            .map(it -> new AssessmentResponse.FactorScore(
                                                    it.factor(),
                                                    it.itemCode(),
                                                    it.itemName(),
                                                    it.itemLabel(),
                                                    it.unit(),
                                                    it.value(),
                                                    it.score(),
                                                    it.percentile(),
                                                    it.band(),
                                                    it.n()))
                                            .toList(),
                            parentScope == null ? Map.of() : parentScope.copy()),
                    lowSample,
                    disclaimer);
        }
    }

    // ---- trajectory ----

    record TrajectoryRequestBody(
            @JsonProperty("profile_ref") String profileRef,
            int age,
            @JsonProperty("age_unit") String ageUnit,
            String sex,
            @JsonProperty("height_cm") @Nullable Double heightCm,
            @JsonProperty("weight_kg") @Nullable Double weightKg,
            Map<String, Double> measurements,
            @JsonProperty("item_code") String itemCode,
            @JsonProperty("horizon_years") int horizonYears) {
        static TrajectoryRequestBody of(TrajectoryRequest r) {
            AiProfile it = r.profile();
            return new TrajectoryRequestBody(
                    it.profileRef(),
                    it.age(),
                    it.ageUnit(),
                    it.sex(),
                    it.heightCm(),
                    it.weightKg(),
                    it.measurements(),
                    r.itemCode(),
                    r.horizonYears());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TrajectoryBody(
            String basis,
            @JsonProperty("item_code") String itemCode,
            @JsonProperty("item_name") String itemName,
            String unit,
            List<BandBody> bands,
            String notice,
            @JsonProperty("low_sample") boolean lowSample) {
        TrajectoryBody {
            itemName = itemName == null ? "" : itemName;
            unit = unit == null ? "" : unit;
            bands = bands == null ? List.of() : bands;
            notice = notice == null ? "" : notice;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record BandBody(
                int age,
                @Nullable Double p10,
                @Nullable Double p50,
                @Nullable Double p90,
                int n) {}

        TrajectoryResponse toDomain() {
            return new TrajectoryResponse(
                    basis,
                    itemCode,
                    itemName,
                    unit,
                    bands.stream()
                            .map(it -> new TrajectoryResponse.Band(it.age(), it.p10(), it.p50(), it.p90(), it.n()))
                            .toList(),
                    notice,
                    lowSample);
        }
    }

    // ---- videos/search ----

    record VideoSearchRequestBody(
            @JsonProperty("age_group") String ageGroup,
            @JsonProperty("fitness_factors") List<String> fitnessFactors,
            @JsonProperty("exercise_names") List<String> exerciseNames,
            int k) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VideoSearchBody(
            List<HitBody> hits,
            @JsonProperty("filtered_out") @Nullable FilteredOut filteredOut) {
        VideoSearchBody {
            hits = hits == null ? List.of() : hits;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record HitBody(
                @JsonProperty("video_id") String videoId,
                @JsonProperty("start_sec") @Nullable Integer startSec,
                double score,
                @JsonProperty("matched_exercise_names") List<String> matchedExerciseNames,
                CitationBody citation) {
            HitBody {
                matchedExerciseNames = matchedExerciseNames == null ? List.of() : matchedExerciseNames;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record FilteredOut(
                @JsonProperty("age_group") int ageGroup,
                @JsonProperty("below_threshold") int belowThreshold) {}

        VideoSearchResponse toDomain() {
            return new VideoSearchResponse(
                    hits.stream()
                            .map(it -> new VideoSearchResponse.Hit(
                                    it.videoId(),
                                    it.startSec(),
                                    it.score(),
                                    it.matchedExerciseNames(),
                                    it.citation().toDomain()))
                            .toList(),
                    filteredOut == null ? 0 : filteredOut.ageGroup(),
                    filteredOut == null ? 0 : filteredOut.belowThreshold());
        }
    }

    // ---- coach/runs ----

    record CoachRunRequestBody(
            @JsonProperty("profile_refs") List<ProfileRefBody> profileRefs, Period period, Constraints constraints) {
        record ProfileRefBody(
                String ref,
                String role,
                int age,
                @JsonProperty("age_unit") String ageUnit,
                String sex,
                @JsonProperty("input_level") String inputLevel,
                @JsonProperty("height_cm") @Nullable Double heightCm,
                @JsonProperty("weight_kg") @Nullable Double weightKg,
                @Nullable Map<String, Double> measurements) {}

        record Period(@JsonProperty("start_date") String startDate, int weeks) {}

        record Constraints(
                @JsonProperty("days_per_week") int daysPerWeek,
                @JsonProperty("minutes_per_session") int minutesPerSession) {}

        static CoachRunRequestBody of(CoachRunRequest r) {
            return new CoachRunRequestBody(
                    r.profiles().stream()
                            .map(p -> {
                                AiProfile a = p.profile();
                                return new ProfileRefBody(
                                        a.profileRef(),
                                        p.role(),
                                        a.age(),
                                        a.ageUnit(),
                                        a.sex(),
                                        a.inputLevel(),
                                        a.heightCm(),
                                        a.weightKg(),
                                        a.measurements().isEmpty() ? null : a.measurements());
                            })
                            .toList(),
                    new Period(r.startDate(), r.weeks()),
                    new Constraints(r.daysPerWeek(), r.minutesPerSession()));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CoachRunAcceptedBody(
            @JsonProperty("run_id") String runId,
            String status,
            @JsonProperty("poll_after_ms") Integer pollAfterMs) {
        CoachRunAcceptedBody {
            status = status == null ? "running" : status;
            pollAfterMs = pollAfterMs == null ? 1500 : pollAfterMs;
        }

        CoachRunAccepted toDomain() {
            return new CoachRunAccepted(runId, status, pollAfterMs);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CoachRunResultBody(
            @JsonProperty("run_id") String runId,
            String status,
            List<StepBody> steps,
            @Nullable ProposalBody proposal,
            boolean refused,
            @JsonProperty("refusal_reason") @Nullable String refusalReason) {
        CoachRunResultBody {
            steps = steps == null ? List.of() : steps;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record StepBody(int seq, String name, String status, String summary) {
            StepBody {
                summary = summary == null ? "" : summary;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record ProposalBody(List<MissionBody> missions, List<CitationBody> citations) {
            ProposalBody {
                missions = missions == null ? List.of() : missions;
                citations = citations == null ? List.of() : citations;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record MissionBody(
                String title,
                PeriodBody period,
                List<ParticipantBody> participants,
                List<SessionBody> sessions,
                @Nullable CopyBody copy) {
            MissionBody {
                participants = participants == null ? List.of() : participants;
                sessions = sessions == null ? List.of() : sessions;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record PeriodBody(
                @JsonProperty("start_date") String startDate,
                @JsonProperty("end_date") String endDate) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record ParticipantBody(String ref, String role) {
            ParticipantBody {
                role = role == null ? "" : role;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record SessionBody(
                @JsonProperty("day_offset") int dayOffset,
                @JsonProperty("exercise_name") String exerciseName,
                @JsonProperty("fitness_factor") String fitnessFactor,
                @JsonProperty("duration_min") int durationMin,
                @Nullable VideoBody video,
                List<Integer> evidence) {
            SessionBody {
                exerciseName = exerciseName == null ? "" : exerciseName;
                fitnessFactor = fitnessFactor == null ? "" : fitnessFactor;
                evidence = evidence == null ? List.of() : evidence;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record VideoBody(
                @JsonProperty("video_id") String videoId,
                @JsonProperty("start_sec") @Nullable Integer startSec) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record CopyBody(String child, String parent) {
            CopyBody {
                child = child == null ? "" : child;
                parent = parent == null ? "" : parent;
            }
        }

        CoachRunResult toDomain() {
            return new CoachRunResult(
                    runId,
                    status,
                    steps.stream()
                            .map(it -> new CoachRunResult.Step(it.seq(), it.name(), it.status(), it.summary()))
                            .toList(),
                    proposal == null
                            ? null
                            : new CoachRunResult.Proposal(
                                    proposal.missions().stream()
                                            .map(m -> new CoachRunResult.Mission(
                                                    m.title(),
                                                    m.period().startDate(),
                                                    m.period().endDate(),
                                                    m.participants().stream()
                                                            .map(it -> new CoachRunResult.ParticipantRef(
                                                                    it.ref(), it.role()))
                                                            .toList(),
                                                    m.sessions().stream()
                                                            .map(s -> new CoachRunResult.Session(
                                                                    s.dayOffset(),
                                                                    s.exerciseName(),
                                                                    s.fitnessFactor(),
                                                                    s.durationMin(),
                                                                    s.video() == null
                                                                            ? null
                                                                            : new CoachRunResult.Video(
                                                                                    s.video()
                                                                                            .videoId(),
                                                                                    s.video()
                                                                                            .startSec()),
                                                                    s.evidence()))
                                                            .toList(),
                                                    m.copy() == null
                                                            ? ""
                                                            : m.copy().child(),
                                                    m.copy() == null
                                                            ? ""
                                                            : m.copy().parent()))
                                            .toList(),
                                    proposal.citations().stream()
                                            .map(CitationBody::toDomain)
                                            .toList()),
                    refused,
                    refusalReason);
        }
    }

    // ---- coach/messages ----

    record CoachMessageRequestBody(
            @JsonProperty("profile_ref") String profileRef,
            @JsonProperty("age_group") String ageGroup,
            String question) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CoachMessageBody(
            String answer,
            List<CitationBody> citations,
            boolean refused,
            @JsonProperty("refusal_reason") @Nullable String refusalReason) {
        CoachMessageBody {
            answer = answer == null ? "" : answer;
            citations = citations == null ? List.of() : citations;
        }

        CoachMessageResponse toDomain() {
            return new CoachMessageResponse(
                    answer, citations.stream().map(CitationBody::toDomain).toList(), refused, refusalReason);
        }
    }
}
