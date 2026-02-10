package com.uos.lms.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(StorageProperties.class)
public class S3Config {

    @Bean
    public AmazonS3 amazonS3(StorageProperties storageProperties) {
        BasicAWSCredentials basicAWSCredentials = new BasicAWSCredentials(
                storageProperties.getAccessKey(),
                storageProperties.getSecretKey()
        );

        return AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(
                                storageProperties.getEndpoint(),
                                storageProperties.getRegion()
                        )
                )
                .withCredentials(new AWSStaticCredentialsProvider(basicAWSCredentials))
                .withPathStyleAccessEnabled(true)
                .disableChunkedEncoding()
                .build();
    }
}
