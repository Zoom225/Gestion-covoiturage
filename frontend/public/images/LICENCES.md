# Ressources visuelles Transport

`route-partagee.jpg` : Mike van Schoonderwalt, « A Road between Two Fields ».
Source : https://www.pexels.com/photo/a-road-between-two-fields-5505757/
Licence Pexels : https://www.pexels.com/license/ (consultée le 12 novembre 2026).
Version locale compressée, largeur 1200 px, 145 986 octets.
Image d'ambiance routière : elle ne représente ni un véhicule de la flotte ni un trajet disponible et sa localisation belge n'est pas affirmée.
L'illustration SVG locale demeure le secours en cas d'échec de chargement.

`mobilite-transport.svg` est une illustration vectorielle originale créée pour
ce projet : automobile générique, route et silhouettes urbaines, sans logo ni
photo d'un véhicule réel. Elle est décorative dans les bannières et les cartes.
La photographie Pexels ci-dessus reste utilisée comme fond de la présentation du service.

## Carrousel automobile — issue #29

Licence et pages sources vérifiées le 14 novembre 2026 :
[licence Pexels](https://www.pexels.com/license/) et
[conditions Pexels](https://www.pexels.com/terms-of-service/).
La licence autorise le téléchargement, l’utilisation sur ce site et les adaptations
de format/cadrage. Elle n’autorise pas à suggérer que les personnes ou les marques
représentées approuvent le service, à revendre les photos non modifiées ou à les
redistribuer comme banque d’images. Les crédits sont conservés même si leur mention
n’est pas imposée par cette licence.

| Fichiers locaux `car-{nom}-*.webp` | Photographe | Source Pexels |
| --- | --- | --- |
| `car-route-*` | Kelvin Linhares | [28502253 — berline sur une route](https://www.pexels.com/photo/sleek-silver-car-on-scenic-country-road-28502253/) |
| `car-famille-*` | Mikhail Nilov | [8323226 — famille près d’une voiture](https://www.pexels.com/photo/a-family-standing-near-parked-car-while-looking-at-the-camera-8323226/) |
| `car-partage-*` | Clément Proust | [18378381 — conducteur et passager](https://www.pexels.com/photo/people-traveling-on-a-road-18378381/) |
| `car-bruxelles-*` | Hakan Yasar | [14599371 — voitures dans une rue de Bruxelles](https://www.pexels.com/photo/cars-parked-along-street-14599371/) |

Chaque photographie possède trois versions paysage 640×360, 1024×576 et 1920×1080,
et une version mobile recadrée 640×800. Compression WebP qualité 0,8 et cadrages
reproductibles par `scripts/optimiser-photos-carousel.cjs`, avec Edge/Playwright
comme outillage externe au frontend. Aucune dépendance de production ajoutée.
Les originaux de travail ne sont pas publiés dans le dépôt.

Ces photos illustrent l’ambiance du service : elles ne représentent ni les véhicules
enregistrés, ni les personnes inscrites, ni des offres de trajet. Seule la photo de
Bruxelles est localisée en Belgique par sa source ; les autres n’ont pas cette attribution.
Les crédits de la photo courante sont consultables dans le carrousel, et les cartes
portent la mention « Photos d’illustration · hors flotte réelle ».
Le SVG original local demeure le secours en cas d’échec d’une photo du carrousel.
