import { Injectable } from '@angular/core';
import { Error } from '../models/error';

declare var bootstrap: any;

@Injectable({
  providedIn: 'root'
})
export class CommonService {

  private commonModal!: any;

  hideModal(id:string) {
    const el = document.getElementById(id);
    const modal = bootstrap.Modal.getInstance(el);
    modal?.hide();
  }

  showCommonModal() {
    const el = document.getElementById('commonModal');
    this.commonModal = bootstrap.Modal.getOrCreateInstance(el, {
      backdrop: 'static',
      keyboard: false
    });
    this.commonModal.show();
  }

  hideCommonModal() {
    if (this.commonModal) {
      this.commonModal.hide();
    }
  }

  prepareError(error: Error | null | undefined, code: string, message: string) {
    if (!error) {
      error = new Error();
      error.code = code;
      error.message = message;
      return error;
    }
    return error;
  }

  generateRandomChartData(seriesName: string, n: number) {
    const series = [];

    const today = new Date();

    for (let i = 0; i < n; i++) {
      const date = new Date(today);  // clone
      date.setDate(today.getDate() + i);  // increment days

      const formattedDate = date.toLocaleDateString('en-US', {
        month: 'short',   // Jan, Feb, Mar
        day: 'numeric'    // 1, 2, 3...
      });

      const randomValue = Math.floor(Math.random() * 20) + 40;

      series.push({
        name: formattedDate,  // e.g. "Nov 30"
        value: randomValue
      });
    }

    return [
      {
        name: seriesName,
        series: series
      }
    ];
  }

  getMaxWithPadding(data: any[]): number {
    const allValues = data[0].series.map((p: any) => p.value);
    const max = Math.max(...allValues);
    return max * 1.30;  // +15% padding
  }

}
