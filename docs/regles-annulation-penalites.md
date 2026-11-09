# Annulation, penalites et remboursements

Regle confirmee le 9 novembre 2026 : un client avec une penalite active
ne peut pas obtenir de remboursement lors de l'annulation de sa reservation.

Une penalite est une dette ACTIVE, y compris apres un reglement partiel.
Un reglement integral avant l'annulation retire ce blocage. Le reglement de la
penalite apres une annulation refusee au remboursement ne cree aucun droit retroactif.

| Situation au moment de l'annulation | Resultat |
| --- | --- |
| Depart atteint | Annulation refusee, aucune modification |
| Reservation en attente, avant le depart | Annulation, aucun paiement a rembourser |
| Place confirmee, penalite active | Annulation sans remboursement, quel que soit le delai |
| Place confirmee, aucune penalite active, au moins 24 h avant le depart | Remboursement integral simule de 20 EUR |
| Place confirmee, aucune penalite active, moins de 24 h avant le depart | Annulation sans remboursement |
| Penalite reglee apres une annulation avec penalite | Toujours aucun remboursement pour cette annulation |
| Versement pour regler une penalite | Non remboursable |

Les remboursements historiques deja effectues restent conserves ; cette regle
ne les annule pas retroactivement. La penalite d'un autre client n'affecte pas
les droits du proprietaire de la reservation.

## Application dans le code

- BookingPolicy definit ce qu'est une penalite active.
- ReservationServiceImpl verrouille le trajet puis le client, controle le depart
  et memorise annuleAvecPenalite a la premiere annulation.
- PaymentServiceImpl refuse le remboursement si cette indication est presente
  ou si une dette active existe, sans appeler le prestataire.
- V22 ajoute cette information a l'historique et protege aussi les nouvelles
  ecritures de remboursement dans PostgreSQL.
- Les reponses de reservation exposent annuleAvecPenalite. L'historique des
  paiements expose remboursementRefusePourPenalite pour expliquer le refus.
- Les replays d'annulation conservent la decision initiale. Les dettes et les
  paiements initiaux ne sont ni effaces ni compenses implicitement.

Le remboursement reste une simulation autorisee en developpement. Aucun
paiement bancaire reel n'est ajoute.

## Preuves de verification

ReservationCancellationIntegrationTests couvre notamment :

- l'absence de penalite, avec les bornes de 25, 24 et 23 heures ;
- la penalite active non payee ou partiellement payee ;
- le reglement ulterieur sans remboursement retroactif ;
- le reglement integral avant annulation, qui retire le blocage de penalite ;
- la penalite d'un autre client, qui ne modifie pas les droits du proprietaire ;
- les annulations repetees ou concurrentes et la conservation des paiements ;
- le rejet des nouvelles restitutions par ecriture SQL directe.

Le scenario navigateur « annulation avec penalite » verifie les messages de
confirmation et le refus conserve dans l'historique apres reglement de la dette.
Les refus de saisie et les messages metier ont aussi des tests dedies.

Verification du 9 novembre 2026 :

- La suite complete a execute 840 tests. Son seul echec etait une assertion
  qui attendait encore le schema V21 ; elle a ete corrigee pour V22.
- Les neuf tests de CovoiturageApplicationTests ont ensuite ete rejoues avec
  succes par Maven verify, qui a genere le JAR. Les derniers rapports des
  44 classes totalisent 840 tests, sans echec, erreur ni ignore restant.
- Les 19 tests d'annulation passent, dont les nouveaux controles de penalite.
- Les 47 tests Angular, les 18 scenarios navigateur, le build de production
  et la verification TypeScript stricte passent.
- Les 35 codes metier rencontres ont un message explicite. Docker Compose
  est valide ; les workflows CI sont actifs pour la branche amelioration.

Les scenarios navigateur utilisent un transport controle. Les ecritures et
contraintes financieres reelles sont testees dans PostgreSQL Testcontainers,
sans appliquer de migration manuelle sur une base partagee.
