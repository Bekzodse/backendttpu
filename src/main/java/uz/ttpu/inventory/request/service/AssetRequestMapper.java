package uz.ttpu.inventory.request.service;

import org.mapstruct.Mapper;
import uz.ttpu.inventory.request.api.AssetRequestDto;
import uz.ttpu.inventory.request.domain.AssetRequest;

@Mapper
public interface AssetRequestMapper {

  AssetRequestDto toDto(AssetRequest entity);
}
