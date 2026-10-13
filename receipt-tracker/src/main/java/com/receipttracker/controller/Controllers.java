package com.receipttracker.controller;

import com.receipttracker.entity.PriceHistory;
import com.receipttracker.entity.Receipt;
import com.receipttracker.repository.PriceHistoryRepository;
import com.receipttracker.repository.ReceiptRepository;
import com.receipttracker.scheduler.SyncScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ═══════════════════════════════════════════════════════════════════════════
// ReceiptController
// ═══════════════════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
class ReceiptController {

    private final ReceiptRepository receiptRepo;

    /** Liste tous les tickets, filtre optionnel par période et magasin */
    @GetMapping
    public List<Receipt> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String magasin) {

        if (from != null && to != null && magasin != null) {
            return receiptRepo.findByDateBetweenOrderByDateDesc(from, to)
                    .stream().filter(r -> r.getMagasin().equalsIgnoreCase(magasin)).toList();
        }
        if (from != null && to != null) {
            return receiptRepo.findByDateBetweenOrderByDateDesc(from, to);
        }
        if (magasin != null) {
            return receiptRepo.findByMagasinOrderByDateDesc(magasin);
        }
        return receiptRepo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Receipt> getById(@PathVariable UUID id) {
        return receiptRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/magasins")
    public List<String> listMagasins() {
        return receiptRepo.findAllMagasins();
    }

    @GetMapping("/categories")
    public List<String> listCategories() {
        return receiptRepo.findAllCategories();
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PriceHistoryController
// ═══════════════════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
class PriceHistoryController {

    private final PriceHistoryRepository priceHistoryRepo;

    /** Historique des prix pour un produit (toutes enseignes ou filtrées) */
    @GetMapping("/history")
    public List<PriceHistory> getHistory(
            @RequestParam String produit,
            @RequestParam(required = false) String magasin) {
        if (magasin != null) {
            return priceHistoryRepo.findByNomProduitAndMagasinOrderByDateDesc(produit, magasin);
        }
        return priceHistoryRepo.findByNomProduitOrderByDateDesc(produit);
    }

    /** Historique du prix au kilo pour un produit sur une période */
    @GetMapping("/kilo-history")
    public List<PriceHistory> getKiloHistory(
            @RequestParam String produit,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return priceHistoryRepo.findKiloHistoryForProduct(produit, from, to);
    }

    /** Alertes : produits dont le prix unitaire a augmenté de plus de X% */
    @GetMapping("/alerts")
    public List<PriceHistory> getPriceAlerts(
            @RequestParam(defaultValue = "10") double threshold,
            @RequestParam(defaultValue = "30") int daysSince) {
        LocalDate since = LocalDate.now().minusDays(daysSince);
        return priceHistoryRepo.findPriceAlerts(threshold, since);
    }

    /** Alertes prix au kilo spécifiquement */
    @GetMapping("/kilo-alerts")
    public List<PriceHistory> getKiloAlerts(
            @RequestParam(defaultValue = "10") double threshold,
            @RequestParam(defaultValue = "30") int daysSince) {
        LocalDate since = LocalDate.now().minusDays(daysSince);
        return priceHistoryRepo.findKiloPriceAlerts(threshold, since);
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DashboardController
// ═══════════════════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
class DashboardController {

    private final ReceiptRepository receiptRepo;

    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam(defaultValue = "12") int months) {
        LocalDate from = LocalDate.now().minusMonths(months);
        LocalDate to = LocalDate.now();

        List<Object[]> byMagasin = receiptRepo.sumByMagasinBetween(from, to);
        List<Object[]> monthly = receiptRepo.monthlyTotals(from);

        BigDecimal total = byMagasin.stream()
                .map(row -> (BigDecimal) row[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long nbTickets = byMagasin.stream()
                .mapToLong(row -> (Long) row[2])
                .sum();

        return Map.of(
            "totalPeriode", total,
            "nombreTickets", nbTickets,
            "moyenneParTicket", nbTickets > 0 ? total.divide(BigDecimal.valueOf(nbTickets), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO,
            "parMagasin", byMagasin.stream().map(row -> Map.of(
                "magasin", row[0],
                "total", row[1],
                "nombreTickets", row[2]
            )).toList(),
            "evolutionMensuelle", monthly.stream().map(row -> Map.of(
                "mois", row[0],
                "total", row[1]
            )).toList()
        );
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SyncController
// ═══════════════════════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/sync")
@Profile("!test")
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
class SyncController {

    private final SyncScheduler syncScheduler;

    /** Déclenche une synchronisation manuelle depuis le dashboard Angular */
    @PostMapping
    public Map<String, Object> triggerSync() {
        log.info("Synchronisation manuelle déclenchée via API");
        SyncScheduler.SyncResult result = syncScheduler.sync();
        return Map.of(
            "imported", result.imported(),
            "skipped", result.skipped(),
            "errors", result.errors(),
            "syncedAt", LocalDateTime.now().toString()
        );
    }
}
