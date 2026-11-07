package uz.ttpu.inventory.file.service;

import org.mapstruct.Mapper;
import uz.ttpu.inventory.file.api.StoredFileDto;
import uz.ttpu.inventory.file.domain.StoredFileRecord;

@Mapper
public interface StoredFileMapper {

  StoredFileDto toDto(StoredFileRecord entity);
}
