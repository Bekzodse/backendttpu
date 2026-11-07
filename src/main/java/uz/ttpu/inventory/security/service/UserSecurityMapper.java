package uz.ttpu.inventory.security.service;

import org.mapstruct.Mapper;
import uz.ttpu.inventory.security.api.UserPrincipalDto;
import uz.ttpu.inventory.security.domain.UserAccount;

@Mapper(componentModel = "spring")
public interface UserSecurityMapper {

    UserPrincipalDto toPrincipalDto(UserAccount userAccount);
}
