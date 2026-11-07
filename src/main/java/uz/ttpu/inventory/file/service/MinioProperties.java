package uz.ttpu.inventory.file.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "file.minio")
public class MinioProperties {

    /**
     * MinIO / S3 API endpoint URL (e.g. http://localhost:9000 or http://minio:9000).
     */
    private String endpoint = "http://localhost:9000";

    /**
     * MinIO Access Key / Root User.
     */
    private String accessKey = "minioadmin";

    /**
     * MinIO Secret Key / Root Password.
     */
    private String secretKey = "minioadmin_secret";

    /**
     * Primary S3 bucket name for university inventory attachments.
     */
    private String bucketName = "ttpu-inventory";

    /**
     * AWS Region (default: us-east-1).
     */
    private String region = "us-east-1";

    /**
     * Maximum allowed size for photo uploads (default: 10 MB).
     */
    private long maxPhotoSizeBytes = 10 * 1024 * 1024L;

    /**
     * Maximum allowed size for documents / signed PDFs (default: 25 MB).
     */
    private long maxDocumentSizeBytes = 25 * 1024 * 1024L;

    /**
     * Whether to automatically check and create the bucket upon startup.
     */
    private boolean autoCreateBucket = true;
}
