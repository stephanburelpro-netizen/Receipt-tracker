package com.receipttracker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// ─── Receipt ────────────────────────────────────────────────────────────────

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class ReceiptDto {
    UUID id;
    LocalDate date;
    String magasin;
    BigDecimal total;
    String categorie;
    String devise;
    List<ReceiptItemDto> items;
    LocalDateTime createdAt;
}

// ─── ReceiptItem ─────────────────────────────────────────────────────────────

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class ReceiptItemDto {
    UUID id;
    String nomProduit;
    BigDecimal prixUnitaire;
    BigDecimal quantite;
    BigDecimal poidsKg;
    /** Null si non disponible sur le ticket */
    BigDecimal prixAuKilo;
    String unite;
    BigDecimal totalLigne;
}

// ─── PriceHistory ────────────────────────────────────────────────────────────

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class PriceHistoryDto {
    UUID id;
    String nomProduit;
    String magasin;
    LocalDate date;
    BigDecimal prix;
    /** Null si non disponible */
    BigDecimal prixAuKilo;
    BigDecimal poidsKg;
    BigDecimal variationPrixPct;
    BigDecimal variationKiloPct;
}

// ─── Dashboard summary ───────────────────────────────────────────────────────

@Data
@Builder
class DashboardSummaryDto {
    BigDecimal totalPeriode;
    long nombreTickets;
    BigDecimal moyenneParTicket;
    List<MagasinSummaryDto> parMagasin;
    List<MonthlyTotalDto> evolutionMensuelle;
}

@Data
@Builder
class MagasinSummaryDto {
    String magasin;
    BigDecimal total;
    long nombreTickets;
}

@Data
@Builder
class MonthlyTotalDto {
    String mois;   // "2024-03"
    BigDecimal total;
}

// ─── Price alert ─────────────────────────────────────────────────────────────

@Data
@Builder
class PriceAlertDto {
    String nomProduit;
    String magasin;
    LocalDate date;
    BigDecimal ancienPrix;
    BigDecimal nouveauPrix;
    BigDecimal variationPct;
    /** true = alerte sur le prix/kg, false = alerte sur le prix unitaire */
    boolean surPrixAuKilo;
}

// ─── Sync result ─────────────────────────────────────────────────────────────

@Data
@Builder
class SyncResultDto {
    int mailsTraites;
    int ticketsImportes;
    int ticketsIgnores;   // déjà en BDD
    int erreurs;
    LocalDateTime syncedAt;
}
