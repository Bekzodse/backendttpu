package uz.ttpu.inventory.audit.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import uz.ttpu.inventory.common.domain.AuditableItemAction;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Component
@RequiredArgsConstructor
public class ItemCardAuditAspect {

  private final AuditService auditService;

  @AfterReturning(pointcut = "@annotation(auditableItemAction)", returning = "result")
  public void logItemCardAction(
      JoinPoint joinPoint, AuditableItemAction auditableItemAction, Object result) {
    String methodName = joinPoint.getSignature().toShortString();
    String targetId = resolveTargetId(joinPoint.getArgs(), result);
    String actor = resolveActorUsername();
    String details = buildDetails(methodName, joinPoint.getArgs());

    auditService.recordItemAction(
        auditableItemAction.value(), targetId, methodName, actor, details);
  }

  private String buildDetails(String methodName, Object[] args) {
    if (args == null || args.length == 0) {
      return "Executed " + methodName;
    }
    String summarizedArgs =
        Arrays.stream(args)
            .map(
                arg -> {
                  if (arg == null) {
                    return "null";
                  }
                  if (arg instanceof Collection<?> col) {
                    return "Collection(size=" + col.size() + ")";
                  }
                  String str = arg.toString();
                  return str.length() > 200 ? str.substring(0, 200) + "..." : str;
                })
            .collect(Collectors.joining(", ", "[", "]"));
    return "Executed " + methodName + " with args=" + summarizedArgs;
  }

  private String resolveTargetId(Object[] args, Object result) {
    if (args != null && args.length > 0 && args[0] != null) {
      if (args[0] instanceof UUID || args[0] instanceof String) {
        return args[0].toString();
      }
    }
    if (result != null) {
      String extracted = extractIdFromObject(result);
      if (extracted != null) {
        return extracted;
      }
      if (result instanceof Collection<?> collection && !collection.isEmpty()) {
        String joinedIds =
            collection.stream()
                .map(this::extractIdFromObject)
                .filter(Objects::nonNull)
                .limit(5)
                .collect(Collectors.joining(","));
        if (!joinedIds.isBlank()) {
          return joinedIds.length() > 255 ? joinedIds.substring(0, 255) : joinedIds;
        }
      }
    }
    return "COLLECTION";
  }

  private String extractIdFromObject(Object obj) {
    if (obj == null) {
      return null;
    }
    for (String accessor : new String[] {"id", "qrId", "itemId"}) {
      try {
        var method = obj.getClass().getMethod(accessor);
        Object val = method.invoke(obj);
        if (val != null) {
          return val.toString();
        }
      } catch (ReflectiveOperationException ignored) {
        // try next accessor
      }
    }
    return null;
  }

  private String resolveActorUsername() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getName() != null) {
      return authentication.getName();
    }
    return "ANONYMOUS";
  }
}
