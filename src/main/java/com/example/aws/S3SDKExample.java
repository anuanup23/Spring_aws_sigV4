package com.example.aws;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.profiles.ProfileFile;
import java.nio.file.Paths;
import java.time.LocalDateTime;

public class S3SDKExample {
    private static final String REGION = "us-west-2";
    private static final String BUCKET_NAME = "my-sdk-example-bucket";
    private static final String FILE_KEY = "example-data.txt";
    private static final String FILE_CONTENT = String.format(
        "Sample data file\n" +
        "Created at: %s\n" +
        "This is a test file uploaded using AWS SDK for Java.\n" +
        "It contains multiple lines of text\n" +
        "Line 1: Hello from AWS SDK\n" +
        "Line 2: Testing file upload\n" +
        "Line 3: Using S3 client\n" +
        "End of file.",
        LocalDateTime.now()
    );

    public static void main(String[] args) {
        try {
            // Create credentials provider
            ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
            
            // Create S3 client
            S3Client s3Client = S3Client.builder()
                    .region(Region.of(REGION))
                    .credentialsProvider(credentialsProvider)
                    .build();

            // Create bucket (if it doesn't exist)
            createBucket(s3Client);
            
            // Upload file
            uploadFile(s3Client);
            
            // Close the client
            s3Client.close();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createBucket(S3Client s3Client) {
        try {
            System.out.println("\nChecking/Creating bucket: " + BUCKET_NAME);
            
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                    .bucket(BUCKET_NAME)
                    .createBucketConfiguration(
                        CreateBucketConfiguration.builder()
                            .locationConstraint(REGION)
                            .build())
                    .build();

            s3Client.createBucket(createBucketRequest);
            System.out.println("Bucket created successfully!");
            
        } catch (BucketAlreadyOwnedByYouException e) {
            System.out.println("Using existing bucket (already owned by you): " + BUCKET_NAME);
        } catch (Exception e) {
            System.err.println("Error creating bucket: " + e.getMessage());
            throw e;
        }
    }

    private static void uploadFile(S3Client s3Client) {
        System.out.println("\nUploading file: " + FILE_KEY);
        System.out.println("File content length: " + FILE_CONTENT.length() + " characters");
        System.out.println("First few lines of content:");
        System.out.println("----------------------------------------");
        System.out.println(FILE_CONTENT.substring(0, Math.min(FILE_CONTENT.length(), 200)));
        System.out.println("----------------------------------------");
        
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(FILE_KEY)
                .contentType("text/plain")
                .build();

        s3Client.putObject(putObjectRequest, 
            RequestBody.fromString(FILE_CONTENT));
            
        System.out.println("\nFile uploaded successfully!");
        System.out.println("S3 URI: s3://" + BUCKET_NAME + "/" + FILE_KEY);
    }
} 