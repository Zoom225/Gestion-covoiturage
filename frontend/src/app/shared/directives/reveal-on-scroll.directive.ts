import { afterNextRender, DestroyRef, Directive, ElementRef, inject } from '@angular/core';

@Directive({ selector: '[appReveal]', standalone: true })
export class RevealOnScrollDirective {
  constructor() {
    const element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      if (typeof IntersectionObserver === 'undefined' || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
      const observer = new IntersectionObserver(entries => {
        if (entries.some(entry => entry.isIntersecting)) {
          element.classList.add('ui-entered');
          observer.disconnect();
        }
      }, { threshold: .12 });
      observer.observe(element);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }
}
