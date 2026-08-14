package com.myblog.publicsite.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 媒体存储选择（#24）：{@code site.media.storage=local}（默认，开发）或
 * {@code s3}（生产 R2）。统一 {@link MediaStorage} 能力，上传/删除只经
 * 认证 Admin API。
 */
@Configuration
public class MediaStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "site.media.storage", havingValue = "local", matchIfMissing = true)
    public MediaStorage localMediaStorage(
            @Value("${site.media.local-root:data/media}") String localRoot) {
        return new LocalMediaStorage(localRoot);
    }

    @Bean
    @ConditionalOnProperty(name = "site.media.storage", havingValue = "s3")
    public MediaStorage s3MediaStorage(
            @Value("${R2_ENDPOINT:}") String endpoint,
            @Value("${R2_ACCESS_KEY_ID:}") String accessKeyId,
            @Value("${R2_SECRET_ACCESS_KEY:}") String secretAccessKey,
            @Value("${R2_BUCKET:}") String bucket,
            @Value("${R2_PUBLIC_BASE_URL:}") String publicBaseUrl) {
        return S3MediaStorage.create(endpoint, accessKeyId, secretAccessKey, bucket, publicBaseUrl);
    }
}
