# Plan Angular — pages raccordées aux API existantes

Préparation uniquement : aucun fichier de frontend/ n'est modifié.
Contrats et JSON : [contrat-api-angular.md](contrat-api-angular.md).

| Page | Endpoints nécessaires | Périmètre et éventuels manques |
|---|---|---|
| Accueil | Aucun ; GET /api/auth/me si JWT présent | Présentation publique ; pas de catalogue public sans connexion |
| Connexion | POST /api/auth/login, GET /api/auth/me | Matricule CLIENT ou email ADMIN, erreurs génériques 401 |
| Inscription / activation | POST /api/auth/invitations/accept | Activation sur invitation reçue ; aucune inscription publique autonome |
| Tableau de bord | GET /api/auth/me, /api/reservations/me, /api/dettes/me, /api/paiements/me | Données personnelles uniquement, pas de compteur de places libres |
| Recherche de trajets | GET /api/zones, /api/trajets?zoneId=... | Recherche paginée par zone. Pas de filtre serveur date/lieu/statut ; ne pas filtrer une seule page en prétendant filtrer tout le catalogue |
| Détail d'un trajet | GET /api/trajets/{id}, /api/reservations/me | Visibilité contrôlée ; statut affiché ; pas de liste de participants ni compteur de capacité dans la réponse |
| Réservation | POST /api/trajets/{id}/reservations, POST /api/reservations/{id}/paiements | PUBLIC : demande puis paiement simulé de 20 EUR. Garder l'UUID idempotent lors des reprises réseau |
| Participants privés | POST /api/trajets/{id}/participants | Organisateur uniquement ; clientId à fournir. Pas d'annuaire client public pour sélectionner une personne par nom ; ne pas exposer /admin/comptes aux clients. Une sélection par matricule nécessiterait un besoin et un contrat de confidentialité validés |
| Mes réservations | GET /api/reservations/me, POST /api/reservations/{id}/annulation, GET /api/paiements/me | EN_ATTENTE/CONFIRMEE/ANNULEE, date d'annulation, retour au détail si encore visible ; nouvelle réservation possible après annulation selon règles |
| Mes trajets | GET /api/auth/me, GET /api/trajets | Filtrer organisateurId sur toutes les pages récupérées ; pas de pagination serveur dédiée. Ne pas se baser uniquement sur ses réservations actives : l'organisateur peut avoir annulé sa place |
| Création de trajet | GET /api/zones, /api/vehicules?zoneId=..., /api/itineraires ; POST /api/trajets | Lieux proposés ou texte libre ; type PUBLIC/PRIVE ; compte organisateur issu du JWT |
| Modification de trajet | GET /api/trajets/{id}, GET /api/vehicules, PUT /api/trajets/{id} | Corps complet limité ; expliquer les refus 409, en particulier lieux figés par EN_ATTENTE ; ne pas annuler une réservation pour débloquer la demande |
| Mes véhicules / flotte accessible | GET /api/vehicules, GET /api/zones | Renommer la page « Véhicules disponibles dans mes zones » ou « Flotte ». Aucune propriété personnelle. La liste n'est pas une garantie de disponibilité à une date ; création contrôlée côté serveur |
| Paiements et remboursements | GET /api/paiements/me, GET /api/dettes/me, POST /api/dettes/{id}/paiements | Historique simulé, remboursement à H-24 inclus, règlement dette partiel/intégral ; pas de carte bancaire |
| Administration comptes | GET /api/admin/comptes, /api/admin/comptes/{id}, POST /api/admin/clients, /api/admin/administrateurs, /api/admin/comptes/{id}/invitations | GLOBAL réseau, LOCAL limité ; création/lecture/invitation, pas de boutons modifier/supprimer/désactiver compte |
| Administration zones | GET/POST /api/admin/zones, GET/PUT /api/admin/zones/{id}, PATCH /api/admin/zones/{id}/statut, PUT/DELETE /api/admin/zones/{id}/clients-vip/{clientId} | Écritures GLOBAL uniquement |
| Administration flotte et indisponibilités | GET/POST /api/admin/vehicules, GET /api/admin/vehicules/{id}, PATCH /api/admin/vehicules/{id}/statut ; GET/POST /api/admin/indisponibilites, GET/PUT/DELETE /api/admin/indisponibilites/{id} | GLOBAL ou LOCAL dans sa zone ; conflits de calendrier refusés |
| Administration statistiques | GET /api/admin/statistiques?zoneId=... | Cumuls existants, pas de compteur REALISE ni de total net des remboursements. Pas de liste administrative détaillée des paiements/dettes ni d'écran de consultation des logs |

## Ordre de réalisation conseillé

1. Socle Angular standalone, routage, HttpClient, formulaires connexion/activation, profil et gestion centralisée des erreurs.
2. Zones/flotte, recherche par zone, détail, création/modification et réservations personnelles.
3. Paiement simulé, annulation/remboursement, dettes, puis écrans administratifs réellement exposés.

Le guard améliore la navigation ; le backend reste responsable de l'autorisation. L'interceptor ajoute le JWT uniquement vers l'API prévue. À expiration : retour à la connexion ; aucun endpoint de renouvellement n'existe. Ne pas journaliser de jetons et ne pas conserver la preuve d'invitation après son utilisation.

## Besoins à arbitrer, sans endpoint ajouté

- Une pagination efficace de « Mes trajets » : ajouter éventuellement un filtre serveur « organisateur connecté » sur GET /api/trajets, avec périmètre appliqué avant pagination.
- Une recherche avancée : filtres serveur lieu/date/statut à préciser selon la page souhaitée ; le catalogue /itineraires ne recherche pas les trajets.
- Une gestion privée avec recherche de participants : modèle actuel clientId, sans annuaire public. Valider le besoin et la confidentialité avant toute extension.
- Les historiques J-1 incertains restent protégés et signalés ; aucune dette ne doit être inventée depuis l'interface.

Ces limites n'empêchent pas de commencer le frontend sur les parcours actuellement exposés. Elles empêchent de promettre des pages plus riches que le contrat backend.
