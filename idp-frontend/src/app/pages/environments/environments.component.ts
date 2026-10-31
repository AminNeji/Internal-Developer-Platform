import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { StatusBadgeComponent } from '../../components/status-badge/status-badge.component';
import { ApplicationStatus, Environment } from '../../core/environment.model';
import { EnvironmentService } from '../../core/environment.service';

interface EnvironmentRow extends Environment {
  argo: ApplicationStatus;
}

const UNKNOWN_STATUS: ApplicationStatus = {
  syncStatus: 'Unknown',
  healthStatus: 'Unknown',
  url: null,
};

@Component({
  selector: 'app-environments',
  imports: [RouterLink, StatusBadgeComponent],
  templateUrl: './environments.component.html',
  styleUrl: './environments.component.scss',
})
export class EnvironmentsComponent implements OnInit {
  private readonly api = inject(EnvironmentService);
  private readonly router = inject(Router);

  readonly rows = signal<EnvironmentRow[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api
      .listAll()
      .pipe(
        switchMap(envs =>
          envs.length === 0
            ? of<EnvironmentRow[]>([])
            : forkJoin(
                envs.map(env =>
                  this.api.getStatus(env.envId).pipe(
                    // the /status endpoint does not exist yet, so a failure here is expected
                    catchError(() => of(UNKNOWN_STATUS)),
                    map(argo => ({ ...env, argo })),
                  ),
                ),
              ),
        ),
      )
      .subscribe({
        next: rows => {
          this.rows.set(rows);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Could not reach the platform API. Is the backend running on port 8082?');
          this.loading.set(false);
        },
      });
  }

  open(envId: string): void {
    this.router.navigate(['/environments', envId]);
  }

  expiresIn(iso: string | null): string {
    if (!iso) {
      return 'never';
    }

    const remaining = new Date(iso).getTime() - Date.now();
    if (Number.isNaN(remaining)) {
      return '—';
    }
    // the cleanup job runs every 5 minutes, so an environment can sit here briefly past its time
    if (remaining <= 0) {
      return 'expired';
    }

    const minutes = Math.ceil(remaining / 60_000);
    if (minutes < 60) {
      return `in ${minutes} minute${minutes === 1 ? '' : 's'}`;
    }

    const hours = Math.round(minutes / 60);
    return `in ${hours} hour${hours === 1 ? '' : 's'}`;
  }

  relative(iso: string | null): string {
    if (!iso) {
      return '—';
    }

    const elapsed = Date.now() - new Date(iso).getTime();
    if (Number.isNaN(elapsed)) {
      return '—';
    }

    const minutes = Math.floor(elapsed / 60_000);
    if (minutes < 1) {
      return 'just now';
    }
    if (minutes < 60) {
      return `${minutes} minute${minutes === 1 ? '' : 's'} ago`;
    }

    const hours = Math.floor(minutes / 60);
    if (hours < 24) {
      return `${hours} hour${hours === 1 ? '' : 's'} ago`;
    }

    const days = Math.floor(hours / 24);
    return `${days} day${days === 1 ? '' : 's'} ago`;
  }
}
