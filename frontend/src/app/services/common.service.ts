import { Injectable } from '@angular/core';

declare var bootstrap: any;

@Injectable({
  providedIn: 'root'
})
export class CommonService {

  hideModal(id:string) {
    const el = document.getElementById(id);
    const modal = bootstrap.Modal.getInstance(el);
    modal?.hide();
  }

  showCommonModal() {
    const el = document.getElementById('commonModal');
    (bootstrap.Modal.getInstance(el) || new bootstrap.Modal(el)).show();
  }

  hideCommonModal() {
    const el = document.getElementById('commonModal');
    const modal = bootstrap.Modal.getInstance(el);
    modal?.hide();
  }

}
