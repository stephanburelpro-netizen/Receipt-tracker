package com.receipttracker.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Accès à Gmail : recherche les mails contenant des PDF,
 * télécharge les pièces jointes.
 */
@Service
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class GmailService {

    private final Gmail gmail;
    private static final String USER = "me";

    // Requête Gmail pour trouver les tickets de caisse PDF
    // Adapte le filtre selon tes expéditeurs (ex: noreply@lidl.fr, facture@amazon.fr…)
    private static final String RECEIPT_QUERY =
    		"has:attachment filename:pdf (from:magasins-u.fr OR from:o-tera.fr OR from:o-tera.com OR cooperative-u.fr)";
    /**
     * Retourne les IDs de tous les messages correspondant au filtre.
     */
    public List<String> listReceiptMessageIds() throws IOException {
        List<String> ids = new ArrayList<>();
        String pageToken = null;

        do {
            ListMessagesResponse response = gmail.users().messages()
                    .list(USER)
                    .setQ(RECEIPT_QUERY)
                    .setPageToken(pageToken)
                    .setMaxResults(10L)
                    .execute();

            if (response.getMessages() != null) {
                response.getMessages().forEach(m -> ids.add(m.getId()));
            }
            pageToken = response.getNextPageToken();
        } while (pageToken != null);

        log.info("Gmail: {} messages trouvés avec le filtre '{}'", ids.size(), RECEIPT_QUERY);
        return ids;
    }

    /**
     * Télécharge le contenu complet d'un message.
     */
    public Message getMessage(String messageId) throws IOException {
        return gmail.users().messages()
                .get(USER, messageId)
                .setFormat("full")
                .execute();
    }

    /**
     * Extrait les PDF en pièce jointe d'un message.
     * Retourne une liste de (nomFichier, contenuBytes).
     */
    public List<PdfAttachment> getPdfAttachments(Message message) throws IOException {
        List<PdfAttachment> attachments = new ArrayList<>();
        extractParts(message.getId(), message.getPayload(), attachments);
        return attachments;
    }

    private void extractParts(String messageId, MessagePart part, List<PdfAttachment> out)
            throws IOException {
        if (part == null) return;

        // Vérifie si cette partie est un PDF
        if (isPdfPart(part)) {
            byte[] data = downloadAttachment(messageId, part);
            if (data != null) {
                String filename = Optional.ofNullable(part.getFilename())
                        .filter(f -> !f.isBlank())
                        .orElse("ticket_" + messageId + ".pdf");
                out.add(new PdfAttachment(filename, data));
                log.debug("PDF trouvé: {} ({} bytes)", filename, data.length);
            }
        }

        // Récursion sur les parties imbriquées (multipart/mixed, etc.)
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                extractParts(messageId, subPart, out);
            }
        }
    }

    private boolean isPdfPart(MessagePart part) {
        String mimeType = part.getMimeType();
        String filename = part.getFilename();
        return "application/pdf".equalsIgnoreCase(mimeType)
            || (filename != null && filename.toLowerCase().endsWith(".pdf"));
    }

    private byte[] downloadAttachment(String messageId, MessagePart part) throws IOException {
        // Le contenu peut être inline ou référencé par attachmentId
        if (part.getBody().getData() != null) {
            return Base64.getUrlDecoder().decode(part.getBody().getData());
        }

        String attachmentId = part.getBody().getAttachmentId();
        if (attachmentId == null) return null;

        var attachment = gmail.users().messages().attachments()
                .get(USER, messageId, attachmentId)
                .execute();
        return Base64.getUrlDecoder().decode(attachment.getData());
    }

    /** DTO interne pour un PDF téléchargé */
    public record PdfAttachment(String filename, byte[] content) {}
}
