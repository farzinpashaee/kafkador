import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, TitleStrategy } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { routes } from './app.routes';
import { LogInterceptor } from './interceptors/log-interceptor';
import { SessionInterceptor } from './interceptors/session-interceptor';
import { KafkadorTitleStrategy } from './services/kafkador-title-strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideNoopAnimations(),
    provideHttpClient(
          withInterceptors([
            LogInterceptor,
            SessionInterceptor
          ])
        ),
    { provide: TitleStrategy, useClass: KafkadorTitleStrategy }
  ]
};
