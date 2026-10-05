import { Injectable } from '@angular/core';
import { Error, Config } from '../models';

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

  /**
   * Starts closing every open Bootstrap modal. Called when a navigation starts: Bootstrap puts the grey backdrop
   * directly on <body>, outside Angular, so a modal whose component is destroyed while open (browser Back, the
   * redirect to /connect on an expired session, ...) would leave a backdrop that blocks every click.
   */
  closeOpenModals() {
    document.querySelectorAll<HTMLElement>('.modal.show').forEach(el => bootstrap.Modal.getInstance(el)?.hide());
  }

  /**
   * Removes a backdrop left behind when no modal is open any more — e.g. hide() was ignored because the modal was
   * still in its opening animation when its component went away — and gives the page its scrolling back.
   */
  removeOrphanModalBackdrops() {
    if (document.querySelector('.modal.show')) return;
    document.querySelectorAll('.modal-backdrop').forEach(backdrop => backdrop.remove());
    document.body.classList.remove('modal-open');
    document.body.style.removeProperty('overflow');
    document.body.style.removeProperty('padding-right');
  }

  showTab(tabId:string) {
    const el = document.getElementById(tabId);
    if (!el) return;
    const tab = bootstrap.Tab.getOrCreateInstance(el);
    tab.show();
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
    if (!error || !error.message) {
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

  generateConfigEditHtml(config: Config){
    let form = config.name;
    return form;
  }

}
