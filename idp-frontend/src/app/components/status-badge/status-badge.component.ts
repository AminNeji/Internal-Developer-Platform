import { Component, computed, input } from '@angular/core';
import { HealthStatus, SyncStatus } from '../../core/environment.model';

type Tone = 'healthy' | 'outofsync' | 'progressing' | 'degraded' | 'unknown';

@Component({
  selector: 'app-status-badge',
  imports: [],
  templateUrl: './status-badge.component.html',
  styleUrl: './status-badge.component.scss',
})
export class StatusBadgeComponent {
  readonly status = input.required<SyncStatus | HealthStatus>();

  readonly tone = computed<Tone>(() => {
    switch (this.status()) {
      case 'Synced':
      case 'Healthy':
        return 'healthy';
      case 'OutOfSync':
        return 'outofsync';
      case 'Progressing':
        return 'progressing';
      case 'Degraded':
        return 'degraded';
      default:
        return 'unknown';
    }
  });
}
