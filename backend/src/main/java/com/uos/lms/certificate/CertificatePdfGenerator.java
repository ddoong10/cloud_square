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
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class CertificatePdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public byte[] generate(String userName, String courseName, LocalDateTime issuedAt,
                           String certificateNumber, String verificationUrl) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                // Border
                cs.setLineWidth(3);
                cs.addRect(30, 30, pageWidth - 60, pageHeight - 60);
                cs.stroke();

                cs.setLineWidth(1);
                cs.addRect(35, 35, pageWidth - 70, pageHeight - 70);
                cs.stroke();

                // Title
                drawCenteredText(cs, fontBold, 28, "CERTIFICATE", pageWidth, pageHeight - 120);
                drawCenteredText(cs, fontRegular, 14, "OF COMPLETION", pageWidth, pageHeight - 150);

                // Line separator
                cs.setLineWidth(1);
                cs.moveTo(pageWidth / 2 - 100, pageHeight - 170);
                cs.lineTo(pageWidth / 2 + 100, pageHeight - 170);
                cs.stroke();

                // Body text
                drawCenteredText(cs, fontRegular, 12, "This is to certify that", pageWidth, pageHeight - 210);
                drawCenteredText(cs, fontBold, 22, userName != null ? userName : "Student", pageWidth, pageHeight - 245);
                drawCenteredText(cs, fontRegular, 12, "has successfully completed the course", pageWidth, pageHeight - 280);
                drawCenteredText(cs, fontBold, 16, courseName, pageWidth, pageHeight - 310);

                // Date and number
                String dateStr = issuedAt.format(DATE_FORMAT);
                drawCenteredText(cs, fontRegular, 11, "Date of Completion: " + dateStr, pageWidth, pageHeight - 360);
                drawCenteredText(cs, fontRegular, 10, "Certificate No: " + certificateNumber, pageWidth, pageHeight - 380);

                // QR Code
                if (verificationUrl != null) {
                    try {
                        byte[] qrBytes = generateQrCode(verificationUrl, 100, 100);
                        PDImageXObject qrImage = PDImageXObject.createFromByteArray(document, qrBytes, "qr");
                        float qrX = (pageWidth - 100) / 2;
                        cs.drawImage(qrImage, qrX, 60, 100, 100);
                        drawCenteredText(cs, fontRegular, 8, "Scan to verify", pageWidth, 50);
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

    private void drawCenteredText(PDPageContentStream cs, PDType1Font font, float fontSize,
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
