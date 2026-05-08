package com.PrimeCare.PrimeCare.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.s3")
public class S3Properties {
    private String region = "ap-southeast-1";
    private String bucket = "primecare-dev";
    private String accessKey = "local-access-key";
    private String secretKey = "local-secret-key";
    private String publicBaseUrl;
}
