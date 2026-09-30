package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.Objects;
import kr.ac.kookmin.familyfitness.coaching.application.ChatCommand;
import kr.ac.kookmin.familyfitness.coaching.application.ChatView;
import kr.ac.kookmin.familyfitness.coaching.application.CoachChatService;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 코치 대화(근거 있는 답만; 거부는 200). */
@RestController
@RequestMapping("/api/v1/coach/chat")
public class CoachChatController {
    private final CoachChatService service;

    public CoachChatController(CoachChatService service) {
        this.service = service;
    }

    @PostMapping
    public ChatView chat(CurrentUser user, @Valid @RequestBody ChatRequest body) {
        return service.chat(
                user.userId(),
                new ChatCommand(Objects.requireNonNull(body.profileId()), body.conversationId(), body.question()));
    }
}
