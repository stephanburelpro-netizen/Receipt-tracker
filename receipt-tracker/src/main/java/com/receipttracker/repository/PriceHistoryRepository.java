package com.receipttracker.repository;

import com.receipttracker.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, UUID> {

    List<PriceHistory> findByNomProduitAndMagasinOrderByDateDesc(String nomProduit, String magasin);

    List<PriceHistory> findByNomProduitOrderByDateDesc(String nomProduit);

    /** Dernier relevé connu pour ce produit dans ce magasin */
    Optional<PriceHistory> findTopByNomProduitAndMagasinOrderByDateDesc(String nomProduit, String magasin);

    /** Produits dont le prix au kilo a augmenté de plus de X% récemment */
    @Query("""
        SELECT ph FROM PriceHistory ph
        WHERE ph.variationKiloPct >= :threshold
        AND ph.date >= :since
        ORDER BY ph.variationKiloPct DESC
    """)
    List<PriceHistory> findKiloPriceAlerts(@Param("threshold") double threshold,
                                            @Param("since") LocalDate since);

    /** Produits dont le prix unitaire a augmenté de plus de X% récemment */
    @Query("""
        SELECT ph FROM PriceHistory ph
        WHERE ph.variationPrixPct >= :threshold
        AND ph.date >= :since
        ORDER BY ph.variationPrixPct DESC
    """)
    List<PriceHistory> findPriceAlerts(@Param("threshold") double threshold,
                                        @Param("since") LocalDate since);

    /** Évolution du prix/kg d'un produit sur une période */
    @Query("""
        SELECT ph FROM PriceHistory ph
        WHERE ph.nomProduit = :produit
        AND ph.prixAuKilo IS NOT NULL
        AND ph.date BETWEEN :from AND :to
        ORDER BY ph.date ASC
    """)
    List<PriceHistory> findKiloHistoryForProduct(@Param("produit") String produit,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);
}
