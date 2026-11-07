package uz.ttpu.inventory.common.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;

public enum ErrorCode {
  ERR_RESOURCE_NOT_FOUND(
      HttpStatus.NOT_FOUND,
      "Resource Not Found",
      "So'ralgan ma'lumot topilmadi.",
      "Запрашиваемый ресурс не найден.",
      "The requested resource was not found."),
  ERR_VALIDATION_FAILED(
      HttpStatus.BAD_REQUEST,
      "Validation Error",
      "So'rov ma'lumotlari tekshiruvdan o'tmadi.",
      "Ошибка валидации входных данных запроса.",
      "Request validation failed."),
  ERR_BAD_REQUEST(
      HttpStatus.BAD_REQUEST,
      "Bad Request",
      "So'rov noto'g'ri shakllantirilgan.",
      "Некорректный запрос.",
      "The request is invalid or malformed."),
  ERR_STATE_CONFLICT(
      HttpStatus.CONFLICT,
      "Conflict",
      "Amalni joriy holatda bajarib bo'lmaydi (ziddiyat).",
      "Конфликт состояния ресурса.",
      "The operation conflicts with the current state of the resource."),
  ERR_ILLEGAL_ITEM_STATE_TRANSITION(
      HttpStatus.CONFLICT,
      "Illegal Item State Transition",
      "Inventar obyekti holatini bunday o'zgartirish taqiqlangan.",
      "Недопустимый переход состояния инвентарного объекта.",
      "The requested item lifecycle state transition is not permitted."),
  ERR_DUPLICATE_SERIAL_NUMBER(
      HttpStatus.CONFLICT,
      "Duplicate Serial Number in Category",
      "Ushbu kategoriyada bunday seriya raqamli jihoz allaqachon mavjud.",
      "Оборудование с таким серийным номером уже существует в данной категории.",
      "An item with this serial number already exists in the specified category."),
  ERR_AUTH_FAILED(
      HttpStatus.UNAUTHORIZED,
      "Authentication Failed",
      "Autentifikatsiya xatosi: login, parol yoki 2FA kodi noto'g'ri.",
      "Ошибка аутентификации: неверный логин, пароль или код 2FA.",
      "Authentication failed: invalid credentials or 2FA verification code."),
  ERR_ACCOUNT_LOCKED(
      HttpStatus.LOCKED,
      "Account Locked or Disabled",
      "Foydalanuvchi hisobi vaqtincha bloklangan yoki faolsizlantirilgan.",
      "Учетная запись пользователя временно заблокирована или отключена.",
      "The user account is locked or disabled."),
  ERR_ACCESS_DENIED(
      HttpStatus.FORBIDDEN,
      "Access Denied",
      "Ushbu amalni bajarish uchun sizda yetarli ruxsat yo'q.",
      "Доступ запрещен: недостаточно прав для выполнения операции.",
      "Access denied: insufficient permissions for this operation."),
  ERR_RATE_LIMIT_EXCEEDED(
      HttpStatus.TOO_MANY_REQUESTS,
      "Too Many Requests",
      "So'rovlar soni belgilangan me'yordan oshib ketdi. Birozdan so'ng qayta urinib ko'ring.",
      "Превышен лимит запросов. Пожалуйста, повторите попытку позже.",
      "Too many requests. Please wait before retrying."),
  ERR_CLEARANCE_CHECKLIST_BLOCKED(
      HttpStatus.UNPROCESSABLE_ENTITY,
      "Clearance Checklist Blocked",
      "Aylanma varaqa yopilmadi: xodimga biriktirilgan inventar to'liq topshirilmagan.",
      "Обходной лист не может быть закрыт: не сдано закрепленное имущество.",
      "Clearance checklist cannot be closed until all assigned inventory is handed over."),
  ERR_DATA_WIPE_REQUIRED(
      HttpStatus.UNPROCESSABLE_ENTITY,
      "IT Data Wipe Required",
      "Hisobdan chiqarish rad etildi: IT uskuna xotirasini tozalash (data-wipe) bajarilmagan.",
      "Списание заблокировано: обязательная очистка данных (data-wipe) не выполнена.",
      "Mandatory data-wipe record is required before completing write-off."),
  ERR_DEVICE_REVOKED(
      HttpStatus.UNAUTHORIZED,
      "Device Revoked",
      "Ushbu mobil qurilma administrator tomonidan bekor qilingan.",
      "Устройство было отозвано администратором. Доступ заблокирован.",
      "This mobile device has been revoked by an administrator."),
  ERR_PERMIT_INVALID(
      HttpStatus.UNPROCESSABLE_ENTITY,
      "Off-Campus Permit Invalid",
      "Universitet hududidan olib chiqish ruxsatnomasi mavjud emas yoki muddati tugagan.",
      "Пропуск на вынос имущества отсутствует или просрочен.",
      "Off-campus permit is missing, invalid, or expired."),
  ERR_INTERNAL_SERVER_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "Internal Server Error",
      "Kutilmagan tizim xatoligi yuz berdi. Iltimos, administratorga murojaat qiling.",
      "Произошла непредвиденная системная ошибка. Пожалуйста, обратитесь к администратору.",
      "An unexpected internal error occurred. Please contact the administrator.");

  private final HttpStatus status;
  private final String title;
  private final String messageUz;
  private final String messageRu;
  private final String messageEn;

  ErrorCode(HttpStatus status, String title, String messageUz, String messageRu, String messageEn) {
    this.status = status;
    this.title = title;
    this.messageUz = messageUz;
    this.messageRu = messageRu;
    this.messageEn = messageEn;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getTitle() {
    return title;
  }

  public String getMessageUz() {
    return messageUz;
  }

  public String getMessageRu() {
    return messageRu;
  }

  public String getMessageEn() {
    return messageEn;
  }

  public Map<String, String> toLocalizedMessagesMap() {
    Map<String, String> messages = new LinkedHashMap<>();
    messages.put("uz", messageUz);
    messages.put("ru", messageRu);
    messages.put("en", messageEn);
    return messages;
  }

  public String resolveMessage(Locale locale) {
    if (locale == null) {
      return messageUz;
    }
    String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
    return switch (lang) {
      case "ru" -> messageRu;
      case "en" -> messageEn;
      default -> messageUz;
    };
  }
}
