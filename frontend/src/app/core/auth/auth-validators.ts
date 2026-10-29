import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const notBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length ? null : { required: true };

export const passwordBytes: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && new TextEncoder().encode(control.value).length > 72
    ? { passwordBytes: true }
    : null;

export const invitationPassword: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null =>
  typeof control.value === 'string' && Array.from(control.value).length < 15
    ? { minCharacters: true }
    : null;

export const passwordsMatch: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.get('password')?.value === control.get('confirmation')?.value ? null : { mismatch: true };
