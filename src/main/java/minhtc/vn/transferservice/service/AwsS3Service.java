package minhtc.vn.transferservice.service;

import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.dto.S3FileInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class AwsS3Service {
    @Value("${app.r2.bucket-name}")
    private String bucketName;

    private final S3Client s3Client;

    private static final String FORMAT_FILE_NAME = "%s/%s";

    public InputStream load(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3Client.getObject(getObjectRequest);
    }

    // save file having length less than 5 mb
    public String save(byte[] file, String prefix, String fileId, String contentType) {
        String fileName = String.format(FORMAT_FILE_NAME,prefix,fileId);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(contentType)
                .contentLength((long) file.length)
                .build();
        RequestBody body = RequestBody.fromBytes(file);
        s3Client.putObject(request, body);
        return fileName;
    }

    // save large file 5 MB - 100 MB
    public String save(InputStream inputStream,
                       long contentLength,
                       String prefix,
                       String fileId,
                       String contentType) {

        String key = String.format(FORMAT_FILE_NAME,prefix,fileId);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromInputStream(inputStream, contentLength)
        );

        return key;
    }

    public String save(MultipartFile file,
                       String prefix,
                       String fileId) throws IOException {

        String key = String.format(FORMAT_FILE_NAME,prefix,fileId);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );

        return key;
    }

    public S3FileInfo getFileInfo(String key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest);
        GetObjectResponse metadata = response.response();
        return  new S3FileInfo(key, response,
                metadata.contentLength(), metadata.contentType());
    }

    public boolean delete(String key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            // Lệnh này nếu không bị lỗi mạng hoặc lỗi quyền, nó sẽ luôn thành công
            s3Client.deleteObject(deleteRequest);

            return true;

        } catch (S3Exception e) {
            // Lỗi xảy ra khi: Lỗi mạng, sai tên bucket, hoặc App bị tước quyền (IAM)
            System.err.println("Lỗi khi xóa file trên S3: " + e.awsErrorDetails().errorMessage());
            return false;
        }
    }
}
