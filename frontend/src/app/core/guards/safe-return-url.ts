export function safeReturnUrl(value: string | null): string {
  // Liste explicite des parcours privés ; jamais de destination externe ou arbitraire.
  return value && /^\/(?:tableau-de-bord|trajets(?:\/[1-9]\d*)?)(?:[?#].*)?$/.test(value)
    ? value
    : '/tableau-de-bord';
}
