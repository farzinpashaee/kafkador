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

   validatePositiveIntegerFields(model: any, fields: string[]): string[] {
      const errors: string[] = [];

      fields.forEach(field => {
        const value = Number(model[field]);
        if (!Number.isInteger(value) || value < 1) {
          errors.push(`${this.formatFieldName(field)} must be a positive whole number`);
        }
      });

      return errors;
   }

   private formatFieldName(field: string): string {
      return field.charAt(0).toUpperCase() + field.slice(1);
   }


}
