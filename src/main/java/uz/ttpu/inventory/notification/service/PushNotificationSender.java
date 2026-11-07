package uz.ttpu.inventory.notification.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.ttpu.inventory.notification.domain.NotificationChannel;

@Component
public class PushNotificationSender implements NotificationChannelSender {

  private final FirebaseMessaging firebaseMessaging;
  private final boolean liveSend;

  @Autowired
  public PushNotificationSender(
      @Value("${app.notifications.push.live-send:false}") boolean liveSend) {
    this.firebaseMessaging = null;
    this.liveSend = liveSend;
  }

  public PushNotificationSender(FirebaseMessaging firebaseMessaging, boolean liveSend) {
    this.firebaseMessaging = firebaseMessaging;
    this.liveSend = liveSend;
  }

  @Override
  public NotificationChannel getChannel() {
    return NotificationChannel.PUSH;
  }

  @Override
  public void send(String recipient, String subject, String body) {
    Message message =
        Message.builder()
            .setToken(recipient)
            .setNotification(Notification.builder().setTitle(subject).setBody(body).build())
            .build();
    if (liveSend) {
      try {
        FirebaseMessaging client =
            firebaseMessaging != null
                ? firebaseMessaging
                : (!FirebaseApp.getApps().isEmpty() ? FirebaseMessaging.getInstance() : null);
        if (client != null) {
          client.send(message);
        }
      } catch (FirebaseMessagingException ex) {
        throw new IllegalStateException("Failed to dispatch Firebase Push notification", ex);
      }
    }
  }
}
