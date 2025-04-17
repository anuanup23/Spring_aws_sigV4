package com.example.aws;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

public class S3ListBucketsAndContents {
    private static final String REGION = "ap-south-1";
    private static final String SERVICE = "s3";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";

    public static void main(String[] args) {
        try {
            // Get AWS credentials using the default credential provider chain
            ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
            AwsBasicCredentials credentials = (AwsBasicCredentials) credentialsProvider.resolveCredentials();
            
            if (credentials == null) {
                throw new RuntimeException("AWS credentials not found. Please configure your AWS credentials.");
            }

            // First list all buckets
            listBuckets(credentials);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void listBuckets(AwsBasicCredentials credentials) throws Exception {
        // Get current time in UTC
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // Prepare headers
        TreeMap<String, String> headers = new TreeMap<>();
        headers.put("host", "s3." + REGION + ".amazonaws.com");
        headers.put("x-amz-date", amzDate);
        headers.put("x-amz-content-sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        // Create canonical request
        String canonicalRequest = createCanonicalRequest("GET", "/", headers, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        // Create string to sign
        String stringToSign = createStringToSign(canonicalRequest, amzDate, dateStamp);

        // Calculate signature
        String signature = calculateSignature(stringToSign, credentials.secretAccessKey(), dateStamp, REGION);

        String signedHeaders = String.join(";", headers.keySet());
        String authorizationHeader = String.format(
            "AWS4-HMAC-SHA256 Credential=%s/%s/%s/s3/aws4_request, SignedHeaders=%s, Signature=%s",
            credentials.accessKeyId(), dateStamp, REGION, signedHeaders, signature);

        // Create the S3 URL and connection
        String s3Url = "https://s3." + REGION + ".amazonaws.com";

        URL url = new URL(s3Url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Set headers
        for (String headerName : headers.keySet()) {
            String headerValue = headers.get(headerName);
            String properHeaderName = headerName.equals("host") ? "Host" : headerName;
            connection.setRequestProperty(properHeaderName, headerValue);
        }
        connection.setRequestProperty("Authorization", authorizationHeader);

        // Get the response
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            // Read the entire response into a string
            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
            }

            System.out.println("\n=== Bucket List XML Response ===");
            System.out.println(response.toString());
            System.out.println("=== End of Bucket List XML Response ===\n");

            // Parse the XML response
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(response.toString())));

            System.out.println("\n=== Document Object ===");
            System.out.println("Document Node Name: " + doc.getNodeName());
            System.out.println("Document Node Type: " + doc.getNodeType());
            System.out.println("Document Namespace URI: " + doc.getNamespaceURI());
            System.out.println("Document Local Name: " + doc.getLocalName());

            // Get all Bucket elements
            NodeList buckets = doc.getElementsByTagName("Bucket");
            System.out.println("\n=== Buckets NodeList ===");
            System.out.println("Number of Buckets: " + buckets.getLength());
            for (int i = 0; i < buckets.getLength(); i++) {
                Node bucket = buckets.item(i);
                System.out.println("\nBucket " + (i + 1) + ":");
                System.out.println("Node Name: " + bucket.getNodeName());
                System.out.println("Node Type: " + bucket.getNodeType());
                if (bucket instanceof Element) {
                    Element bucketElement = (Element) bucket;
                    System.out.println("Name: " + bucketElement.getElementsByTagName("Name").item(0).getTextContent());
                    System.out.println("CreationDate: " + bucketElement.getElementsByTagName("CreationDate").item(0).getTextContent());
                }
            }
            
            int totalFiles = 0;
            
            for (int i = 0; i < buckets.getLength(); i++) {
                Element bucket = (Element) buckets.item(i);
                NodeList nameNodes = bucket.getElementsByTagName("Name");
                if (nameNodes.getLength() > 0) {
                    String bucketName = nameNodes.item(0).getTextContent();
                    if (bucketName.startsWith("sigv4")) {
                        totalFiles += listBucketContents(bucketName, credentials);
                    }
                }
            }
            
            System.out.println("\nTotal files fetched: " + totalFiles);
        }
    }

    private static int listBucketContents(String bucketName, AwsBasicCredentials credentials) throws Exception {
        // Get current time in UTC
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // Prepare headers
        TreeMap<String, String> headers = new TreeMap<>();
        headers.put("host", bucketName + ".s3." + REGION + ".amazonaws.com");
        headers.put("x-amz-date", amzDate);
        headers.put("x-amz-content-sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        // Create canonical request
        String canonicalRequest = createCanonicalRequest("GET", "/", headers, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        // Create string to sign
        String stringToSign = createStringToSign(canonicalRequest, amzDate, dateStamp);

        // Calculate signature
        String signature = calculateSignature(stringToSign, credentials.secretAccessKey(), dateStamp, REGION);

        String signedHeaders = String.join(";", headers.keySet());
        String authorizationHeader = String.format(
            "AWS4-HMAC-SHA256 Credential=%s/%s/%s/s3/aws4_request, SignedHeaders=%s, Signature=%s",
            credentials.accessKeyId(), dateStamp, REGION, signedHeaders, signature);

        // Create the S3 URL and connection
        String s3Url = "https://" + bucketName + ".s3." + REGION + ".amazonaws.com";

        URL url = new URL(s3Url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Set headers
        for (String headerName : headers.keySet()) {
            String headerValue = headers.get(headerName);
            String properHeaderName = headerName.equals("host") ? "Host" : headerName;
            connection.setRequestProperty(properHeaderName, headerValue);
        }
        connection.setRequestProperty("Authorization", authorizationHeader);

        // Get the response
        int responseCode = connection.getResponseCode();
        int fileCount = 0;
        
        if (responseCode == HttpURLConnection.HTTP_OK) {
            // Read the entire response into a string
            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
            }

            System.out.println("\n=== Bucket Contents XML Response for " + bucketName + " ===");
            System.out.println(response.toString());
            System.out.println("=== End of Bucket Contents XML Response ===\n");

            // Parse the XML response
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(response.toString())));

            System.out.println("\n=== Document Object for Bucket Contents ===");
            System.out.println("Document Node Name: " + doc.getNodeName());
            System.out.println("Document Node Type: " + doc.getNodeType());
            System.out.println("Document Namespace URI: " + doc.getNamespaceURI());
            System.out.println("Document Local Name: " + doc.getLocalName());

            // Get all Contents elements
            NodeList contents = doc.getElementsByTagName("Contents");
            System.out.println("\n=== Contents NodeList ===");
            System.out.println("Number of Contents: " + contents.getLength());
            for (int i = 0; i < contents.getLength(); i++) {
                Node content = contents.item(i);
                System.out.println("\nContent " + (i + 1) + ":");
                System.out.println("Node Name: " + content.getNodeName());
                System.out.println("Node Type: " + content.getNodeType());
                if (content instanceof Element) {
                    Element contentElement = (Element) content;
                    System.out.println("Key: " + contentElement.getElementsByTagName("Key").item(0).getTextContent());
                    System.out.println("Size: " + contentElement.getElementsByTagName("Size").item(0).getTextContent());
                    System.out.println("LastModified: " + contentElement.getElementsByTagName("LastModified").item(0).getTextContent());
                }
            }
            
            for (int i = 0; i < contents.getLength(); i++) {
                Element content = (Element) contents.item(i);
                NodeList keyNodes = content.getElementsByTagName("Key");
                
                if (keyNodes.getLength() > 0) {
                    String fileKey = keyNodes.item(0).getTextContent();
                    System.out.println("\nFile: " + fileKey);
                    readFileContents(bucketName, fileKey, credentials);
                    fileCount++;
                }
            }
        }
        return fileCount;
    }

    private static void readFileContents(String bucketName, String fileKey, AwsBasicCredentials credentials) throws Exception {
        // Get current time in UTC
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // Prepare headers
        TreeMap<String, String> headers = new TreeMap<>();
        headers.put("host", bucketName + ".s3." + REGION + ".amazonaws.com");
        headers.put("x-amz-date", amzDate);
        headers.put("x-amz-content-sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        // Create canonical request
        String canonicalRequest = createCanonicalRequest("GET", "/" + fileKey, headers, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        // Create string to sign
        String stringToSign = createStringToSign(canonicalRequest, amzDate, dateStamp);

        // Calculate signature
        String signature = calculateSignature(stringToSign, credentials.secretAccessKey(), dateStamp, REGION);

        String signedHeaders = String.join(";", headers.keySet());
        String authorizationHeader = String.format(
            "AWS4-HMAC-SHA256 Credential=%s/%s/%s/s3/aws4_request, SignedHeaders=%s, Signature=%s",
            credentials.accessKeyId(), dateStamp, REGION, signedHeaders, signature);

        // Create the S3 URL and connection
        String s3Url = "https://" + bucketName + ".s3." + REGION + ".amazonaws.com/" + fileKey;

        URL url = new URL(s3Url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Set headers
        for (String headerName : headers.keySet()) {
            String headerValue = headers.get(headerName);
            String properHeaderName = headerName.equals("host") ? "Host" : headerName;
            connection.setRequestProperty(properHeaderName, headerValue);
        }
        connection.setRequestProperty("Authorization", authorizationHeader);

        // Get the response
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader in = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
                String line;
                System.out.println("Contents:");
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                }
            }
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