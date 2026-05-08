package com.PrimeCare.PrimeCare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(S3Properties props) {
        String region = props.getRegion() == null || props.getRegion().isBlank() ? "ap-southeast-1" : props.getRegion();
        String accessKey = props.getAccessKey() == null || props.getAccessKey().isBlank() ? "local-access-key" : props.getAccessKey();
        String secretKey = props.getSecretKey() == null || props.getSecretKey().isBlank() ? "local-secret-key" : props.getSecretKey();

        return S3Client.builder()
                       .region(Region.of(region))
                       .credentialsProvider(
                               StaticCredentialsProvider.create(
                                       AwsBasicCredentials.create(accessKey, secretKey)
                               )
                       )
                       .build();
    }
}
