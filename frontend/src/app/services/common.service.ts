import { Injectable } from '@angular/core';

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

}
