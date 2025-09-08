package space.devincoopers.bingo_ws.ws;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Controller
public class BingoController {

    public record BingoCall(String gameId, String code, String createdAt) {}

    private final SimpMessagingTemplate template;

    // In-memory store so late joiners can fetch history (swap to DB later)
    private final Map<String, Set<String>> calledByGame = new ConcurrentHashMap<>();

    public BingoController(SimpMessagingTemplate template) {
        this.template = template;
    }

    @MessageMapping("/call") // client publishes to /app/call
    public void handleCall(BingoCall call) {
        if (call == null || !isValidCode(call.code())) return;

        // idempotent add per game
        calledByGame.computeIfAbsent(call.gameId(), k -> new CopyOnWriteArraySet<>()).add(call.code());

        // normalize timestamp if missing
        String timestamp = (call.createdAt() == null || call.createdAt().isBlank())
                ? Instant.now().toString()
                : call.createdAt();

        // broadcast to all subscribers of this game
        template.convertAndSend("/topic/bingo/" + call.gameId(), new BingoCall(call.gameId(), call.code(), timestamp));
    }

    private boolean isValidCode(String code) {
        if (code == null || code.length() < 2) return false;
        char col = Character.toUpperCase(code.charAt(0));
        int num;
        try {
            num = Integer.parseInt(code.substring(1));
        } catch (Exception e) {
            return false;
        }
        return switch (col) {
            case 'B' -> num >= 1 && num <= 15;
            case 'I' -> num >= 16 && num <= 30;
            case 'N' -> num >= 31 && num <= 45;
            case 'G' -> num >= 46 && num <= 60;
            case 'O' -> num >= 61 && num <= 75;
            default -> false;
        };
    }

    // --- simple REST helpers for joiners & admin reset ---

    @org.springframework.web.bind.annotation.GetMapping("/api/games/{gameId}/calls")
    public List<String> getCalls(@org.springframework.web.bind.annotation.PathVariable String gameId) {
        return new ArrayList<>(calledByGame.getOrDefault(gameId, Set.of()));
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/games/{gameId}/reset")
    public void reset(@org.springframework.web.bind.annotation.PathVariable String gameId) {
        calledByGame.remove(gameId);
        template.convertAndSend("/topic/bingo/" + gameId,
                new BingoCall(gameId, "RESET", Instant.now().toString()));
    }
}
