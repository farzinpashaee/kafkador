import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class DocumentationService {

  createDocumentationHtml(doc: string, link: string): string {
      return `
        <p class="text-s">${doc}</p>
        <a target="_blank" href="${link}">More Information</a>
      `;
    }

}
