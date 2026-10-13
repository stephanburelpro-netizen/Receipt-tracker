-- V1__init_schema.sql
-- Schéma initial : tickets de caisse, articles, historique des prix

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- -------------------------------------------------------
-- Table principale : un ticket = une visite en magasin
-- -------------------------------------------------------
CREATE TABLE receipts (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    gmail_message_id VARCHAR(255) NOT NULL UNIQUE,
    date             DATE         NOT NULL,
    magasin          VARCHAR(255) NOT NULL,
    total            NUMERIC(10, 2),
    categorie        VARCHAR(100),
    devise           CHAR(3)      NOT NULL DEFAULT 'EUR',
    raw_text         TEXT,                     -- texte brut extrait du PDF
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------
-- Articles d'un ticket
-- -------------------------------------------------------
CREATE TABLE receipt_items (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    receipt_id     UUID           NOT NULL REFERENCES receipts (id) ON DELETE CASCADE,
    nom_produit    VARCHAR(500)   NOT NULL,
    prix_unitaire  NUMERIC(10, 2) NOT NULL,
    quantite       NUMERIC(8, 3)  NOT NULL DEFAULT 1,
    -- Champs prix au kilo (nullable — renseignés si le ticket affiche €/kg)
    poids_kg       NUMERIC(8, 3),            -- ex: 0.850 kg
    prix_au_kilo   NUMERIC(10, 2),           -- ex: 12.50 €/kg
    unite          VARCHAR(20),              -- 'kg', 'g', 'L', 'pièce', etc.
    -- Total ligne calculé
    total_ligne    NUMERIC(10, 2)
            GENERATED ALWAYS AS (prix_unitaire * quantite) STORED,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------
-- Historique des prix : snapshot à chaque passage en caisse
-- On historise prix unitaire ET prix au kilo séparément
-- -------------------------------------------------------
CREATE TABLE price_history (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nom_produit     VARCHAR(500)   NOT NULL,
    magasin         VARCHAR(255)   NOT NULL,
    date            DATE           NOT NULL,
    -- Prix unitaire (toujours présent)
    prix            NUMERIC(10, 2) NOT NULL,
    -- Prix au kilo (nullable)
    prix_au_kilo    NUMERIC(10, 2),
    poids_kg        NUMERIC(8, 3),
    -- Variation par rapport au relevé précédent pour ce produit/magasin
    variation_prix_pct       NUMERIC(6, 2),    -- variation prix unitaire
    variation_kilo_pct       NUMERIC(6, 2),    -- variation prix/kg
    -- Référence au ticket source
    receipt_id      UUID REFERENCES receipts (id) ON DELETE SET NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------
-- Index pour les requêtes fréquentes du dashboard
-- -------------------------------------------------------
CREATE INDEX idx_receipts_date        ON receipts (date DESC);
CREATE INDEX idx_receipts_magasin     ON receipts (magasin);
CREATE INDEX idx_receipts_categorie   ON receipts (categorie);

CREATE INDEX idx_items_receipt        ON receipt_items (receipt_id);
CREATE INDEX idx_items_produit        ON receipt_items (nom_produit);
CREATE INDEX idx_items_prix_kilo      ON receipt_items (prix_au_kilo) WHERE prix_au_kilo IS NOT NULL;

CREATE INDEX idx_history_produit_mag  ON price_history (nom_produit, magasin, date DESC);
CREATE INDEX idx_history_date         ON price_history (date DESC);
CREATE INDEX idx_history_kilo         ON price_history (prix_au_kilo) WHERE prix_au_kilo IS NOT NULL;
