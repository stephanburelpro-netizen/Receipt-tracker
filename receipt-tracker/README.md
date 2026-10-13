# Receipt Tracker — Backend Spring Boot

Suivi automatique des tickets de caisse via Gmail + Claude AI.

## Stack
- **Java 21** + **Spring Boot 3.3**
- **PostgreSQL** (Flyway migrations)
- **Gmail API** (OAuth 2.0, lecture seule)
- **Apache PDFBox** (extraction texte PDF)
- **Anthropic SDK** (Claude Sonnet — extraction structurée)
- **Angular 18** (frontend — repo séparé)

---

## Démarrage rapide

### 1. PostgreSQL

```bash
docker run -d \
  --name receipt-db \
  -e POSTGRES_DB=receipt_tracker \
  -e POSTGRES_USER=receipt_user \
  -e POSTGRES_PASSWORD=receipt_pass \
  -p 5432:5432 \
  postgres:16
```

### 2. Credentials Google (Gmail API)

1. Va sur [Google Cloud Console](https://console.cloud.google.com)
2. Crée un projet ou sélectionne-en un
3. Active l'API **Gmail API**
4. Crée des identifiants OAuth 2.0 → **Application de bureau**
5. Télécharge le JSON → renomme-le `credentials.json`
6. Place `credentials.json` dans `src/main/resources/`

> Au premier lancement, un navigateur s'ouvre pour autoriser l'accès à ton compte Gmail.
> Le token est ensuite stocké dans le dossier `tokens/` (non commité).

### 3. Variables d'environnement

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export DB_USERNAME=receipt_user
export DB_PASSWORD=receipt_pass
# Optionnel — chemin custom vers credentials.json
# export GMAIL_CREDENTIALS_FILE=/chemin/vers/credentials.json
```

### 4. Lancement

```bash
./mvnw spring-boot:run
```

L'application démarre sur `http://localhost:8080`.
La synchronisation Gmail tourne automatiquement toutes les heures.

---

## API REST

### Tickets

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `GET` | `/api/receipts` | Liste tous les tickets |
| `GET` | `/api/receipts?from=2024-01-01&to=2024-12-31` | Filtre par période |
| `GET` | `/api/receipts?magasin=Lidl` | Filtre par magasin |
| `GET` | `/api/receipts/{id}` | Détail d'un ticket |
| `GET` | `/api/receipts/magasins` | Liste des magasins |
| `GET` | `/api/receipts/categories` | Liste des catégories |

### Prix

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `GET` | `/api/prices/history?produit=Bœuf haché` | Historique prix d'un produit |
| `GET` | `/api/prices/history?produit=...&magasin=Lidl` | Filtré par magasin |
| `GET` | `/api/prices/kilo-history?produit=...&from=...&to=...` | Historique €/kg |
| `GET` | `/api/prices/alerts?threshold=10&daysSince=30` | Alertes hausses prix |
| `GET` | `/api/prices/kilo-alerts?threshold=10` | Alertes hausses €/kg |

### Dashboard

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `GET` | `/api/dashboard/summary?months=12` | Résumé global + répartition |

### Synchronisation

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| `POST` | `/api/sync` | Déclenche une synchro manuelle |

---

## Authentification API

Basic Auth (configurable via `API_USER` / `API_PASSWORD`) :
```
admin / changeme  (par défaut — à changer en prod !)
```

---

## Personnaliser le filtre Gmail

Dans `GmailService.java`, modifie `RECEIPT_QUERY` :
```java
private static final String RECEIPT_QUERY =
    "has:attachment filename:pdf (ticket OR reçu OR facture OR receipt)";
```

Tu peux cibler des expéditeurs précis :
```java
"from:noreply@lidl.fr OR from:facture@amazon.fr has:attachment filename:pdf"
```

---

## Structure du projet

```
src/main/java/com/receipttracker/
├── config/
│   ├── GmailConfig.java          # Bean Gmail OAuth2
│   └── SecurityConfig.java       # CORS + Basic Auth
├── controller/
│   └── Controllers.java          # ReceiptController, PriceHistoryController,
│                                 # DashboardController, SyncController
├── entity/
│   ├── Receipt.java
│   ├── ReceiptItem.java
│   └── PriceHistory.java
├── repository/
│   ├── ReceiptRepository.java
│   ├── ReceiptItemRepository.java
│   └── PriceHistoryRepository.java
├── service/
│   ├── GmailService.java          # Accès Gmail API
│   ├── PdfExtractorService.java   # Extraction texte PDFBox
│   ├── AnthropicService.java      # Appel Claude API
│   └── ReceiptService.java        # Pipeline d'import
├── scheduler/
│   └── SyncScheduler.java         # @Scheduled Gmail polling
└── ReceiptTrackerApplication.java
```

---

## Prochaine étape

Frontend Angular — voir le repo `receipt-tracker-frontend`.
```
ng new receipt-tracker-frontend --standalone --routing --style=scss
```
