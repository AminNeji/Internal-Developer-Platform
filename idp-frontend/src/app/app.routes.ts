import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'environments', pathMatch: 'full' },
  {
    path: 'environments',
    loadComponent: () =>
      import('./pages/environments/environments.component')
        .then(m => m.EnvironmentsComponent),
  },
  {
    path: 'environments/new',
    loadComponent: () =>
      import('./pages/environment-create/environment-create.component')
        .then(m => m.EnvironmentCreateComponent),
  },
  {
    path: 'environments/:envId',
    loadComponent: () =>
      import('./pages/environment-detail/environment-detail.component')
        .then(m => m.EnvironmentDetailComponent),
  },
];
