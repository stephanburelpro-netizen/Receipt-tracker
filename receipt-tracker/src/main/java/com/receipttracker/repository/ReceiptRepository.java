package com.receipttracker.repository;

import com.receipttracker.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    boolean existsByGmailMessageId(String gmailMessageId);

    Optional<Receipt> findByGmailMessageId(String gmailMessageId);

    List<Receipt> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);

    List<Receipt> findByMagasinOrderByDateDesc(String magasin);

    @Query("SELECT DISTINCT r.magasin FROM Receipt r ORDER BY r.magasin")
    List<String> findAllMagasins();

    @Query("SELECT DISTINCT r.categorie FROM Receipt r WHERE r.categorie IS NOT NULL ORDER BY r.categorie")
    List<String> findAllCategories();

    @Query("""
        SELECT r.magasin, SUM(r.total), COUNT(r)
        FROM Receipt r
        WHERE r.date BETWEEN :from AND :to
        GROUP BY r.magasin
        ORDER BY SUM(r.total) DESC
    """)
    List<Object[]> sumByMagasinBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT FUNCTION('TO_CHAR', r.date, 'YYYY-MM') AS month, SUM(r.total)
        FROM Receipt r
        WHERE r.date >= :since
        GROUP BY FUNCTION('TO_CHAR', r.date, 'YYYY-MM')
        ORDER BY 1
    """)
    List<Object[]> monthlyTotals(@Param("since") LocalDate since);
}
