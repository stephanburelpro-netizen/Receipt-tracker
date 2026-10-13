package com.receipttracker.repository;

import com.receipttracker.entity.ReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReceiptItemRepository extends JpaRepository<ReceiptItem, UUID> {

    List<ReceiptItem> findByReceiptId(UUID receiptId);

    /** Articles avec un prix au kilo disponible */
    @Query("SELECT ri FROM ReceiptItem ri WHERE ri.prixAuKilo IS NOT NULL ORDER BY ri.nomProduit")
    List<ReceiptItem> findAllWithKiloPrice();

    /** Recherche par nom de produit (insensible à la casse) */
    @Query("SELECT ri FROM ReceiptItem ri WHERE LOWER(ri.nomProduit) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<ReceiptItem> searchByNomProduit(@Param("search") String search);

    /** Top produits les plus achetés */
    @Query("""
        SELECT ri.nomProduit, COUNT(ri), SUM(ri.prixUnitaire * ri.quantite)
        FROM ReceiptItem ri
        GROUP BY ri.nomProduit
        ORDER BY COUNT(ri) DESC
    """)
    List<Object[]> topProductsByFrequency();
}
