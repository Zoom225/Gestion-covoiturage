> Historique initial, remplace pour D1-D5 par les decisions validees et le [contrat courant](implementation.md). La priorite au paiement ne garantit pas une confirmation a l'organisateur.

# Lot 5 — trajets et réservations

Issue : [#26](https://github.com/Zoom225/Gestion-covoiturage/issues/26).
Branche : feature/trips-reservations. Dépend du lot 4 / issue #25.

Base commune : origin/master 653e9a0c044bdc2fd7151498ff9d9192c4269384.
PR #24 fusionnée le 25 octobre 2026 ; issue #23 fermée. 294 tests antérieurs conservés.
Les contrôles GitHub de la PR #24 sont réussis ; les rapports locaux existants et la CI confirment
294 tests, 0 échec, 0 erreur, 0 ignoré. Aucun nouveau lancement Maven pour les lots ci-dessous à ce stade.

Références lues : énoncé ../Enonce_CarShare_Network.docx ; cahier
../cahier des charge/FIN-DriveConnect_Cahier_des_charges_FINAL_25P_DIAGRAMMES_UML_EA.docx,
registre RM01–RM60, sections 5/7/14/16/19/25, et docs/gestion-comptes/.
Les chemins externes sont relatifs à la racine du dépôt et restent hors Git.

Ce fichier est une préparation, pas une décision fonctionnelle validée ni une annonce d'API disponible.
Aucune migration, aucun compte réel, aucune donnée PostgreSQL partagée n'est modifié.
Les trois branches ont été créées depuis ce master. Les lots 5 et 6 devront intégrer les commits
de leurs dépendances et être présentés en PR dépendantes, sans fusion automatique dans master.

## Règles confirmées à conserver

- RM05/RM06/RM08 : ouverture J-30 VIP, J-10 LOCAL, J-3 OCCASIONNEL.
  RM05/RM06 mentionnent la date et l'heure du départ ; ne pas ouvrir silencieusement à minuit.
- RM07/RM12/RM38 : zone LOCAL et zones autorisées VIP ; autorisation serveur.
- RM14/RM17/RM18/RM22 : organisateur issu du JWT ; PUBLIC/PRIVE ;
  ajout privé par l'organisateur seul ; réservation publique personnelle.
- RM16/RM25–RM28/RM31–RM35/RM40 : quatre confirmés maximum, paiement avant confirmation,
  EN_ATTENTE sans place garantie, premier paiement effectivement validé prioritaire.
- RM19–RM21/RM56 : disponibilité, non-chevauchement véhicule, pause de quinze minutes,
  revalidation des modifications.
- RM24/RM58/RM59 : doublons et intégrité ; politique de réinscription après annulation non inventée.
- RM36/RM37/RM44 : dette ACTIVE bloque création, réservation et ajout.
- RM49/RM51 : périmètres administratifs conservés.

## Décisions bloquantes du cahier

### D5-01 — heure de fin et bornes temporelles

Sections 7, 14 et 25 : durée/heure de fin « À PRÉCISER », indispensable pour les conflits et la pause.
Proposition soumise : début et fin saisis explicitement, instants stockés UTC, calendrier métier
Europe/Paris. Les horizons conservent l'heure : ouverture à départ moins le nombre de jours retenu,
borne d'ouverture incluse, départ futur obligatoire ; ne pas substituer un accès au jour entier.
Aucune durée moyenne de trajet ni fuseau métier n'est présumé validé.

### D5-02 — zones VIP/OCCASIONNEL

Sections 14/25 et RM12 : le mode de définition des zones autorisées n'est pas fixé.
Options soumises : habilitations explicites par client attribuées GLOBAL, accès réseau des deux
catégories, ou VIP sur habilitations et OCCASIONNEL réseau. LOCAL reste dans sa zone.
Ne pas attribuer toutes les zones implicitement à partir du préfixe ou d'un champ Angular.

### D5-03 — contradiction de première place

RM15 : organisateur « participant confirmé ... dès sa création ».
RM27/RM40 et consigne du responsable : aucune confirmation sans paiement validé.
La première place ne peut pas être auto-confirmée ni accompagnée d'un faux paiement.
Proposition soumise : l'organisateur paie d'abord ses 20 euros avant toute confirmation d'autrui.
Cette proposition nécessite une décision explicite qui précise RM15 sans renuméroter les RM.

### D5-04 — paiement du participant privé

Sections 5.2, 14 et 25 : qui initie/valide les 20 euros n'est pas décidé.
Proposition soumise avec D5-03 : chaque participant ajouté paie personnellement sa place ;
l'organisateur ne paie pas pour autrui. Aucune confirmation à l'ajout.
Annulation/remboursement/réinscription et transition REALISE restent séparés et ouverts.

## Architecture et contrats à stabiliser — non implémentés

- TrajetService : création et disponibilité, propriété, états et transactions.
- ReservationService : demande personnelle, ajout privé par organisateur, éligibilité commune.
- Politique métier commune catégorie/zone/dette, distincte des rôles Spring Security.
- Réutiliser les services de disponibilité du lot 4 ; aucune validation d'intervalle concurrente dupliquée.
- Interface de validation de paiement réutilisable pour le lot 6, sans implémentation qui invente
  un paiement, sans endpoint acceptant un booléen frontend « paiement validé ».
- La consultation d'une dette ACTIVE doit être réelle en PostgreSQL ; pas de valeur false
  de secours en l'absence d'implémentation. Coordonner le modèle de dette avec le lot 6.
- Modèle envisagé : trajet → zone/véhicule/organisateur ; réservation → trajet/client ;
  états explicites ; FK, unicité et dates ; protection SQL de conflits sous concurrence.
- Schéma et migration additive après décisions et intégration de la dépendance ; pas de migration créée.

API à stabiliser : création/consultation des trajets, réservation personnelle, ajout privé,
consultation de ses réservations. Aucun endpoint d'annulation/remboursement non spécifié.

## Tests à développer

Horizon aux bornes avec Clock, zone, propriété, 401/403/404/409, dettes actives,
matricule et client inactif, participants privés, doubles demandes, absence de confirmation avant
paiement, véhicules en concurrence et pause exactement quinze minutes, indisponibilités, rollback.
Conserver 294 tests + ceux du lot 4. Aucun test ignoré ; clean verify et CI après implémentation.

AA1–AA7 et AA9–AA11 concernés partiellement ; AA8 préparé seulement.
Preuves prévues : modèle, séquence réservation/paiement, activité, matrice RM/tests et Swagger.
