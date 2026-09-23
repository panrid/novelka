package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.chat.ChatMessageRequest;
import panrid.space.novelka.server.chat.ChatService;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public final class ChatController {
    private final AccessService access;
    private final ChatService chat;

    public ChatController(AccessService access, ChatService chat) {
        this.access = access;
        this.chat = chat;
    }

    @GetMapping
    public Map<String, Object> history(Principal principal, @RequestParam(defaultValue = "0") long before) throws Exception {
        return chat.history(access.require(principal, Role.READER), before);
    }

    @GetMapping("/updates")
    public Map<String, Object> updates(Principal principal, @RequestParam long after) throws Exception {
        return chat.updates(access.require(principal, Role.READER), after);
    }

    @PostMapping
    public Map<String, Long> send(Principal principal, @RequestBody ChatMessageRequest request) throws Exception {
        return Map.of("id", chat.send(access.require(principal, Role.READER), request));
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(Principal principal, @PathVariable long id) throws Exception {
        chat.delete(access.require(principal, Role.READER), id);
        return Map.of("message", "Повідомлення видалено.");
    }
}
