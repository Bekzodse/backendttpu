package uz.ttpu.inventory.notification.service;

import uz.ttpu.inventory.notification.domain.NotificationChannel;

public interface NotificationChannelSender {

  NotificationChannel getChannel();

  void send(String recipient, String subject, String body);
}
