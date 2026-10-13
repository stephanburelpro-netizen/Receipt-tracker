package com.receipttracker.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "gmail_message_id", nullable = false, unique = true)
    private String gmailMessageId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String magasin;

    @Column(precision = 10, scale = 2)
    private BigDecimal total;

    private String categorie;

    @Column(length = 3)
    @Builder.Default
    private String devise = "EUR";

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReceiptItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addItem(ReceiptItem item) {
        items.add(item);
        item.setReceipt(this);
    }
}
