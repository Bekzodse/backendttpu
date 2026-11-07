package uz.ttpu.inventory.notification.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.notification.domain.NotificationChannel;
import uz.ttpu.inventory.notification.domain.NotificationStatus;

public record NotificationLogDto(
    UUID id,
    String eventType,
    UUID referenceId,
    NotificationChannel channel,
    String recipient,
    String subject,
    String messageBody,
    NotificationStatus status,
    String errorMessage,
    Instant sentAt,
    Instant createdAt) {}
