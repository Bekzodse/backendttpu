package uz.ttpu.inventory.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.notification.service.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(
        name = "12. Multi-Channel Notifications (Email, Telegram, FCM Push)",
        description = "Event-driven and manual notifications dispatched across SMTP Email, Telegram Bot API, and Firebase Cloud Messaging (FCM) Push."
)
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "List all notification delivery logs",
            description = "Returns all dispatched notification logs across EMAIL, TELEGRAM, and PUSH channels."
    )
    public List<NotificationLogDto> listNotifications() {
        return notificationService.listNotifications();
    }

    @GetMapping("/reference/{referenceId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "List notifications for a specific reference entity",
            description = "Returns all notification logs associated with a specific domain entity UUID (e.g., repair ticket, workflow instance, or asset alert)."
    )
    public List<NotificationLogDto> listNotificationsByReference(@PathVariable UUID referenceId) {
        return notificationService.listNotificationsByReference(referenceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'TECHNICIAN', 'TECHNICAL_SPECIALIST')")
    @Operation(
            summary = "Send a manual notification via Email, Telegram, or Push",
            description = "Dispatches a notification over the selected channel (EMAIL, TELEGRAM, or PUSH) and records the delivery status in notification_logs."
    )
    public NotificationLogDto sendNotification(@Valid @RequestBody SendNotificationRequest request) {
        return notificationService.sendNotification(request);
    }
}

