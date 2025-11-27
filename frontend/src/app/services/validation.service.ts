import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ValidationService {

   validateRequiredFields(model: any, requiredFields: string[]): string[] {
      const errors: string[] = [];

      requiredFields.forEach(field => {
        if (!model[field] || model[field].toString().trim() === '') {
          errors.push(`${this.formatFieldName(field)} is required`);
        }
      });

      return errors;
   }

   private formatFieldName(field: string): string {
      return field.charAt(0).toUpperCase() + field.slice(1);
   }


}
