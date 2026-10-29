export function safeReturnUrl(value: string | null): string {
  // Seul parcours privé de ce lot. Aucune URL externe ou route arbitraire n'est acceptée.
  return value && /^\/tableau-de-bord(?:[?#].*)?$/.test(value) ? value : '/tableau-de-bord';
}
