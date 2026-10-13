package com.receipttracker.service;

import com.receipttracker.entity.PriceHistory;
import com.receipttracker.entity.Receipt;
import com.receipttracker.entity.ReceiptItem;
import com.receipttracker.repository.PriceHistoryRepository;
import com.receipttracker.repository.ReceiptRepository;
import com.receipttracker.service.GeminiService.ItemExtraction;
import com.receipttracker.service.GeminiService.ReceiptExtraction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PdfExtractorService pdfExtractor;
    private final GeminiService geminiService;

    @Value("${pricing.alert-threshold-percent:10.0}")
    private double alertThresholdPercent;

    @Transactional
    public Optional<Receipt> importReceipt(byte[] pdfBytes, String gmailMessageId) {

        // 1. Dédoublonnage
        if (receiptRepository.existsByGmailMessageId(gmailMessageId)) {
            log.debug("Ticket déjà importé pour messageId={}", gmailMessageId);
            return Optional.empty();
        }

        // 2. Validation PDF
        if (!pdfExtractor.isValidPdf(pdfBytes)) {
            log.warn("Fichier invalide pour messageId={}", gmailMessageId);
            return Optional.empty();
        }

        // 3. Extraction texte
        String rawText = pdfExtractor.extractText(pdfBytes);
        if (rawText.isBlank()) {
            log.warn("Texte vide extrait du PDF pour messageId={}", gmailMessageId);
            return Optional.empty();
        }

        // 4. Analyse Gemini
        ReceiptExtraction extraction = geminiService.extractReceiptData(rawText);
        if (extraction == null || extraction.getDate() == null || extraction.getMagasin() == null) {
            log.error("Gemini n'a pas pu analyser le ticket pour messageId={}", gmailMessageId);
            return Optional.empty();
        }

        // 5. Construction Receipt
        Receipt receipt = Receipt.builder()
                .gmailMessageId(gmailMessageId)
                .date(extraction.getDate())
                .magasin(extraction.getMagasin().trim())
                .total(extraction.getTotal())
                .categorie(extraction.getCategorie())
                .devise(Optional.ofNullable(extraction.getDevise()).orElse("EUR"))
                .rawText(rawText)
                .build();

        // 6. Ajout des articles
        if (extraction.getItems() != null) {
            for (ItemExtraction item : extraction.getItems()) {
                ReceiptItem entity = ReceiptItem.builder()
                        .nomProduit(item.getNomProduit())
                        .prixUnitaire(item.getPrixUnitaire())
                        .quantite(Optional.ofNullable(item.getQuantite()).orElse(BigDecimal.ONE))
                        .poidsKg(item.getPoidsKg())
                        .prixAuKilo(item.getPrixAuKilo())
                        .unite(item.getUnite())
                        .build();
                receipt.addItem(entity);
            }
        }

        Receipt saved = receiptRepository.save(receipt);
        log.info("Ticket importé : {} — {} — {} items",
                saved.getMagasin(), saved.getDate(), saved.getItems().size());

        // 7. Historique des prix
        if (extraction.getItems() != null) {
            for (ItemExtraction item : extraction.getItems()) {
                if (item.getPrixUnitaire() == null) continue;
                recordPriceHistory(item, saved);
            }
        }

        return Optional.of(saved);
    }

    private void recordPriceHistory(ItemExtraction item, Receipt receipt) {
        String produit = item.getNomProduit();
        String magasin = receipt.getMagasin();

        Optional<PriceHistory> previous = priceHistoryRepository
                .findTopByNomProduitAndMagasinOrderByDateDesc(produit, magasin);

        BigDecimal variationPrix = null;
        BigDecimal variationKilo = null;

        if (previous.isPresent()) {
            PriceHistory prev = previous.get();
            variationPrix = computeVariation(prev.getPrix(), item.getPrixUnitaire());
            if (prev.getPrixAuKilo() != null && item.getPrixAuKilo() != null) {
                variationKilo = computeVariation(prev.getPrixAuKilo(), item.getPrixAuKilo());
            }
            logAlertIfNeeded(produit, magasin, variationPrix, variationKilo);
        }

        PriceHistory history = PriceHistory.builder()
                .nomProduit(produit)
                .magasin(magasin)
                .date(receipt.getDate())
                .prix(item.getPrixUnitaire())
                .prixAuKilo(item.getPrixAuKilo())
                .poidsKg(item.getPoidsKg())
                .variationPrixPct(variationPrix)
                .variationKiloPct(variationKilo)
                .receipt(receipt)
                .build();

        priceHistoryRepository.save(history);
    }

    private BigDecimal computeVariation(BigDecimal ancien, BigDecimal nouveau) {
        if (ancien == null || ancien.compareTo(BigDecimal.ZERO) == 0) return null;
        return nouveau.subtract(ancien)
                .divide(ancien, new MathContext(6, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void logAlertIfNeeded(String produit, String magasin,
                                   BigDecimal varPrix, BigDecimal varKilo) {
        if (varPrix != null && varPrix.doubleValue() >= alertThresholdPercent) {
            log.warn("ALERTE PRIX : {} chez {} — hausse de {}%", produit, magasin, varPrix);
        }
        if (varKilo != null && varKilo.doubleValue() >= alertThresholdPercent) {
            log.warn("ALERTE PRIX/KG : {} chez {} — hausse de {}%", produit, magasin, varKilo);
        }
    }
    
    public boolean isRateLimited() {
        return geminiService.isRateLimited();
    }
}
