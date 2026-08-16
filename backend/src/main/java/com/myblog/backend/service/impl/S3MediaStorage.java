package com.myblog.backend.service.impl;
import com.myblog.backend.service.MediaStorage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URI;

/**
 * 生产媒体存储：私有 Cloudflare R2（S3 兼容，#14 实现决策）。
 *
 * <p>通过环境变量配置：{@code R2_ENDPOINT}、{@code R2_ACCESS_KEY_ID}、
 * {@code R2_SECRET_ACCESS_KEY}、{@code R2_BUCKET}、{@code R2_PUBLIC_BASE_URL}
 * （GET/HEAD-only Worker 的公开读取入口）。上传与删除只经认证的 Admin API；
 * 公开读取走不可变 Worker URL，不经后端。
 */
public class S3MediaStorage implements MediaStorage {

    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;

    public S3MediaStorage(S3Client s3, String bucket, String publicBaseUrl) {
        this.s3 = s3;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    public static S3MediaStorage create(
            String endpoint, String accessKeyId, String secretAccessKey,
            String bucket, String publicBaseUrl) {
        S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
        return new S3MediaStorage(s3, bucket, publicBaseUrl);
    }

    @Override
    public StoredObject store(byte[] content, String contentType) throws IOException {
        String objectKey = keyFor(content);
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
            return new StoredObject(objectKey);
        } catch (S3Exception e) {
            throw new IOException("R2 写入失败：" + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public byte[] load(String objectKey) throws IOException {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(objectKey).build()).asByteArray();
        } catch (S3Exception e) {
            throw new IOException("R2 读取失败：" + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (S3Exception e) {
            throw new IOException("R2 删除失败：" + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return true;
        } catch (S3Exception e) {
            return false;
        }
    }

    @Override
    public String publicUrl(String objectKey) {
        return publicBaseUrl + "/" + objectKey;
    }

    private String keyFor(byte[] content) {
        String hash = com.myblog.backend.utils.TokenUtil.sha256Hex(content);
        java.time.LocalDate today = java.time.LocalDate.now();
        return String.format("%d/%02d/%s", today.getYear(), today.getMonthValue(),
                hash.substring(0, 16));
    }
}