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

        /**
         * focus_factor(보호자가 키워 주고 싶은 역량)와 with_companion 은 AI develop 의 ConstraintsIn 에 아직 없어 AI 가 받아서 버린다.
         * focus_factor 는 AI 에서 이 칸을 받는 변경이 develop 에 들어간 뒤부터 AI 편성에 반영되고, 그 전에 배포한 AI 는 버린다.
         * 버려져도 요청이 깨지지 않으므로 먼저 보낸다(CO-07).
         */
        record Constraints(
                @JsonProperty("days_per_week") int daysPerWeek,
                @JsonProperty("minutes_per_session") int minutesPerSession,
                @JsonProperty("weekly_minutes") @Nullable Integer weeklyMinutes,
                boolean quiet,
                @JsonProperty("small_space") boolean smallSpace,
                @JsonProperty("no_props") boolean noProps,
                @JsonProperty("focus_factor") @Nullable String focusFactor,
                @JsonProperty("with_companion") boolean withCompanion) {
            static Constraints of(CoachRunRequest.Constraints c) {
                return new Constraints(
                        c.daysPerWeek(),
                        c.minutesPerSession(),
                        c.weeklyMinutes(),
                        c.quiet(),
                        c.smallSpace(),
                        c.noProps(),
                        c.focusFactor(),
                        c.withCompanion());
            }
        }

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
                    Constraints.of(r.constraints()));
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

        /** notices 는 비었으면 AI 가 아예 싣지 않는다(compose.py 「if notices」) — 빈 목록으로 읽는다. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record ProposalBody(List<MissionBody> missions, List<CitationBody> citations, List<String> notices) {
            ProposalBody {
                missions = missions == null ? List.of() : missions;
                citations = citations == null ? List.of() : citations;
                notices = notices == null ? List.of() : notices;
            }

            CoachRunResult.Proposal toDomain() {
                return new CoachRunResult.Proposal(
                        missions.stream().map(MissionBody::toDomain).toList(),
                        citations.stream().map(CitationBody::toDomain).toList(),
                        List.copyOf(notices));
            }
        }

        /**
         * 숫자 칸(duration_min · video_sec)은 Integer 다. Jackson 3 는 FAIL_ON_NULL_FOR_PRIMITIVES 가 기본 true 라
         * int 로 두면 칸 하나가 빠질 때 응답 전체를 읽지 못하고 실행이 FAILED 가 된다.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record MissionBody(
                String kind,
                String title,
                PeriodBody period,
                List<ParticipantBody> participants,
                @JsonProperty("duration_min") @Nullable Integer durationMin,
                @JsonProperty("video_sec") @Nullable Integer videoSec,
                List<SessionBody> sessions,
                @Nullable CopyBody copy,
                String reason) {
            MissionBody {
                kind = kind == null ? "" : kind;
                participants = participants == null ? List.of() : participants;
                sessions = sessions == null ? List.of() : sessions;
                reason = reason == null ? "" : reason;
            }

            CoachRunResult.Mission toDomain() {
                return new CoachRunResult.Mission(
                        kind,
                        title,
                        period.startDate(),
                        period.endDate(),
                        participants.stream()
                                .map(it -> new CoachRunResult.ParticipantRef(it.ref(), it.role()))
                                .toList(),
                        durationMin != null ? durationMin : legacySessionMinutes(),
                        videoSec,
                        sessions.stream().map(SessionBody::toDomain).toList(),
                        copy == null ? "" : copy.child(),
                        copy == null ? "" : copy.parent(),
                        reason);
            }

            /** 9/17 앞의 옛 모양은 미션에 duration_min 이 없고 세션마다 분이 있었다. 그때만 세션 분의 합을 쓴다. */
            private @Nullable Integer legacySessionMinutes() {
                if (sessions.stream().allMatch(it -> it.durationMin() == null)) return null;
                return sessions.stream()
                        .map(SessionBody::durationMin)
                        .mapToInt(it -> it == null ? 0 : it)
                        .sum();
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

        /** duration_min 은 옛 모양 호환용이다. 지금 AI 는 세션에 duration_sec(초)만 싣는다. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record SessionBody(
                @JsonProperty("day_offset") @Nullable Integer dayOffset,
                String phase,
                @Nullable Integer order,
                @JsonProperty("exercise_name") String exerciseName,
                @JsonProperty("fitness_factor") String fitnessFactor,
                @JsonProperty("duration_sec") @Nullable Integer durationSec,
                @JsonProperty("duration_min") @Nullable Integer durationMin,
                @Nullable VideoBody video,
                List<Integer> evidence) {
            SessionBody {
                phase = phase == null ? "" : phase;
                exerciseName = exerciseName == null ? "" : exerciseName;
                fitnessFactor = fitnessFactor == null ? "" : fitnessFactor;
                evidence = evidence == null ? List.of() : evidence;
            }

            CoachRunResult.Session toDomain() {
                return new CoachRunResult.Session(
                        dayOffset,
                        phase,
                        order,
                        exerciseName,
                        fitnessFactor,
                        durationSec,
                        video == null ? null : video.toDomain(),
                        evidence);
            }
        }

        /**
         * video_id 가 빠졌거나 null · 빈칸이면 영상이 없는 세션으로 읽는다. 도메인 {@link CoachRunResult.Video#videoId} 는
         * null 이 아니어야 한다 — 칸 변환과 구간 제목 조회가 그 값을 바로 쓴다. 영상 하나 때문에 제안 전체를 버리지 않는다.
         * source(youtube · kspo) · media_url(공단 영상의 mp4 주소)은 없어도 읽힌다. 빈칸은 null 로 읽는다.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record VideoBody(
                @JsonProperty("video_id") @Nullable String videoId,
                @JsonProperty("start_sec") @Nullable Integer startSec,
                @JsonProperty("end_sec") @Nullable Integer endSec,
                @JsonProperty("source") @Nullable String source,
                @JsonProperty("media_url") @Nullable String mediaUrl) {
            CoachRunResult.@Nullable Video toDomain() {
                if (videoId == null || videoId.isBlank()) return null;
                return new CoachRunResult.Video(videoId, startSec, endSec, blankToNull(source), blankToNull(mediaUrl));
            }

            private static @Nullable String blankToNull(@Nullable String value) {
                return value == null || value.isBlank() ? null : value.strip();
            }
        }

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
                    proposal == null ? null : proposal.toDomain(),
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
