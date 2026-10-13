import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Receipt {
  id: string;
  date: string;
  magasin: string;
  total: number;
  categorie: string;
  devise: string;
  items: ReceiptItem[];
}

export interface ReceiptItem {
  id: string;
  nomProduit: string;
  prixUnitaire: number;
  quantite: number;
  poidsKg: number | null;
  prixAuKilo: number | null;
  unite: string;
  totalLigne: number;
}

export interface PriceHistory {
  id: string;
  nomProduit: string;
  magasin: string;
  date: string;
  prix: number;
  prixAuKilo: number | null;
  poidsKg: number | null;
  variationPrixPct: number | null;
  variationKiloPct: number | null;
}

export interface DashboardSummary {
  totalPeriode: number;
  nombreTickets: number;
  moyenneParTicket: number;
  parMagasin: { magasin: string; total: number; nombreTickets: number }[];
  evolutionMensuelle: { mois: string; total: number }[];
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private baseUrl = 'http://localhost:8080/api';

  private options = {
    headers: new HttpHeaders({
      Authorization: 'Basic ' + btoa('admin:changeme'),
      'Content-Type': 'application/json'
    }),
    withCredentials: true
  };

  constructor(private http: HttpClient) {}

  getDashboardSummary(months = 12): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.baseUrl}/dashboard/summary`, {
      ...this.options,
      params: new HttpParams().set('months', months)
    });
  }

  getReceipts(from?: string, to?: string, magasin?: string): Observable<Receipt[]> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (magasin) params = params.set('magasin', magasin);
    return this.http.get<Receipt[]>(`${this.baseUrl}/receipts`, {
      ...this.options, params
    });
  }

  getMagasins(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/receipts/magasins`, this.options);
  }

  getPriceHistory(produit: string, magasin?: string): Observable<PriceHistory[]> {
    let params = new HttpParams().set('produit', produit);
    if (magasin) params = params.set('magasin', magasin);
    return this.http.get<PriceHistory[]>(`${this.baseUrl}/prices/history`, {
      ...this.options, params
    });
  }

  getKiloHistory(produit: string, from: string, to: string): Observable<PriceHistory[]> {
    const params = new HttpParams()
      .set('produit', produit)
      .set('from', from)
      .set('to', to);
    return this.http.get<PriceHistory[]>(`${this.baseUrl}/prices/kilo-history`, {
      ...this.options, params
    });
  }

  triggerSync(): Observable<any> {
    return this.http.post(`${this.baseUrl}/sync`, {}, this.options);
  }
}
