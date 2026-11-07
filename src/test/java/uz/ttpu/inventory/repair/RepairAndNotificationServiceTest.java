package uz.ttpu.inventory.repair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import uz.ttpu.inventory.asset.api.OverdueReturnAlertEvent;
import uz.ttpu.inventory.asset.api.WarrantyExpiringAlertEvent;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.domain.ItemStateHistory;
import uz.ttpu.inventory.asset.repository.ItemRepository;
import uz.ttpu.inventory.asset.repository.ItemStateHistoryRepository;
import uz.ttpu.inventory.asset.service.AssetScheduledAlertJob;
import uz.ttpu.inventory.asset.service.AssetService;
import uz.ttpu.inventory.asset.service.ItemMapper;
import uz.ttpu.inventory.asset.service.ItemStateService;
import uz.ttpu.inventory.asset.service.QrIdService;
import uz.ttpu.inventory.notification.api.NotificationLogDto;
import uz.ttpu.inventory.notification.domain.NotificationChannel;
import uz.ttpu.inventory.notification.domain.NotificationLog;
import uz.ttpu.inventory.notification.domain.NotificationStatus;
import uz.ttpu.inventory.notification.repository.NotificationLogRepository;
import uz.ttpu.inventory.notification.service.EmailNotificationSender;
import uz.ttpu.inventory.notification.service.NotificationMapper;
import uz.ttpu.inventory.notification.service.NotificationService;
import uz.ttpu.inventory.notification.service.PushNotificationSender;
import uz.ttpu.inventory.notification.service.TelegramNotificationSender;
import uz.ttpu.inventory.repair.api.CompleteRepairRequest;
import uz.ttpu.inventory.repair.api.CreateRepairTicketRequest;
import uz.ttpu.inventory.repair.api.ItemMovedToVendorRepairEvent;
import uz.ttpu.inventory.repair.api.ItemRepairCompletedEvent;
import uz.ttpu.inventory.repair.api.OverdueRepairTicketAlertEvent;
import uz.ttpu.inventory.repair.api.RepairTicketDto;
import uz.ttpu.inventory.repair.api.StartInProgressRepairRequest;
import uz.ttpu.inventory.repair.api.VendorRepairRequest;
import uz.ttpu.inventory.repair.domain.RepairTicket;
import uz.ttpu.inventory.repair.domain.RepairTicketState;
import uz.ttpu.inventory.repair.repository.RepairTicketRepository;
import uz.ttpu.inventory.repair.service.RepairMapper;
import uz.ttpu.inventory.repair.service.RepairService;

class RepairAndNotificationServiceTest {

    private ItemRepository itemRepository;
    private ItemStateHistoryRepository historyRepository;
    private RepairTicketRepository repairTicketRepository;
    private NotificationLogRepository notificationLogRepository;

    private JavaMailSender mailSender;
    private AbsSender telegramAbsSender;
    private FirebaseMessaging firebaseMessaging;

    private AssetService assetService;
    private NotificationService notificationService;
    private RepairService repairService;
    private AssetScheduledAlertJob scheduledAlertJob;

    private final List<NotificationLog> savedLogs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        itemRepository = mock(ItemRepository.class);
        historyRepository = mock(ItemStateHistoryRepository.class);
        repairTicketRepository = mock(RepairTicketRepository.class);
        notificationLogRepository = mock(NotificationLogRepository.class);

        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(ItemStateHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repairTicketRepository.save(any(RepairTicket.class))).thenAnswer(inv -> {
            RepairTicket t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });

        savedLogs.clear();
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(inv -> {
            NotificationLog log = inv.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            savedLogs.add(log);
            return log;
        });

        ApplicationEventPublisher eventPublisher = event -> {
            if (event instanceof ItemMovedToVendorRepairEvent vendorEvent) {
                assetService.onItemMovedToVendorRepair(vendorEvent);
                notificationService.onItemMovedToVendorRepair(vendorEvent);
            } else if (event instanceof ItemRepairCompletedEvent completedEvent) {
                assetService.onItemRepairCompleted(completedEvent);
                notificationService.onItemRepairCompleted(completedEvent);
            } else if (event instanceof OverdueRepairTicketAlertEvent overdueRepairEvent) {
                notificationService.onOverdueRepairTicketAlert(overdueRepairEvent);
            } else if (event instanceof OverdueReturnAlertEvent overdueReturnEvent) {
                notificationService.onOverdueReturnAlert(overdueReturnEvent);
            } else if (event instanceof WarrantyExpiringAlertEvent warrantyEvent) {
                notificationService.onWarrantyExpiringAlert(warrantyEvent);
            }
        };

        ItemStateService itemStateService = new ItemStateService(itemRepository, historyRepository, eventPublisher);
        QrIdService qrIdService = new QrIdService(itemRepository);
        ItemMapper itemMapper = Mappers.getMapper(ItemMapper.class);
        assetService = new AssetService(itemRepository, historyRepository, itemStateService, qrIdService, itemMapper);

        mailSender = mock(JavaMailSender.class);
        telegramAbsSender = mock(AbsSender.class);
        firebaseMessaging = mock(FirebaseMessaging.class);

        EmailNotificationSender emailSender = new EmailNotificationSender(mailSender, "noreply@ttpu.uz", true);
        TelegramNotificationSender telegramSender = new TelegramNotificationSender(telegramAbsSender, true);
        PushNotificationSender pushSender = new PushNotificationSender(firebaseMessaging, true);

        NotificationMapper notificationMapper = Mappers.getMapper(NotificationMapper.class);
        notificationService = new NotificationService(
                notificationLogRepository,
                notificationMapper,
                List.of(emailSender, telegramSender, pushSender),
                "ops@ttpu.uz",
                "chat_12345",
                "fcm_token_xyz"
        );

        RepairMapper repairMapper = Mappers.getMapper(RepairMapper.class);
        repairService = new RepairService(repairTicketRepository, repairMapper, eventPublisher);
        scheduledAlertJob = new AssetScheduledAlertJob(itemRepository, eventPublisher);
    }

    @Test
    void repairTicketLifecycleMovesItemToTamirdaOnVendorRepairAndDispatchesAllNotificationChannels() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();

        Item item = Item.builder()
                .inventoryNumber("INV-2026-500")
                .qrCode("ABCDEFGHIJ")
                .name("3D Printer Ultimaker S7")
                .categoryId(UUID.randomUUID())
                .state(ItemState.FOYDALANISHDA)
                .purchasePrice(new BigDecimal("85000000.00"))
                .residualValue(new BigDecimal("80000000.00"))
                .build();
        item.setId(itemId);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        // 1. Create Repair Ticket -> State is NEW
        RepairTicketDto created = repairService.createTicket(new CreateRepairTicketRequest(
                "RPR-2026-001",
                itemId,
                reporterId,
                null,
                "Extruder heating element failure",
                Instant.now().plus(5, ChronoUnit.DAYS)
        ));
        assertThat(created.state()).isEqualTo(RepairTicketState.NEW);

        RepairTicket storedTicket = RepairTicket.builder()
                .ticketNumber(created.ticketNumber())
                .itemId(itemId)
                .reportedByEmployeeId(reporterId)
                .state(RepairTicketState.NEW)
                .issueDescription(created.issueDescription())
                .expectedReturnAt(created.expectedReturnAt())
                .build();
        storedTicket.setId(created.id());
        when(repairTicketRepository.findById(created.id())).thenReturn(Optional.of(storedTicket));

        // 2. Move to IN_PROGRESS
        RepairTicketDto inProgress = repairService.startInProgress(
                created.id(),
                new StartInProgressRepairRequest(technicianId, Instant.now().plus(7, ChronoUnit.DAYS))
        );
        assertThat(inProgress.state()).isEqualTo(RepairTicketState.IN_PROGRESS);
        assertThat(item.getState()).isEqualTo(ItemState.FOYDALANISHDA);

        // 3. Move to Vendor Repair (IN_SERVICE) -> Triggers ItemMovedToVendorRepairEvent -> Item state becomes TAMIRDA
        Instant expectedVendorReturn = Instant.now().plus(10, ChronoUnit.DAYS);
        RepairTicketDto inService = repairService.sendToVendorRepair(
                created.id(),
                new VendorRepairRequest(
                        "TechService Tashkent LLC",
                        "VND-CTR-88",
                        new BigDecimal("3500000.00"),
                        expectedVendorReturn
                )
        );
        assertThat(inService.state()).isEqualTo(RepairTicketState.IN_SERVICE);
        assertThat(item.getState()).isEqualTo(ItemState.TAMIRDA);
        assertThat(item.getExpectedReturnAt()).isEqualTo(expectedVendorReturn);

        // Verify Email, Telegram, and Push channels were all invoked and logged as SENT
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        verify(telegramAbsSender, times(1)).execute(any(SendMessage.class));
        verify(firebaseMessaging, times(1)).send(any(Message.class));
        assertThat(savedLogs).hasSize(3);
        assertThat(savedLogs).extracting(NotificationLog::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.TELEGRAM, NotificationChannel.PUSH);
        assertThat(savedLogs).extracting(NotificationLog::getStatus)
                .containsOnly(NotificationStatus.SENT);

        // 4. Complete Repair (IN_SERVICE -> DONE) -> Item returns from TAMIRDA to FOYDALANISHDA
        RepairTicketDto done = repairService.completeRepair(
                created.id(),
                new CompleteRepairRequest(new BigDecimal("3200000.00"), "Replaced print head core and calibrated")
        );
        assertThat(done.state()).isEqualTo(RepairTicketState.DONE);
        assertThat(item.getState()).isEqualTo(ItemState.FOYDALANISHDA);
        assertThat(item.getExpectedReturnAt()).isNull();
        assertThat(savedLogs).hasSize(6);

        // 5. Close Ticket (DONE -> CLOSED)
        RepairTicketDto closed = repairService.closeTicket(created.id());
        assertThat(closed.state()).isEqualTo(RepairTicketState.CLOSED);
        assertThat(closed.closedAt()).isNotNull();

        // Verify invalid state transition after CLOSED is rejected
        assertThatThrownBy(() -> repairService.closeTicket(created.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void scheduledDailyJobsCheckOverdueReturnsAndWarrantiesEndingIn30DaysAndDispatchNotifications() {
        UUID overdueItemId = UUID.randomUUID();
        Item overdueItem = Item.builder()
                .inventoryNumber("INV-OVERDUE-01")
                .qrCode("BCDEFGHIJK")
                .name("Portable Oscilloscope")
                .categoryId(UUID.randomUUID())
                .state(ItemState.VAQTINCHA_TASHQARIDA)
                .custodianEmployeeId(UUID.randomUUID())
                .expectedReturnAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .purchasePrice(new BigDecimal("12000000.00"))
                .residualValue(new BigDecimal("10000000.00"))
                .build();
        overdueItem.setId(overdueItemId);

        UUID expiringWarrantyItemId = UUID.randomUUID();
        Item warrantyItem = Item.builder()
                .inventoryNumber("INV-WARRANTY-01")
                .qrCode("CDEFGHIJKL")
                .name("Server Rack UPS")
                .categoryId(UUID.randomUUID())
                .state(ItemState.FOYDALANISHDA)
                .custodianEmployeeId(UUID.randomUUID())
                .warrantyExpiresAt(Instant.now().plus(15, ChronoUnit.DAYS))
                .purchasePrice(new BigDecimal("45000000.00"))
                .residualValue(new BigDecimal("40000000.00"))
                .build();
        warrantyItem.setId(expiringWarrantyItemId);

        when(itemRepository.findAllByStateInAndExpectedReturnAtBefore(any(), any()))
                .thenReturn(List.of(overdueItem));
        when(itemRepository.findAllByWarrantyExpiresAtBetween(any(), any()))
                .thenReturn(List.of(warrantyItem));

        RepairTicket overdueRepairTicket = RepairTicket.builder()
                .ticketNumber("RPR-OVERDUE-09")
                .itemId(overdueItemId)
                .reportedByEmployeeId(UUID.randomUUID())
                .state(RepairTicketState.IN_SERVICE)
                .issueDescription("Power supply repair")
                .vendorName("ElectroFix UZ")
                .expectedReturnAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        overdueRepairTicket.setId(UUID.randomUUID());
        when(repairTicketRepository.findAllByStateInAndExpectedReturnAtBefore(any(), any()))
                .thenReturn(List.of(overdueRepairTicket));

        int overdueCount = scheduledAlertJob.checkAndPublishOverdueReturns();
        int warrantyCount = scheduledAlertJob.checkAndPublishExpiringWarranties();
        int overdueRepairCount = repairService.checkAndPublishOverdueRepairTickets();

        assertThat(overdueCount).isEqualTo(1);
        assertThat(warrantyCount).isEqualTo(1);
        assertThat(overdueRepairCount).isEqualTo(1);

        // 3 events * 3 channels = 9 notification logs dispatched
        assertThat(savedLogs).hasSize(9);
        assertThat(savedLogs).extracting(NotificationLog::getEventType)
                .contains("OVERDUE_RETURN_ALERT", "WARRANTY_EXPIRING_ALERT", "OVERDUE_REPAIR_TICKET_ALERT");
    }
}
