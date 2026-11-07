package uz.ttpu.inventory.notification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import uz.ttpu.inventory.notification.domain.NotificationChannel;

public record SendNotificationRequest(
    @NotNull NotificationChannel channel,
    @NotBlank String recipient,
    @NotBlank String subject,
    @NotBlank String messageBody,
    String eventType,
    UUID referenceId) {}
