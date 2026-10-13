package com.receipttracker.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Appel à l'API Gemini (Google) pour extraire les données d'un ticket de caisse.
 * Tier gratuit : 1500 requêtes/jour avec gemini-1.5-flash.
 */
@Service
@Slf4j
public class GeminiService {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.8-flash}")
    private String model;

    @Value("${gemini.max-tokens:2048}")
    private int maxTokens;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
    	    .connectTimeout(30, TimeUnit.SECONDS)
    	    .readTimeout(60, TimeUnit.SECONDS)
    	    .writeTimeout(30, TimeUnit.SECONDS)
    	    .build();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private static final MediaType JSON_MEDIA = MediaType.get("application/json");

    private String getApiUrl() {
        return "https://generativelanguage.googleapis.com/v1/models/"
                + model + ":generateContent?key=" + apiKey;
    }

    private static final String PROMPT_TEMPLATE = """
        Tu es un expert en analyse de tickets de caisse.
        À partir du texte brut d'un ticket, extrais les informations structurées.

        RÈGLES IMPORTANTES :
        - Réponds UNIQUEMENT en JSON valide, sans texte autour, sans balises markdown, sans ```json.
        - Pour le prix au kilo (prixAuKilo) : ne renseigne ce champ QUE si le ticket affiche
          explicitement un prix euros/kg ou euros/100g ou euros/litre. Si absent, mets null.
        - Pour le poids (poidsKg) : convertis toujours en kg (ex: 850g = 0.850, 1.2kg = 1.2).
        - Pour la date : format ISO 8601 (YYYY-MM-DD).
        - Pour les montants : nombres décimaux avec point (ex: 12.50), pas de symbole euros.
        - Si une info est inconnue ou absente du ticket, mets null.
        - categorie : déduis-la parmi : ALIMENTATION, HYGIENE, ENTRETIEN, HABILLEMENT,
          ELECTRONIQUE, RESTAURANT, CARBURANT, AUTRE

        Structure JSON attendue :
        {
          "date": "YYYY-MM-DD",
          "magasin": "Nom du magasin",
          "total": 42.50,
          "categorie": "ALIMENTATION",
          "devise": "EUR",
          "items": [
            {
              "nomProduit": "Nom du produit",
              "prixUnitaire": 3.20,
              "quantite": 1.0,
              "poidsKg": null,
              "prixAuKilo": null,
              "unite": "pièce"
            },
            {
              "nomProduit": "Boeuf haché 15%",
              "prixUnitaire": 4.29,
              "quantite": 1.0,
              "poidsKg": 0.330,
              "prixAuKilo": 13.00,
              "unite": "kg"
            }
          ]
        }

        Ticket à analyser :
        ---
        %s
        ---
        """;
    
    private volatile boolean rateLimited = false;

    public boolean isRateLimited() {
        return rateLimited;
    }

    public void resetRateLimit() {
        rateLimited = false;
    }

    /**
     * Analyse le texte d'un ticket et retourne les données structurées.
     */
    public ReceiptExtraction extractReceiptData(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            log.warn("Texte vide, extraction annulée");
            return null;
        }

        try {
            String prompt = PROMPT_TEMPLATE.replace("%s", rawText);

            // Corps de la requête Gemini
            Map<String, Object> body = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(
                        Map.of("text", prompt)
                    ))
                ),
                "generationConfig", Map.of(
                    "maxOutputTokens", maxTokens,
                    "temperature", 0.1   // faible température pour des réponses stables
                )
            );

            String bodyJson = mapper.writeValueAsString(body);

            String url = getApiUrl();
            log.debug("Appel Gemini URL: {}", url);
            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(bodyJson, JSON_MEDIA))
                .addHeader("Content-Type", "application/json")
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
            	 String responseBody = response.body() != null ? response.body().string() : "vide";
            	    if (!response.isSuccessful()) {
            	    	if (response.code() == 429) {
            	            log.warn("Rate limit Gemini 429 — arrêt demandé");
            	            rateLimited = true;
            	        }
            	        log.error("Erreur API Gemini: {} {} — body: {}", response.code(), response.message(), responseBody);
            	        return null;
            	    }
            	    // ... reste du code en utilisant responseBody au lieu de response.body().string()
            	    Map<?, ?> responseMap = mapper.readValue(responseBody, Map.class);
                List<?> candidates = (List<?>) responseMap.get("candidates");
                if (candidates == null || candidates.isEmpty()) {
                    log.error("Aucun candidat dans la réponse Gemini");
                    return null;
                }

                Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
                Map<?, ?> content = (Map<?, ?>) candidate.get("content");
                List<?> parts = (List<?>) content.get("parts");
                String json = (String) ((Map<?, ?>) parts.get(0)).get("text");

                // Nettoyage au cas où Gemini ajoute des backticks malgré la consigne
                json = json.trim()
                           .replaceAll("^```json\\s*", "")
                           .replaceAll("^```\\s*", "")
                           .replaceAll("\\s*```$", "")
                           .trim();

                log.debug("Gemini response: {}", json);
                return mapper.readValue(json, ReceiptExtraction.class);
            }

        } catch (Exception e) {
            log.error("Erreur appel Gemini API: {}", e.getMessage(), e);
            return null;
        }
    }

    // ─── Modèles de données retournés par Gemini ─────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReceiptExtraction {
        private LocalDate date;
        private String magasin;
        private BigDecimal total;
        private String categorie;
        private String devise = "EUR";
        private List<ItemExtraction> items;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemExtraction {
        private String nomProduit;
        private BigDecimal prixUnitaire;
        private BigDecimal quantite;
        private BigDecimal poidsKg;
        private BigDecimal prixAuKilo;
        private String unite;
    }
}
