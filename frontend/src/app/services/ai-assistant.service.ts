import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from './api.service';

/** Shared state so the chat widget appears/disappears as soon as the AI settings change. */
@Injectable({
  providedIn: 'root'
})
export class AiAssistantService {

  private apiService = inject(ApiService);

  readonly available = signal(false);

  refresh() {
    this.apiService.getAiConfig().subscribe({
      next: res => this.available.set(res.body?.data?.available === true),
      error: () => this.available.set(false)
    });
  }

  setAvailable(available: boolean) {
    this.available.set(available);
  }

}
