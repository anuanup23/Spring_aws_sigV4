package com.example.aws;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class S3BucketCreator {
    private static final String REGION = "us-west-2"; // Changed to US West (Oregon)
    private static final String BUCKET_NAME = "my-sigv4-example-bucket-west-anupkmr";

    public static void main(String[] args) {
        try {
            createBucket();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String calculateSHA256Hash(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static void createBucket() throws Exception {
        // Get credentials from the default credentials provider
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
        AwsBasicCredentials credentials = (AwsBasicCredentials) credentialsProvider.resolveCredentials();
        
        // Create request body for location constraint
        String requestBody = String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<CreateBucketConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">" +
            "    <LocationConstraint>%s</LocationConstraint>" +
            "</CreateBucketConfiguration>",
            REGION
        );

        // Calculate SHA256 hash of the request body
        String contentSha256 = calculateSHA256Hash(requestBody);
        
        // Create the request
        SdkHttpFullRequest.Builder requestBuilder = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.PUT)
                .protocol("https")
                .host("s3.us-west-2.amazonaws.com")
                .encodedPath("/" + BUCKET_NAME)
                .putHeader("Host", "s3.us-west-2.amazonaws.com")
                .putHeader("x-amz-date", getAmzDate())
                .putHeader("x-amz-content-sha256", contentSha256)
                .putHeader("Content-Type", "application/xml");

        // Add the request body
        requestBuilder.contentStreamProvider(() -> 
            new java.io.ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8)));

        // Create signer parameters
        Aws4SignerParams signerParams = Aws4SignerParams.builder()
                .awsCredentials(credentials)
                .signingName("s3")
                .signingRegion(Region.of(REGION))
                .build();

        // Sign the request
        Aws4Signer signer = Aws4Signer.create();
        SdkHttpFullRequest signedRequest = signer.sign(requestBuilder.build(), signerParams);

        // Create HTTP client and execute the request
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPut httpPut = new HttpPut(signedRequest.getUri());
            
            // Add all headers from the signed request
            signedRequest.headers().forEach((key, values) -> 
                values.forEach(value -> httpPut.addHeader(key, value)));

            // Add the request body
            httpPut.setEntity(new StringEntity(requestBody));

            // Execute the request
            org.apache.http.HttpResponse response = httpClient.execute(httpPut);
            HttpEntity entity = response.getEntity();
            
            System.out.println("Request Body: " + requestBody);
            System.out.println("Content SHA256: " + contentSha256);
            System.out.println("Response Status: " + response.getStatusLine());
            if (entity != null) {
                System.out.println("Response Body: " + EntityUtils.toString(entity));
            }
        }
    }

    private static String getAmzDate() {
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
    }
} 