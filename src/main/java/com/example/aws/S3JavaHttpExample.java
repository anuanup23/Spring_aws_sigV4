package com.example.aws;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Formatter;
import java.util.Locale;
import java.util.TreeMap;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

public class S3JavaHttpExample {
    private static final String REGION = "ap-south-1";
    private static final String BUCKET_NAME = "sigv4-test-bucket-" + System.currentTimeMillis();
    private static final String FILE_KEY = "test-file.txt";
    private static final String FILE_CONTENT = String.format(
        "Hello AWS S3!\n" +
        "This is a test file uploaded at: %s\n" +
        "Using SigV4 signing process.",
        LocalDateTime.now()
    );

    private static void createBucket(String bucketName, String region, AwsBasicCredentials credentials) throws Exception {
        String requestBody = String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<CreateBucketConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">" +
            "    <LocationConstraint>%s</LocationConstraint>" +
            "</CreateBucketConfiguration>",
            region
        );

        byte[] contentBytes = requestBody.getBytes(StandardCharsets.UTF_8);
        String contentLength = String.valueOf(contentBytes.length);
        
        System.out.println("\n=== Bucket Creation Details ===");
        System.out.println("Bucket Name: " + bucketName);
        System.out.println("Region: " + region);
        System.out.println("Request Body: " + requestBody);
        System.out.println("Content Length: " + contentLength);
        
        // Calculate SHA256 of payload
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] payloadHash = digest.digest(contentBytes);
        String payloadHashHex = bytesToHex(payloadHash);
        System.out.println("Content SHA256: " + payloadHashHex);

        // Get current time in UTC
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        System.out.println("\n=== Time Details ===");
        System.out.println("AMZ Date: " + amzDate);
        System.out.println("Date Stamp: " + dateStamp);

        // Prepare headers
        TreeMap<String, String> headers = new TreeMap<>();
        headers.put("content-length", contentLength);
        headers.put("content-type", "application/xml");
        headers.put("host", bucketName + ".s3." + region + ".amazonaws.com");
        headers.put("x-amz-content-sha256", payloadHashHex);
        headers.put("x-amz-date", amzDate);

        System.out.println("\n=== Headers ===");
        headers.forEach((key, value) -> System.out.println(key + ": " + value));

        // Create canonical request
        String canonicalRequest = createCanonicalRequest("PUT", "/", headers, payloadHashHex);
        System.out.println("\n=== Canonical Request ===");
        System.out.println(canonicalRequest);

        // Create string to sign
        String stringToSign = createStringToSign(canonicalRequest, amzDate, dateStamp);
        System.out.println("\n=== String to Sign ===");
        System.out.println(stringToSign);

        // Calculate signature
        String signature = calculateSignature(stringToSign, credentials.secretAccessKey(), dateStamp, region);
        System.out.println("\n=== Signature Details ===");
        System.out.println("Signature: " + signature);

        String signedHeaders = String.join(";", headers.keySet());
        String authorizationHeader = String.format(
            "AWS4-HMAC-SHA256 Credential=%s/%s/%s/s3/aws4_request, SignedHeaders=%s, Signature=%s",
            credentials.accessKeyId(), dateStamp, region, signedHeaders, signature);
        
        System.out.println("\n=== Authorization Header ===");
        System.out.println(authorizationHeader);

        System.out.println("\n=== S3 URL ===");
        String s3Url = String.format("https://%s.s3.%s.amazonaws.com", bucketName, region);
        System.out.println(s3Url);

        URL url = new URL(s3Url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);

        // Set headers
        for (String headerName : headers.keySet()) {
            String headerValue = headers.get(headerName);
            String properHeaderName = headerName.equals("content-length") ? "Content-Length" :
                                    headerName.equals("content-type") ? "Content-Type" :
                                    headerName.equals("host") ? "Host" : headerName;
            connection.setRequestProperty(properHeaderName, headerValue);
        }
        connection.setRequestProperty("Authorization", authorizationHeader);

        System.out.println("\n=== Sending Request ===");
        // Write the content
        try (OutputStream out = connection.getOutputStream()) {
            out.write(contentBytes);
        }

        // Get the response
        int responseCode = connection.getResponseCode();
        System.out.println("\n=== Response ===");
        System.out.println("Response Code: " + responseCode);

        if (responseCode >= 400) {
            try (BufferedReader in = new BufferedReader(
                new InputStreamReader(connection.getErrorStream()))) {
                String line;
                System.out.println("Error Response:");
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                }
            }
        } else {
            System.out.println("Bucket created successfully!");
        }
    }

    public static void main(String[] args) {
        try {
            // Get AWS credentials using the default credential provider chain
            ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
            AwsBasicCredentials credentials = (AwsBasicCredentials) credentialsProvider.resolveCredentials();
            
            if (credentials == null) {
                throw new RuntimeException("AWS credentials not found. Please configure your AWS credentials.");
            }

            System.out.println("\n=== Starting S3 Upload Process ===");
            System.out.println("Bucket: " + BUCKET_NAME);
            System.out.println("File Key: " + FILE_KEY);
            System.out.println("Region: " + REGION);
            System.out.println("Access Key: " + credentials.accessKeyId().substring(0, 4) + "..." + credentials.accessKeyId().substring(credentials.accessKeyId().length() - 4));

            // First create the bucket
            createBucket(BUCKET_NAME, REGION, credentials);

            // Then proceed with file upload
            // Calculate content length and hash
            byte[] contentBytes = FILE_CONTENT.getBytes(StandardCharsets.UTF_8);
            String contentLength = String.valueOf(contentBytes.length);
            
            System.out.println("\n=== Content Details ===");
            System.out.println("Content Length: " + contentLength);
            System.out.println("Content Preview: " + FILE_CONTENT.substring(0, Math.min(50, FILE_CONTENT.length())) + "...");
            
            // Calculate SHA256 of payload
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] payloadHash = digest.digest(contentBytes);
            String payloadHashHex = bytesToHex(payloadHash);
            System.out.println("Content SHA256: " + payloadHashHex);

            // Get current time in UTC
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
            String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            
            System.out.println("\n=== Time Details ===");
            System.out.println("AMZ Date: " + amzDate);
            System.out.println("Date Stamp: " + dateStamp);

            // Prepare headers
            TreeMap<String, String> headers = new TreeMap<>();
            headers.put("content-length", contentLength);
            headers.put("content-type", "text/plain");
            headers.put("host", BUCKET_NAME + ".s3." + REGION + ".amazonaws.com");
            headers.put("x-amz-content-sha256", payloadHashHex);
            headers.put("x-amz-date", amzDate);

            System.out.println("\n=== Headers ===");
            headers.forEach((key, value) -> System.out.println(key + ": " + value));

            // Create canonical request
            String canonicalRequest = createCanonicalRequest("PUT", "/" + FILE_KEY, headers, payloadHashHex);
            System.out.println("\n=== Canonical Request ===");
            System.out.println(canonicalRequest);

            // Create string to sign
            String stringToSign = createStringToSign(canonicalRequest, amzDate, dateStamp);
            System.out.println("\n=== String to Sign ===");
            System.out.println(stringToSign);

            // Calculate signature
            String signature = calculateSignature(stringToSign, credentials.secretAccessKey(), dateStamp, REGION);
            System.out.println("\n=== Signature Details ===");
            System.out.println("Signature: " + signature);

            // Create authorization header
            String signedHeaders = String.join(";", headers.keySet());
            String authorizationHeader = String.format(
                "AWS4-HMAC-SHA256 Credential=%s/%s/%s/s3/aws4_request, SignedHeaders=%s, Signature=%s",
                credentials.accessKeyId(), dateStamp, REGION, signedHeaders, signature);
            
            System.out.println("\n=== Authorization Header ===");
            System.out.println(authorizationHeader);

            // Create the S3 URL and connection
            String s3Url = String.format("https://%s.s3.%s.amazonaws.com/%s", 
                BUCKET_NAME, REGION, FILE_KEY);
            System.out.println("\n=== S3 URL ===");
            System.out.println(s3Url);

            URL url = new URL(s3Url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);

            // Set all headers
            for (String headerName : headers.keySet()) {
                String headerValue = headers.get(headerName);
                // Convert header name to proper case for actual HTTP request
                String properHeaderName = headerName.equals("content-length") ? "Content-Length" :
                                        headerName.equals("content-type") ? "Content-Type" :
                                        headerName.equals("host") ? "Host" : headerName;
                connection.setRequestProperty(properHeaderName, headerValue);
            }
            connection.setRequestProperty("Authorization", authorizationHeader);

            System.out.println("\n=== Sending Request ===");
            // Write the content
            try (OutputStream out = connection.getOutputStream()) {
                out.write(contentBytes);
            }

            // Get the response
            int responseCode = connection.getResponseCode();
            System.out.println("\n=== Response ===");
            System.out.println("Response Code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("File uploaded successfully!");
                System.out.println("S3 URI: " + s3Url);
            } else {
                try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream()))) {
                    String line;
                    System.out.println("Error Response:");
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String createCanonicalRequest(String method, String uri, TreeMap<String, String> headers, String payloadHash) {
        StringBuilder canonicalRequest = new StringBuilder();
        
        // HTTP Method
        canonicalRequest.append(method).append("\n");
        
        // Canonical URI
        canonicalRequest.append(uri).append("\n");
        
        // Canonical Query String (empty in this case)
        canonicalRequest.append("\n");
        
        // Canonical Headers
        for (String headerName : headers.keySet()) {
            canonicalRequest.append(headerName).append(":").append(headers.get(headerName)).append("\n");
        }
        
        // Empty line after headers
        canonicalRequest.append("\n");
        
        // Signed Headers
        canonicalRequest.append(String.join(";", headers.keySet())).append("\n");
        
        // Payload Hash
        canonicalRequest.append(payloadHash);
        
        return canonicalRequest.toString();
    }

    private static String createStringToSign(String canonicalRequest, String amzDate, String dateStamp) throws Exception {
        StringBuilder stringToSign = new StringBuilder();
        stringToSign.append("AWS4-HMAC-SHA256\n");
        stringToSign.append(amzDate).append("\n");
        stringToSign.append(dateStamp).append("/").append(REGION).append("/s3/aws4_request\n");
        
        // Hash the canonical request
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        stringToSign.append(bytesToHex(hash));
        
        return stringToSign.toString();
    }

    private static String calculateSignature(String stringToSign, String secretKey, String dateStamp, String region) throws Exception {
        byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSHA256(kSecret, dateStamp);
        byte[] kRegion = hmacSHA256(kDate, region);
        byte[] kService = hmacSHA256(kRegion, "s3");
        byte[] kSigning = hmacSHA256(kService, "aws4_request");
        byte[] signature = hmacSHA256(kSigning, stringToSign);
        return bytesToHex(signature);
    }

    private static byte[] hmacSHA256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
} 