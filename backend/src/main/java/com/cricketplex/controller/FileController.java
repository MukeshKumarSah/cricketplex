package com.cricketplex.controller;

import com.cricketplex.service.FileStorageService;
import io.minio.GetObjectResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @GetMapping("/**")
    public ResponseEntity<InputStreamResource> getFile(HttpServletRequest request) {
        String path = request.getRequestURI();
        String prefix = "/api/files/";
        int idx = path.indexOf(prefix);
        if (idx == -1) {
            return ResponseEntity.notFound().build();
        }
        String objectKey = path.substring(idx + prefix.length());

        if (objectKey.isBlank() || objectKey.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            GetObjectResponse response = fileStorageService.getFile(objectKey);
            String contentType = response.headers().get("Content-Type");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            contentType != null ? contentType : "application/octet-stream"))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(new InputStreamResource(response));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
