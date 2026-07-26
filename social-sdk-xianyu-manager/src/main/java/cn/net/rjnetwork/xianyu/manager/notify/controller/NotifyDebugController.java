package cn.net.rjnetwork.xianyu.manager.notify.controller;

import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@ConditionalOnProperty(prefix = "xianyu.notify.debug", name = "enabled", havingValue = "true")
@RequestMapping("/api/notify/debug")
public class NotifyDebugController {

    private final ApplicationEventPublisher publisher;

    public NotifyDebugController(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/emit")
    public Map<String, Object> emit(@RequestBody DebugNotifyRequest request) {
        Map<String, Object> vars = request.vars() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.vars());
        if (request.accountName() != null && !request.accountName().isBlank()) {
            vars.putIfAbsent("accountName", request.accountName());
        }
        publisher.publishEvent(new NotifyEvent(request.scenario(), request.accountId(), request.accountName(), vars));
        return Map.of("success", true, "scenario", request.scenario());
    }

    public record DebugNotifyRequest(
            String scenario,
            Long accountId,
            String accountName,
            String dedupKey,
            Map<String, Object> vars
    ) {}
}
