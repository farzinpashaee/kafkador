import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class LocalStorageService {

  constructor() { }

  setItem(key: string, value: any): void {
      localStorage.setItem(key, JSON.stringify(value));
    }

    getItem<T>(key: string): T | null {
      const value = localStorage.getItem(key);
      return value ? JSON.parse(value) as T : null;
    }

    removeItem(key: string): void {
      localStorage.removeItem(key);
    }

    clear(): void {
      localStorage.clear();
    }
}
