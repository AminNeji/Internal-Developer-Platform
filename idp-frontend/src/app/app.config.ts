import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';

const IdpPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50:  '#EFF4FE',
      100: '#DCE7FD',
      200: '#BDD0FB',
      300: '#94B1F8',
      400: '#6B92F5',
      500: '#4F7DF3',
      600: '#3D6AE0',
      700: '#2F53B8',
      800: '#2A4794',
      900: '#273D75',
      950: '#1B2748',
    },
  },
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // XSRF protection is on by default: Angular copies the XSRF-TOKEN cookie into an
    // X-XSRF-TOKEN header on POST/DELETE, which is what Spring's csrf.spa() checks
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    providePrimeNG({
      theme: {
        preset: IdpPreset,
        options: {
          darkModeSelector: '.dark',
        },
      },
    }),
  ],
};
