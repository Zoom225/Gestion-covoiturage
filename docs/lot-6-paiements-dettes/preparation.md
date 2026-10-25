> Historique initial : D4 et D5 sont desormais valides. La priorite au premier paiement et le cron 00:15 Europe/Brussels remplacent les propositions anciennes. Voir [implementation.md](implementation.md).

# Lot 6 — paiements et dettes

Issue : [#27](https://github.com/Zoom225/Gestion-covoiturage/issues/27).
Branche : feature/payments-debts. Dépend des lots 4/5, issues #25/#26.

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

## Confirmé par l'énoncé, le cahier et le responsable

- Paiement explicitement simulé dans l'application : aucun prestataire bancaire réel annoncé.
- Place 20 euros, trajet de référence 80 euros, quatre confirmés maximum dont l'organisateur.
- EN_ATTENTE ne garantit pas de place ; priorité au premier paiement effectivement validé.
- Paiement/confirmation atomiques ; verrou PostgreSQL sur la dernière place ; contrôle de capacité,
  d'état, de propriété et des autres préconditions relues sous transaction.
- Trajet COMPLET/ANNULE : aucun nouveau paiement confirmé sans place.
- Privé incomplet J-1 → PUBLIC et dette fixe unique 80 euros pour l'organisateur.
- Dette ACTIVE sans expiration : bloque création/réservation/ajout.
- Paiement partiel conserve ACTIVE ; cumul intégral 80 euros → REGULARISEE.
- Paiements normaux et règlements de dette strictement séparés.
- Aucune pénalité d'une semaine, aucun faux paiement créé au lot 5.

## Blocages à résoudre

1. D5-03 / D5-04 : RM15 confirme l'organisateur à la création mais RM27/RM40 exigent un
   paiement ; les sections 5.2/14/25 laissent le paiement des participants privés ouvert.
   Attendre la réponse aux propositions organisateur payeur initial puis participants payant eux-mêmes.
2. D6-01 : sections 14/25 laissent heure et fuseau J-1 ouverts.
   Proposition soumise : 00:00 Europe/Paris la veille, avec rattrapage après interruption avant départ.
   Alternative soumise : échéance glissante exacte vingt-quatre heures avant le départ.
   Ne pas choisir silencieusement entre ces deux effets métier.
3. Annulation/remboursements et fin REALISE restent hors implémentation tant que non spécifiés.
   Ils ne doivent pas être remplacés par une annulation administrative ou un remboursement inventé.

## Conception envisagée — non implémentée

Controller → services métier → repositories → PostgreSQL ; Clock existant réutilisé.
Réutiliser la politique et le modèle trajet/réservation/dette du lot 5.
Le mécanisme de paiement simulé appartient au serveur et ne fait jamais confiance à un état
« validé » transmis par le navigateur. Référence idempotente unique ; montants en décimal,
jamais double ; conservation des tentatives selon le modèle retenu.
Le service ne valide un paiement normal que si la place peut être attribuée dans la même transaction.
Le règlement de dette ne confirme aucune réservation et ne modifie pas les activités des comptes.

Scheduler idempotent : transaction de conversion et dette UNIQUE(trajet_id), relecture sous verrou,
traitement rejouable après panne, aucune seconde dette pour le même fait générateur.
Ordre des verrous cohérent entre paiement, dette, réservation et traitements planifiés.
Aucune migration ou table provisoire créée ; les FK et index seront ajoutés par migration additive
après intégration des dépendances et validation des points ouverts.

## Tests et preuves à produire

- Deux paiements concurrents pour la dernière place : un succès, un refus, jamais cinq confirmés.
- Rejeu idempotent, changement de référence, paiement sans place, rollback.
- Montant normal 20 euros ; dette 80 euros ; séparation des deux opérations.
- Paiements partiels puis intégraux ; aucune expiration et aucun rétablissement avant paiement intégral.
- Dette ACTIVE bloquant les trois actions, y compris ajout privé par un tiers.
- Conversion J-1, trajet complet inchangé, concurrence et seconde exécution sans doublon de dette.
- Catégories/zones/indisponibilités/propriété et 401/403/404/409.
- Conservation de tous les tests antérieurs ; PostgreSQL Testcontainers ; clean verify, CI verte.
- SMTP, données partagées et comptes fictifs hors de ces tests.

Correspondance : RM25–RM35, RM36/RM37/RM39–RM45, RM48/RM50–RM55 et RM58–RM60 ;
AA1–AA7 et AA9–AA11 partiels ; AA8 non implémenté.
Preuves prévues : séquence de la dernière place, activité J-1, modèle dette/paiements et matrice des tests.
