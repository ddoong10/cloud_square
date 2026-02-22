package com.uos.lms.certificate;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
// Helvetica fallback removed - fail-fast if Korean fonts missing
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class CertificatePdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");

    public byte[] generate(String userName, String courseName, LocalDateTime issuedAt,
                           String certificateNumber, String verificationUrl) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            // Load Korean fonts
            PDFont fontBold;
            PDFont fontRegular;
            try (InputStream boldStream = getClass().getResourceAsStream("/fonts/NanumGothic-Bold.ttf");
                 InputStream regularStream = getClass().getResourceAsStream("/fonts/NanumGothic-Regular.ttf")) {
                if (boldStream != null && regularStream != null) {
                    fontBold = PDType0Font.load(document, boldStream);
                    fontRegular = PDType0Font.load(document, regularStream);
                } else {
                    throw new IllegalStateException("Korean font files (NanumGothic) not found in classpath. "
                            + "Certificates cannot be generated without Korean font support.");
                }
            }

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                // Outer border
                cs.setLineWidth(3);
                cs.addRect(30, 30, pageWidth - 60, pageHeight - 60);
                cs.stroke();

                // Inner border
                cs.setLineWidth(1);
                cs.addRect(38, 38, pageWidth - 76, pageHeight - 76);
                cs.stroke();

                // Decorative corner accents
                float m = 30;
                float cornerLen = 40;
                cs.setLineWidth(2);
                // Top-left
                cs.moveTo(m, pageHeight - m - cornerLen);
                cs.lineTo(m, pageHeight - m);
                cs.lineTo(m + cornerLen, pageHeight - m);
                cs.stroke();
                // Top-right
                cs.moveTo(pageWidth - m - cornerLen, pageHeight - m);
                cs.lineTo(pageWidth - m, pageHeight - m);
                cs.lineTo(pageWidth - m, pageHeight - m - cornerLen);
                cs.stroke();
                // Bottom-left
                cs.moveTo(m, m + cornerLen);
                cs.lineTo(m, m);
                cs.lineTo(m + cornerLen, m);
                cs.stroke();
                // Bottom-right
                cs.moveTo(pageWidth - m - cornerLen, m);
                cs.lineTo(pageWidth - m, m);
                cs.lineTo(pageWidth - m, m + cornerLen);
                cs.stroke();

                // Title
                drawCenteredText(cs, fontBold, 32, "수 료 증", pageWidth, pageHeight - 110);

                // Subtitle
                drawCenteredText(cs, fontRegular, 12, "CERTIFICATE OF COMPLETION", pageWidth, pageHeight - 138);

                // Line separator
                cs.setLineWidth(0.5f);
                cs.moveTo(pageWidth / 2 - 120, pageHeight - 158);
                cs.lineTo(pageWidth / 2 + 120, pageHeight - 158);
                cs.stroke();

                // Body text
                drawCenteredText(cs, fontRegular, 13, "위 사람은 아래 과정을 성실히 이수하였으므로", pageWidth, pageHeight - 200);
                drawCenteredText(cs, fontRegular, 13, "이 증서를 수여합니다.", pageWidth, pageHeight - 220);

                // Student name
                String displayName = (userName != null && !userName.isBlank()) ? userName : "수료자";
                drawCenteredText(cs, fontBold, 28, displayName, pageWidth, pageHeight - 275);

                // Course name label
                drawCenteredText(cs, fontRegular, 11, "이수 과정", pageWidth, pageHeight - 320);

                // Course name
                drawCenteredText(cs, fontBold, 18, courseName, pageWidth, pageHeight - 348);

                // Completion date
                String dateStr = issuedAt.format(DATE_FORMAT);
                drawCenteredText(cs, fontRegular, 12, "수료일: " + dateStr, pageWidth, pageHeight - 395);

                // Certificate number
                drawCenteredText(cs, fontRegular, 10, "이수증 번호: " + certificateNumber, pageWidth, pageHeight - 415);

                // Issuing organization
                drawCenteredText(cs, fontBold, 14, "경기도교통연수원", pageWidth, pageHeight - 470);
                drawCenteredText(cs, fontRegular, 10, "GYEONGGI TRAFFIC TRAINING INSTITUTE", pageWidth, pageHeight - 490);

                // QR Code
                if (verificationUrl != null) {
                    try {
                        byte[] qrBytes = generateQrCode(verificationUrl, 100, 100);
                        PDImageXObject qrImage = PDImageXObject.createFromByteArray(document, qrBytes, "qr");
                        float qrX = (pageWidth - 80) / 2;
                        cs.drawImage(qrImage, qrX, 55, 80, 80);
                        drawCenteredText(cs, fontRegular, 7, "QR 코드를 스캔하여 진위를 확인하세요", pageWidth, 46);
                    } catch (WriterException e) {
                        log.warn("Failed to generate QR code for certificate {}", certificateNumber, e);
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private void drawCenteredText(PDPageContentStream cs, PDFont font, float fontSize,
                                  String text, float pageWidth, float y) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000 * fontSize;
        float x = (pageWidth - textWidth) / 2;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private byte[] generateQrCode(String text, int width, int height) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
}
