package com.receipttracker.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "price_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nom_produit", nullable = false)
    private String nomProduit;

    @Column(nullable = false)
    private String magasin;

    @Column(nullable = false)
    private LocalDate date;

    /** Prix unitaire — toujours présent */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal prix;

    /** Prix au kilo — nullable */
    @Column(name = "prix_au_kilo", precision = 10, scale = 2)
    private BigDecimal prixAuKilo;

    @Column(name = "poids_kg", precision = 8, scale = 3)
    private BigDecimal poidsKg;

    /**
     * Variation du prix unitaire par rapport au relevé précédent (même produit + magasin).
     * Positive = hausse, négative = baisse. Null si premier relevé.
     */
    @Column(name = "variation_prix_pct", precision = 6, scale = 2)
    private BigDecimal variationPrixPct;

    /**
     * Variation du prix au kilo par rapport au relevé précédent.
     * Null si premier relevé ou si prixAuKilo non disponible.
     */
    @Column(name = "variation_kilo_pct", precision = 6, scale = 2)
    private BigDecimal variationKiloPct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
