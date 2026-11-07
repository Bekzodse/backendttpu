package uz.ttpu.inventory.notification.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.ttpu.inventory.notification.domain.NotificationChannel;

@Component
public class TelegramNotificationSender implements NotificationChannelSender {

  private final AbsSender absSender;
  private final boolean liveSend;

  @Autowired
  public TelegramNotificationSender(
      @Value("${app.notifications.telegram.bot-token:}") String botToken,
      @Value("${app.notifications.telegram.live-send:false}") boolean liveSend) {
    this.absSender =
        new DefaultAbsSender(new DefaultBotOptions(), botToken != null ? botToken : "") {};
    this.liveSend = liveSend && botToken != null && !botToken.isBlank();
  }

  public TelegramNotificationSender(AbsSender absSender, boolean liveSend) {
    this.absSender = absSender;
    this.liveSend = liveSend;
  }

  @Override
  public NotificationChannel getChannel() {
    return NotificationChannel.TELEGRAM;
  }

  @Override
  public void send(String recipient, String subject, String body) {
    SendMessage sendMessage =
        SendMessage.builder().chatId(recipient).text("*" + subject + "*\n" + body).build();
    if (liveSend) {
      try {
        absSender.execute(sendMessage);
      } catch (TelegramApiException ex) {
        throw new IllegalStateException("Failed to dispatch Telegram notification", ex);
      }
    }
  }
}
