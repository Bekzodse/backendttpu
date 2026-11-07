package uz.ttpu.inventory.notification.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.OverdueReturnAlertEvent;
import uz.ttpu.inventory.asset.api.WarrantyExpiringAlertEvent;
import uz.ttpu.inventory.notification.api.NotificationLogDto;
import uz.ttpu.inventory.notification.api.SendNotificationRequest;
import uz.ttpu.inventory.notification.domain.NotificationChannel;
import uz.ttpu.inventory.notification.domain.NotificationLog;
import uz.ttpu.inventory.notification.domain.NotificationStatus;
import uz.ttpu.inventory.notification.repository.NotificationLogRepository;
import uz.ttpu.inventory.repair.api.ItemMovedToVendorRepairEvent;
import uz.ttpu.inventory.repair.api.ItemRepairCompletedEvent;
import uz.ttpu.inventory.repair.api.OverdueRepairTicketAlertEvent;
import uz.ttpu.inventory.repair.api.RepairReplacementRecommendedEvent;
import uz.ttpu.inventory.workflow.api.WorkflowCompletedEvent;
import uz.ttpu.inventory.workflow.api.WorkflowReturnedForReworkEvent;

@Service
public class NotificationService {

  private final NotificationLogRepository notificationLogRepository;
  private final NotificationMapper notificationMapper;
  private final Map<NotificationChannel, NotificationChannelSender> channelSenders;
  private final String defaultEmailRecipient;
  private final String defaultTelegramChatId;
  private final String defaultPushToken;

  @Autowired
  public NotificationService(
      NotificationLogRepository notificationLogRepository,
      NotificationMapper notificationMapper,
      List<NotificationChannelSender> senders,
      @Value("${app.notifications.default-email:inventory-alerts@ttpu.uz}")
          String defaultEmailRecipient,
      @Value("${app.notifications.default-telegram-chat-id:ttpu_inventory_ops}")
          String defaultTelegramChatId,
      @Value("${app.notifications.default-push-token:fcm_topic_inventory_alerts}")
          String defaultPushToken) {
    this.notificationLogRepository = notificationLogRepository;
    this.notificationMapper = notificationMapper;
    this.channelSenders = new EnumMap<>(NotificationChannel.class);
    for (NotificationChannelSender sender : senders) {
      this.channelSenders.put(sender.getChannel(), sender);
    }
    this.defaultEmailRecipient = defaultEmailRecipient;
    this.defaultTelegramChatId = defaultTelegramChatId;
    this.defaultPushToken = defaultPushToken;
  }

  @Transactional(readOnly = true)
  public List<NotificationLogDto> listNotifications() {
    return notificationLogRepository.findAll().stream().map(notificationMapper::toDto).toList();
  }

  @Transactional(readOnly = true)
  public List<NotificationLogDto> listNotificationsByReference(UUID referenceId) {
    return notificationLogRepository.findAllByReferenceIdOrderByCreatedAtDesc(referenceId).stream()
        .map(notificationMapper::toDto)
        .toList();
  }

  @Transactional
  public NotificationLogDto sendNotification(SendNotificationRequest request) {
    return dispatchSingleChannel(
        request.eventType() != null && !request.eventType().isBlank()
            ? request.eventType()
            : "MANUAL_NOTIFICATION",
        request.referenceId(),
        request.channel(),
        request.recipient(),
        request.subject(),
        request.messageBody());
  }

  @EventListener
  @Transactional
  public void onItemMovedToVendorRepair(ItemMovedToVendorRepairEvent event) {
    String subject = "Item Sent to Vendor Repair: " + event.ticketNumber();
    String body =
        "Item "
            + event.itemId()
            + " moved to vendor repair ("
            + event.vendorName()
            + ", contract: "
            + event.vendorContractNumber()
            + "). State updated to TAMIRDA.";
    dispatchAcrossAllChannels("ITEM_MOVED_TO_VENDOR_REPAIR", event.ticketId(), subject, body);
  }

  @EventListener
  @Transactional
  public void onItemRepairCompleted(ItemRepairCompletedEvent event) {
    String subject = "Vendor Repair Completed: " + event.ticketNumber();
    String body =
        "Repair ticket "
            + event.ticketNumber()
            + " for item "
            + event.itemId()
            + " is DONE. Actual cost: "
            + event.actualCost();
    dispatchAcrossAllChannels("ITEM_REPAIR_COMPLETED", event.ticketId(), subject, body);
  }

  @EventListener
  @Transactional
  public void onRepairReplacementRecommended(RepairReplacementRecommendedEvent event) {
    String subject = "Replacement Recommended: Ticket " + event.ticketNumber();
    String body =
        "Repair ticket "
            + event.ticketNumber()
            + " for item "
            + event.itemId()
            + " has repair cost "
            + event.repairCost()
            + " reaching/exceeding 50% of residual value ("
            + event.residualValue()
            + "). Reason: "
            + event.reason();
    dispatchAcrossAllChannels("REPLACEMENT_RECOMMENDED_ALERT", event.ticketId(), subject, body);
  }

  @EventListener
  @Transactional
  public void onOverdueRepairTicketAlert(OverdueRepairTicketAlertEvent event) {
    String subject = "Overdue Repair Ticket Alert: " + event.ticketNumber();
    String body =
        "Repair ticket "
            + event.ticketNumber()
            + " for item "
            + event.itemId()
            + " is overdue since "
            + event.expectedReturnAt();
    dispatchAcrossAllChannels("OVERDUE_REPAIR_TICKET_ALERT", event.ticketId(), subject, body);
  }

  @EventListener
  @Transactional
  public void onOverdueReturnAlert(OverdueReturnAlertEvent event) {
    String subject = "Overdue Temporary Export Return: " + event.inventoryNumber();
    String body =
        "Item "
            + event.itemName()
            + " ("
            + event.inventoryNumber()
            + ") was expected back at "
            + event.expectedReturnAt()
            + " and is now overdue.";
    dispatchAcrossAllChannels("OVERDUE_RETURN_ALERT", event.itemId(), subject, body);
  }

  @EventListener
  @Transactional
  public void onWarrantyExpiringAlert(WarrantyExpiringAlertEvent event) {
    String subject = "Warranty Expiring within 30 Days: " + event.inventoryNumber();
    String body =
        "Warranty for item "
            + event.itemName()
            + " ("
            + event.inventoryNumber()
            + ") expires on "
            + event.warrantyExpiresAt()
            + ".";
    dispatchAcrossAllChannels("WARRANTY_EXPIRING_ALERT", event.itemId(), subject, body);
  }

  @EventListener
  @Transactional
  public void onWorkflowCompleted(WorkflowCompletedEvent event) {
    String subject =
        "Workflow Completed [" + event.finalStateName() + "]: " + event.documentTypeName();
    String body =
        "Workflow instance "
            + event.instanceId()
            + " for document "
            + event.documentId()
            + " finished with state "
            + event.finalStateName()
            + ".";
    dispatchAcrossAllChannels("WORKFLOW_COMPLETED", event.instanceId(), subject, body);
  }

  @EventListener
  @Transactional
  public void onWorkflowReturnedForRework(WorkflowReturnedForReworkEvent event) {
    String subject = "Workflow Returned for Rework: " + event.documentTypeName();
    String body =
        "Workflow instance "
            + event.instanceId()
            + " for document "
            + event.documentId()
            + " was returned at step "
            + event.returnedFromStepOrder()
            + ". Reason: "
            + event.reworkReason();
    dispatchAcrossAllChannels("WORKFLOW_RETURNED_FOR_REWORK", event.instanceId(), subject, body);
  }

  @Transactional
  public List<NotificationLogDto> dispatchAcrossAllChannels(
      String eventType, UUID referenceId, String subject, String body) {
    List<NotificationLogDto> results = new ArrayList<>();
    results.add(
        dispatchSingleChannel(
            eventType,
            referenceId,
            NotificationChannel.EMAIL,
            defaultEmailRecipient,
            subject,
            body));
    results.add(
        dispatchSingleChannel(
            eventType,
            referenceId,
            NotificationChannel.TELEGRAM,
            defaultTelegramChatId,
            subject,
            body));
    results.add(
        dispatchSingleChannel(
            eventType, referenceId, NotificationChannel.PUSH, defaultPushToken, subject, body));
    return results;
  }

  private NotificationLogDto dispatchSingleChannel(
      String eventType,
      UUID referenceId,
      NotificationChannel channel,
      String recipient,
      String subject,
      String body) {
    NotificationLog log =
        NotificationLog.builder()
            .eventType(eventType)
            .referenceId(referenceId)
            .channel(channel)
            .recipient(recipient)
            .subject(subject)
            .messageBody(body)
            .status(NotificationStatus.PENDING)
            .build();

    NotificationChannelSender sender = channelSenders.get(channel);
    if (sender == null) {
      log.setStatus(NotificationStatus.FAILED);
      log.setErrorMessage("No sender registered for channel " + channel);
    } else {
      try {
        sender.send(recipient, subject, body);
        log.setStatus(NotificationStatus.SENT);
        log.setSentAt(Instant.now());
      } catch (Exception ex) {
        log.setStatus(NotificationStatus.FAILED);
        log.setErrorMessage(ex.getMessage());
      }
    }
    return notificationMapper.toDto(notificationLogRepository.save(log));
  }
}
