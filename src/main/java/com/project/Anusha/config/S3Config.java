package com.project.Anusha.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3Config {

    @Value("${aws.access-key-id}")
    private String accessKeyId;

    @Value("${aws.secret-access-key}")
    private String secretAccessKey;

    @Value("${aws.region}")
    private String region;

    @Bean
    public AmazonS3 amazonS3() {
        BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKeyId, secretAccessKey);

        // Explicit timeouts so a blocked S3 call fails fast (returns 500)
        // instead of hanging the HTTP connection until the browser gives up (FETCH_ERROR).
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setConnectionTimeout(5_000);   // 5 s to establish TCP
        clientConfig.setSocketTimeout(30_000);       // 30 s to receive data
        clientConfig.setRequestTimeout(60_000);      // 60 s total per request
        clientConfig.setMaxErrorRetry(2);

        return AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .withClientConfiguration(clientConfig)
                .build();
    }
}