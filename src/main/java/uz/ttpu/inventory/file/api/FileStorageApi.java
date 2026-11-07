package uz.ttpu.inventory.file.api;

import java.util.List;
import java.util.UUID;
import uz.ttpu.inventory.file.domain.FileCategory;

public interface FileStorageApi {

  StoredFileDto storeFile(UploadFileRequest request, String uploadedByUsername);

  StoredFileDto storeRawBytes(
      String filename,
      String contentType,
      FileCategory category,
      byte[] bytes,
      UUID referenceEntityId,
      String uploadedByUsername);

  FileDownloadDto downloadFile(UUID id);

  StoredFileDto getMetadata(UUID id);

  List<StoredFileDto> listByReferenceEntity(UUID referenceEntityId);

  List<StoredFileDto> listAll();

  void deleteFile(UUID id, String actorUsername);
}
