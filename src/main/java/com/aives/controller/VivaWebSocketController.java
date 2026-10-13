package com.aives.controller;

import com.aives.dto.request.VivaTurnWsRequest;
import com.aives.dto.response.VivaTurnWsResponse;
import com.aives.service.StudentExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class VivaWebSocketController {

    private final StudentExamService studentExamService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/exam/{sessionId}/submit-turn")
    public void submitTurn(
            @DestinationVariable UUID sessionId,
            @Payload VivaTurnWsRequest request) {
        log.info("Received STOMP turn submission for session: {}, order: {}", sessionId, request.getTurnOrder());
        if (request.getSessionId() == null) {
            request.setSessionId(sessionId);
        }

        VivaTurnWsResponse response = studentExamService.processVivaTurn(sessionId, request);

        // Broadcast AI evaluation & adaptive follow-up to topic subscribers
        messagingTemplate.convertAndSend("/topic/exam/" + sessionId, response);
        log.info("Broadcasted AI viva evaluation response to /topic/exam/{}", sessionId);
    }
}
