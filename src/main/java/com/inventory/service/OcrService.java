package com.inventory.service;


import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Service that extracts plain text from uploaded documents.
 *
 * • Digital PDFs → PDFBox extracts searchable text.
 * • Scanned PDFs or image files → PaddleOCR command‑line tool.
 */
@Service
public class OcrService {

    /**
     * Extracts text from the given {@link MultipartFile}.
     * Supports PDFs (digital via PDFBox) and images/PDF scans via PaddleOCR.
     */
    public String extractText(MultipartFile file) throws IOException {
        String contentType = Optional.ofNullable(file.getContentType())
                .orElse("application/octet-stream");

        // Try PDFBox first for PDF files (digital PDFs)
        if (contentType.contains("pdf")) {
            String pdfText = extractTextFromPdf(file.getInputStream());
            if (pdfText != null && !pdfText.isBlank()) {
                return pdfText;
            }
            // If PDFBox yields nothing, fall back to OCR (scanned PDF)
        }

        // Image files or scanned PDFs → PaddleOCR
        if (contentType.startsWith("image/") || contentType.contains("pdf")) {
            return extractTextWithOcr(file);
        }

        // Fallback: treat as plain UTF‑8 text (e.g., .txt)
        try (InputStream is = file.getInputStream()) {
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private String extractTextFromPdf(InputStream input) throws IOException {
        // Write InputStream to a temporary file because PDFBox 3 uses Loader
        File tempPdf = Files.createTempFile("ocr-pdf-", ".pdf").toFile();
        Files.copy(input, tempPdf.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try (PDDocument document = Loader.loadPDF(tempPdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } finally {
            if (tempPdf.exists()) {
                tempPdf.delete();
            }
        }
    }

    private String extractTextWithOcr(MultipartFile file) throws IOException {
        // Save multipart content to a temporary file for the PaddleOCR CLI
        File temp = Files.createTempFile("ocr-upload-", getExtension(file.getOriginalFilename()))
                .toFile();
        file.transferTo(temp);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "paddleocr",
                    "--lang", "en",
                    "--cls", "true",
                    "--output", "txt",
                    temp.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Capture stdout (useful for debugging)
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder stdout = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stdout.append(line).append(System.lineSeparator());
            }

            try {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IOException("PaddleOCR returned non‑zero exit code " + exitCode + ". Output: " + stdout);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("OCR process interrupted", e);
            }

            // PaddleOCR creates a .txt file next to the image when --output txt is used
            File txtFile = new File(temp.getAbsolutePath() + ".txt");
            if (txtFile.exists()) {
                String text = Files.readString(txtFile.toPath());
                txtFile.delete();
                return text;
            }
            // If no separate file, return captured stdout
            return stdout.toString();
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
        }
    }

    // Helper: preserve original extension, default to .png
    private String getExtension(String filename) {
        if (filename == null) return ".png";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot) : ".png";
    }
}
