import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { PageResult } from '../models/covoiturage.model';

@Component({
  selector: 'app-pagination',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (page(); as result) {
    <nav class="business-actions" aria-label="Pagination">
      <button
        type="button"
        class="btn btn-secondary"
        [disabled]="busy() || result.page === 0"
        (click)="change.emit(result.page - 1)"
      >
        Précédent
      </button>
      <span aria-live="polite"
        >{{ result.totalElements }} résultat(s) · Page
        {{ result.totalPages ? result.page + 1 : 0 }} / {{ result.totalPages }}</span
      >
      <button
        type="button"
        class="btn btn-secondary"
        [disabled]="busy() || result.page + 1 >= result.totalPages"
        (click)="change.emit(result.page + 1)"
      >
        Suivant
      </button>
    </nav>
  }`,
})
export class Pagination {
  readonly page = input<PageResult<unknown> | null>(null);
  readonly busy = input(false);
  readonly change = output<number>();
}
