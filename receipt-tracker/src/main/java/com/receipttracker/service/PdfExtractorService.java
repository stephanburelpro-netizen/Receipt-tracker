package com.receipttracker.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Extrait le texte brut d'un PDF avec Apache PDFBox 3.x.
 * Le texte est ensuite envoyé à Claude pour analyse.
 */
@Service
@Slf4j
public class PdfExtractorService {

    /**
     * Extrait tout le texte d'un PDF fourni en bytes.
     *
     * @param pdfBytes contenu binaire du PDF
     * @return texte brut, ou chaîne vide si extraction impossible
     */
    public String extractText(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            if (doc.isEncrypted()) {
                log.warn("PDF chiffré, impossible d'extraire le texte");
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            log.debug("PDF: {} pages, {} caractères extraits", doc.getNumberOfPages(), text.length());
            return text.trim();
        } catch (IOException e) {
            log.error("Erreur extraction PDF: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Vérifie qu'un contenu bytes est bien un PDF valide.
     */
    public boolean isValidPdf(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        // Magic number PDF : %PDF
        return bytes[0] == 0x25 && bytes[1] == 0x50
            && bytes[2] == 0x44 && bytes[3] == 0x46;
    }
}
