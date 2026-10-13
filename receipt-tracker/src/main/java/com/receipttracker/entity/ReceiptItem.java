package com.receipttracker.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "receipt_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @Column(name = "nom_produit", nullable = false)
    private String nomProduit;

    @Column(name = "prix_unitaire", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixUnitaire;

    @Column(precision = 8, scale = 3)
    @Builder.Default
    private BigDecimal quantite = BigDecimal.ONE;

    /**
     * Poids en kg — renseigné si le ticket affiche un poids (ex: 0.850 kg).
     * Nullable si l'article est vendu à l'unité.
     */
    @Column(name = "poids_kg", precision = 8, scale = 3)
    private BigDecimal poidsKg;

    /**
     * Prix au kilo — nullable, renseigné uniquement si le ticket affiche €/kg.
     * Ex : bœuf haché 12,50 €/kg
     */
    @Column(name = "prix_au_kilo", precision = 10, scale = 2)
    private BigDecimal prixAuKilo;

    /**
     * Unité de mesure : 'kg', 'g', 'L', 'cl', 'pièce', etc.
     */
    @Column(length = 20)
    private String unite;

    // total_ligne est une colonne GENERATED ALWAYS en BDD — on la mappe en lecture seule
    @Column(name = "total_ligne", insertable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal totalLigne;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
