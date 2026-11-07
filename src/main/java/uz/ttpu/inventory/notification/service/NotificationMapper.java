package uz.ttpu.inventory.notification.service;

import org.mapstruct.Mapper;
import uz.ttpu.inventory.notification.api.NotificationLogDto;
import uz.ttpu.inventory.notification.domain.NotificationLog;

@Mapper
public interface NotificationMapper {

  NotificationLogDto toDto(NotificationLog entity);
}
