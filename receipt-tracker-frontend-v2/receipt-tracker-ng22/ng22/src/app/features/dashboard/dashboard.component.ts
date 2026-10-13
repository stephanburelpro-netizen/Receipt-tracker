import {
  Component, OnInit, OnDestroy,
  ViewChild, ElementRef
} from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  Chart,
  BarController, BarElement,
  LineController, LineElement, PointElement,
  DoughnutController, ArcElement,
  CategoryScale, LinearScale,
  Title, Tooltip, Legend, Filler
} from 'chart.js';
import { ApiService, DashboardSummary, PriceHistory } from '../../core/services/api.service';

Chart.register(
  BarController, BarElement,
  LineController, LineElement, PointElement,
  DoughnutController, ArcElement,
  CategoryScale, LinearScale,
  Title, Tooltip, Legend, Filler
);

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, DecimalPipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit, OnDestroy {

  @ViewChild('monthlyCanvas') monthlyCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('magasinCanvas') magasinCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('priceCanvas')   priceCanvas!: ElementRef<HTMLCanvasElement>;

  summary: DashboardSummary | null = null;
  loading = true;
  syncLoading = false;
  selectedMonths = 12;

  searchProduit = '';
  priceHistory: PriceHistory[] = [];
  searchLoading = false;
  showPriceChart = false;

  private monthlyChart?: Chart;
  private magasinChart?: Chart;
  private priceChart?: Chart;

  private readonly COLORS = [
    '#6366f1','#f59e0b','#10b981','#ef4444',
    '#3b82f6','#8b5cf6','#ec4899','#14b8a6'
  ];

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadDashboard();
  }

  ngOnDestroy(): void {
    this.monthlyChart?.destroy();
    this.magasinChart?.destroy();
    this.priceChart?.destroy();
  }

  loadDashboard(): void {
    this.loading = true;
    this.api.getDashboardSummary(this.selectedMonths).subscribe({
      next: (data) => {
        this.summary = data;
        this.loading = false;
        setTimeout(() => {
          this.buildMonthlyChart(data);
          this.buildMagasinChart(data);
        }, 50);
      },
      error: (err) => {
        console.error('Erreur dashboard:', err);
        this.loading = false;
      }
    });
  }

  private buildMonthlyChart(data: DashboardSummary): void {
    this.monthlyChart?.destroy();
    const ctx = this.monthlyCanvas?.nativeElement?.getContext('2d');
    if (!ctx) return;

    this.monthlyChart = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: data.evolutionMensuelle.map(m => m.mois),
        datasets: [{
          data: data.evolutionMensuelle.map(m => m.total),
          backgroundColor: '#6366f1',
          borderRadius: 6
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: {
            beginAtZero: true,
            ticks: { callback: (v) => v + ' €' }
          }
        }
      }
    });
  }

  private buildMagasinChart(data: DashboardSummary): void {
    this.magasinChart?.destroy();
    const ctx = this.magasinCanvas?.nativeElement?.getContext('2d');
    if (!ctx) return;

    this.magasinChart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: data.parMagasin.map(m => m.magasin),
        datasets: [{
          data: data.parMagasin.map(m => m.total),
          backgroundColor: this.COLORS.slice(0, data.parMagasin.length)
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'right' } }
      }
    });
  }

  searchPriceHistory(): void {
    if (!this.searchProduit.trim()) return;
    this.searchLoading = true;
    this.showPriceChart = false;
    this.priceHistory = [];

    this.api.getPriceHistory(this.searchProduit).subscribe({
      next: (history) => {
        this.priceHistory = history;
        this.searchLoading = false;
        if (history.length > 0) {
          this.showPriceChart = true;
          setTimeout(() => this.buildPriceChart(history), 50);
        }
      },
      error: () => { this.searchLoading = false; }
    });
  }

  private buildPriceChart(history: PriceHistory[]): void {
    this.priceChart?.destroy();
    const ctx = this.priceCanvas?.nativeElement?.getContext('2d');
    if (!ctx) return;

    const sorted = [...history].sort((a, b) => a.date.localeCompare(b.date));
    const hasKilo = sorted.some(h => h.prixAuKilo !== null);

    const datasets: any[] = [{
      label: 'Prix unitaire (€)',
      data: sorted.map(h => h.prix),
      borderColor: '#6366f1',
      backgroundColor: 'rgba(99,102,241,0.1)',
      tension: 0.3,
      fill: true,
      pointRadius: 4
    }];

    if (hasKilo) {
      datasets.push({
        label: 'Prix au kilo (€/kg)',
        data: sorted.map(h => h.prixAuKilo),
        borderColor: '#f59e0b',
        backgroundColor: 'rgba(245,158,11,0.1)',
        tension: 0.3,
        fill: true,
        pointRadius: 4
      });
    }

    this.priceChart = new Chart(ctx, {
      type: 'line',
      data: { labels: sorted.map(h => h.date), datasets },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'top' } },
        scales: { y: { ticks: { callback: (v) => v + ' €' } } }
      }
    });
  }

  triggerSync(): void {
    this.syncLoading = true;
    this.api.triggerSync().subscribe({
      next: (result) => {
        this.syncLoading = false;
        alert(`Synchronisation terminée : ${result.imported} tickets importés`);
        this.loadDashboard();
      },
      error: () => {
        this.syncLoading = false;
        alert('Erreur lors de la synchronisation');
      }
    });
  }

  formatCurrency(val: number | undefined | null): string {
    if (val == null) return '—';
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency', currency: 'EUR'
    }).format(val);
  }

  variationClass(val: number | null): string {
    if (val === null) return 'badge badge--neutral';
    return val >= 0 ? 'badge badge--up' : 'badge badge--down';
  }
}
