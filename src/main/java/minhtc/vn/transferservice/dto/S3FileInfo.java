package minhtc.vn.transferservice.dto;

import java.io.InputStream;

public record S3FileInfo(String fileName,
                         InputStream fileContent,
                         long contentLength,
                         String contentType) {
}