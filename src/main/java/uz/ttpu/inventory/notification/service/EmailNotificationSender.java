package uz.ttpu.inventory.notification.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import uz.ttpu.inventory.notification.domain.NotificationChannel;

@Component
public class EmailNotificationSender implements NotificationChannelSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean liveSend;

    @Autowired
    public EmailNotificationSender(
            JavaMailSender mailSender,
            @Value("${app.notifications.email.from:noreply@ttpu.uz}") String fromAddress,
            @Value("${app.notifications.email.live-send:false}") boolean liveSend
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.liveSend = liveSend;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        if (liveSend) {
            mailSender.send(message);
        }
    }
}
