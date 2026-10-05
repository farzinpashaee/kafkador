import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, NavigationStart, Router, RouterModule, RouterOutlet } from '@angular/router';
import { CommonService } from './services/common.service';

// Longer than Bootstrap's modal fade-out (300ms), so modals closed at navigation start have finished hiding.
const MODAL_CLEANUP_DELAY_MS = 500;

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'Kafkador';
  baseUrl = 'http://localhost:4200';

  constructor() {
    const commonService = inject(CommonService);
    const destroyRef = inject(DestroyRef);
    let cleanupTimer: ReturnType<typeof setTimeout> | undefined;

    // A modal open while the route changes must not leave its backdrop behind, or the whole UI stops
    // responding to clicks until the page is reloaded.
    inject(Router).events.pipe(takeUntilDestroyed(destroyRef)).subscribe(event => {
      if (event instanceof NavigationStart) {
        commonService.closeOpenModals();
      } else if (event instanceof NavigationEnd) {
        clearTimeout(cleanupTimer);
        cleanupTimer = setTimeout(() => commonService.removeOrphanModalBackdrops(), MODAL_CLEANUP_DELAY_MS);
      }
    });
    destroyRef.onDestroy(() => clearTimeout(cleanupTimer));
  }

}
