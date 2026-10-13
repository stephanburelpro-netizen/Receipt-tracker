package com.receipttracker.scheduler;

import com.google.api.services.gmail.model.Message;
import com.receipttracker.service.GeminiService;
import com.receipttracker.service.GmailService;
import com.receipttracker.service.GmailService.PdfAttachment;
import com.receipttracker.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lance une synchronisation Gmail automatique selon le cron défini
 * dans application.yml (gmail.sync-cron).
 * Désactivé en profil "test".
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class SyncScheduler {

    private final GmailService gmailService;
    private final ReceiptService receiptService;
    private final GeminiService geminiService;

    @Scheduled(cron = "${gmail.sync-cron:0 0 * * * *}")
    public SyncResult sync() {
        log.info("Démarrage synchronisation Gmail...");
        geminiService.resetRateLimit();
        AtomicInteger imported = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        try {
            List<String> messageIds = gmailService.listReceiptMessageIds();
            Thread.sleep(500);
            log.info("{} messages à traiter", messageIds.size());

            for (String messageId : messageIds) {
                try {
                    Message message = gmailService.getMessage(messageId);
                    List<PdfAttachment> pdfs = gmailService.getPdfAttachments(message);

                    if (pdfs.isEmpty()) {
                        skipped.incrementAndGet();
                        continue;
                    }

                    // Traite le premier PDF (en général un seul ticket par mail)
                    PdfAttachment pdf = pdfs.get(0);
                    boolean[] rateLimited = {false};
                    receiptService.importReceipt(pdf.content(), messageId)
                            .ifPresentOrElse(
                                    r -> imported.incrementAndGet(),
                                    () -> {
                                        if (receiptService.isRateLimited()) {
                                            rateLimited[0] = true;
                                        } else {
                                            skipped.incrementAndGet();
                                        }
                                    }
                                );
                    if (rateLimited[0]) {
                        log.warn("Rate limit Gemini (429) détecté — arrêt de la synchronisation");
                        break;
                    }

                    Thread.sleep(500);
                } catch (Exception e) {
                    log.error("Erreur traitement messageId={}: {}", messageId, e.getMessage());
                    errors.incrementAndGet();
                }
            }

        } catch (Exception e) {
            log.error("Erreur lors de la synchronisation Gmail: {}", e.getMessage(), e);
        }

        SyncResult result = new SyncResult(imported.get(), skipped.get(), errors.get());
        log.info("Synchronisation terminée: {}", result);
        return result;
    }

    public record SyncResult(int imported, int skipped, int errors) {}
}
