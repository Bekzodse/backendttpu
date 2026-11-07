package uz.ttpu.inventory.file.service;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioClientConfig {

    private final MinioProperties properties;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
                ))
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucketExists() {
        if (!properties.isAutoCreateBucket()) {
            return;
        }
        try {
            S3Client client = s3Client();
            try {
                client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucketName()).build());
                log.info("MinIO S3 bucket '{}' is ready.", properties.getBucketName());
            } catch (NoSuchBucketException ex) {
                client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucketName()).build());
                log.info("Created MinIO S3 bucket '{}'.", properties.getBucketName());
            }
        } catch (Exception ex) {
            log.warn("MinIO bucket readiness check deferred or offline at {}: {}", properties.getEndpoint(), ex.getMessage());
        }
    }
}
