package kr.ac.kookmin.familyfitness.coaching.support;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway;
import kr.ac.kookmin.familyfitness.shared.ai.AssessmentRequest;
import kr.ac.kookmin.familyfitness.shared.ai.AssessmentResponse;
import kr.ac.kookmin.familyfitness.shared.ai.CoachMessageRequest;
import kr.ac.kookmin.familyfitness.shared.ai.CoachMessageResponse;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunAccepted;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunRequest;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.ai.StubAiGateway;
import kr.ac.kookmin.familyfitness.shared.ai.VideoSearchRequest;
import kr.ac.kookmin.familyfitness.shared.ai.VideoSearchResponse;
import org.jspecify.annotations.Nullable;

/** 스크립트 가능한 게이트웨이. 기본은 {@link StubAiGateway} 에 위임하고, 필요한 응답만 바꿔 끼운다. */
public class FakeAiGateway implements AiGateway {
    private final AiGateway delegate;

    public @Nullable Function<CoachRunRequest, @Nullable CoachRunAccepted> onStart;
    public @Nullable Function<String, CoachRunResult> onPoll;
    public @Nullable Function<CoachMessageRequest, CoachMessageResponse> onAsk;
    public final List<CoachRunRequest> startRequests = new ArrayList<>();
    public int pollCount = 0;

    public FakeAiGateway() {
        this(new StubAiGateway());
    }

    public FakeAiGateway(AiGateway delegate) {
        this.delegate = delegate;
    }

    @Override
    public AssessmentResponse assess(AssessmentRequest request) {
        return delegate.assess(request);
    }

    @Override
    public VideoSearchResponse searchVideos(VideoSearchRequest request) {
        return delegate.searchVideos(request);
    }

    @Override
    public CoachRunAccepted startCoachRun(CoachRunRequest request) {
        startRequests.add(request);
        // onStart 가 null 을 돌려주면 스텁이 받는다(처음 몇 번만 거절하는 시험)
        CoachRunAccepted scripted = onStart == null ? null : onStart.apply(request);
        return scripted == null ? delegate.startCoachRun(request) : scripted;
    }

    @Override
    public CoachRunResult getCoachRun(String runId) {
        pollCount++;
        return onPoll == null ? delegate.getCoachRun(runId) : onPoll.apply(runId);
    }

    @Override
    public CoachMessageResponse ask(CoachMessageRequest request) {
        return onAsk == null ? delegate.ask(request) : onAsk.apply(request);
    }
}
