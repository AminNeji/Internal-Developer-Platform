import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, interval, of, switchMap, takeWhile } from 'rxjs';
import { StatusBadgeComponent } from '../../components/status-badge/status-badge.component';
import { ApplicationStatus, Environment } from '../../core/environment.model';
import { EnvironmentService } from '../../core/environment.service';

const UNKNOWN_STATUS: ApplicationStatus = {
  syncStatus: 'Unknown',
  healthStatus: 'Unknown',
  url: null,
};

// Healthy and Degraded are where ArgoCD settles; anything else means it is still working
const isSettled = (status: ApplicationStatus) =>
  status.healthStatus === 'Healthy' || status.healthStatus === 'Degraded';

@Component({
  selector: 'app-environment-detail',
  imports: [RouterLink, StatusBadgeComponent],
  templateUrl: './environment-detail.component.html',
  styleUrl: './environment-detail.component.scss',
})
export class EnvironmentDetailComponent implements OnInit {
  private readonly api = inject(EnvironmentService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly envId = signal('');
  readonly env = signal<Environment | null>(null);
  readonly argo = signal<ApplicationStatus>(UNKNOWN_STATUS);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly confirmText = signal('');
  readonly destroying = signal(false);
  readonly destroyError = signal<string | null>(null);

  readonly canDestroy = computed(
    () => this.confirmText().trim() === this.envId() && !this.destroying(),
  );

  ngOnInit(): void {
    const envId = this.route.snapshot.paramMap.get('envId') ?? '';
    this.envId.set(envId);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api.getByEnvId(this.envId()).subscribe({
      next: env => {
        this.env.set(env);
        this.loading.set(false);
        this.loadStatus();
      },
      error: () => {
        this.loading.set(false);
        this.error.set('No environment with this ID, or the platform API is unreachable.');
      },
    });
  }

  private loadStatus(): void {
    this.api
      .getStatus(this.envId())
      .pipe(catchError(() => of(UNKNOWN_STATUS)))
      .subscribe(status => {
        this.argo.set(status);
        if (!isSettled(status)) {
          this.pollStatus();
        }
      });
  }

  // a new environment starts out Missing or Progressing, and its URL only appears once
  // the load balancer exists, so keep polling until ArgoCD settles, then stop on its own
  private pollStatus(): void {
    interval(5000)
      .pipe(
        switchMap(() =>
          this.api.getStatus(this.envId()).pipe(catchError(() => of(UNKNOWN_STATUS))),
        ),
        takeWhile(status => !isSettled(status), true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(status => this.argo.set(status));
  }

  onConfirmInput(event: Event): void {
    this.confirmText.set((event.target as HTMLInputElement).value);
  }

  destroy(): void {
    if (!this.canDestroy()) {
      return;
    }

    this.destroying.set(true);
    this.destroyError.set(null);

    this.api.destroy(this.envId()).subscribe({
      next: () => this.router.navigate(['/environments']),
      error: () => {
        this.destroying.set(false);
        this.destroyError.set(
          'Destroy failed. The ArgoCD application may still exist - check the backend logs.',
        );
      },
    });
  }

  formatDate(iso: string | null): string {
    if (!iso) {
      return '—';
    }
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
  }
}
