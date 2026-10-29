# Contrat API pour Angular

Contrat relevé dans les controllers, DTO et services du backend, le 29 octobre 2026.
Base locale : `http://localhost:8081` (adapter au port choisi). Swagger dev :
`/swagger-ui/index.html`, schéma complet : `/v3/api-docs`.
Aucun composant Angular n'est créé.

## Conventions

- JWT : `Authorization: Bearer <accessToken>`. Tous les endpoints ci-dessous exigent un JWT, sauf les deux POST publics explicitement indiqués.
- CLIENT et ADMIN sont les rôles techniques ; GLOBAL/LOCAL sont les périmètres administratifs. LOCAL est limité à sa zone, y compris avant pagination.
- Dates : chaînes ISO 8601 avec décalage, par exemple `2026-11-03T09:00:00Z`. Montants en EUR. Identifiants numériques strictement positifs.
- Listes : `page=0&size=20`, taille 1 à 100 ; réponse `{"content":[...],"page":0,"size":20,"totalElements":1,"totalPages":1}`.
- Erreur JSON commune : `{"timestamp":"...","status":409,"code":"TRIP_LOCATIONS_LOCKED","message":"...","path":"/api/trajets/1","validationErrors":[]}`.
- Erreurs communes : 400 données/paramètres invalides ; 401 JWT absent, invalide, expiré ou compte indisponible ; 403 rôle/droit interdit ; 404 ressource absente ou masquée ; 409 conflit métier. Afficher `message`, exploiter `code`, associer les `validationErrors` aux formulaires.
- CORS : charger `CORS_ALLOWED_ORIGINS=http://localhost:4200` dans l'environnement local avant le démarrage. Les en-têtes Authorization, Content-Type et Idempotency-Key sont autorisés. Aucune origine n'est ouverte par défaut ; aucune session cookie requise.
- Les paiements sont simulés uniquement en profil dev sans prod. Une configuration de paiement indisponible donne 503. Aucune coordonnée bancaire à demander.
## Authentification et inscription par invitation

| Méthode et URL | Accès / JWT | Paramètres et corps JSON | Réponse / succès | Erreurs |
|---|---|---|---|---|
| POST /api/auth/login | Public, sans JWT | `{"identifiant":"matricule-ou-email","password":"mot-de-passe"}` | 200 `{"accessToken":"<JWT>","tokenType":"Bearer","expiresIn":900}` (durée configurée) | 400, 401 |
| POST /api/auth/invitations/accept | Public, sans JWT | `{"jeton":"<preuve-personnelle>","password":"<mot-de-passe-choisi>"}` | 204 sans corps, puis connexion normale | 400 invitation invalide/expirée/consommée ou mot de passe invalide |
| GET /api/auth/me | Tout compte authentifié | Aucun corps | 200 Profile | 401, 403 |

Profile : `{"compteId":1,"identifiant":"...","role":"CLIENT","clientId":2,"categorie":"LOCAL","zoneClientId":1,"perimetreAdmin":null,"zoneAdminId":null}`.
ADMIN : client/catégorie/zoneClient nuls ; périmètre GLOBAL ou LOCAL.

Il n'existe pas de POST /register public. L'administrateur crée le compte invité ; son destinataire définit son mot de passe avec la preuve reçue. Minimum 15 caractères Unicode, maximum 72 octets UTF-8. Les clients se connectent par matricule, les administrateurs par email. Ne jamais afficher ni journaliser JWT, preuve ou mot de passe.

## Référentiels, trajets et réservations

Tous ces endpoints exigent CLIENT et JWT. Pas de corps pour GET ni pour les POST indiqués « aucun ».
Les réponses nommées sont illustrées dans la section suivante.

| Méthode et URL | Paramètres / corps JSON | Réponse / succès | Droits complémentaires et erreurs |
|---|---|---|---|
| GET /api/zones | page, size | 200 Page<Zone> | Zones autorisées ; 400, 401, 403 |
| GET /api/vehicules | page, size, zoneId facultatif | 200 Page<Vehicule> | Flotte des zones autorisées ; 400, 401, 403 |
| GET /api/itineraires | page, size, origine et destination facultatives, 255 caractères maximum | 200 Page<Itineraire> | Catalogue proposé, recherche partielle sans casse ; 400, 401, 403 |
| POST /api/trajets | CreateTrajet ci-dessous | 201 Trajet ; Location /api/trajets/{id} | Éligibilité, zone, véhicule, horizon, disponibilité ; 400, 401, 403, 404, 409 |
| GET /api/trajets | page, size, zoneId facultatif | 200 Page<Trajet> | Publics autorisés et privés organisés/rejoints ; 400, 401, 403 |
| GET /api/trajets/{id} | id dans le chemin | 200 Trajet | 400, 401, 403 ; 404 si absent ou invisible |
| PUT /api/trajets/{id} | UpdateTrajet ci-dessous, corps complet | 200 Trajet | Organisateur uniquement ; 400, 401, 403, 404, 409 |
| POST /api/trajets/{id}/reservations | Aucun | 201 Reservation | Pour soi, trajet PUBLIC ; 400, 401, 403, 404, 409 |
| POST /api/trajets/{id}/participants | `{"clientId":2}` | 201 Reservation | Organisateur d'un trajet PRIVE, participant éligible ; 400, 401, 403, 404, 409 |
| GET /api/reservations/me | page, size | 200 Page<Reservation> | Uniquement les siennes, annulées incluses ; 400, 401, 403 |
| POST /api/reservations/{id}/annulation | Aucun | 200 Reservation ANNULEE | Propriétaire avant départ ; 400, 401, 403, 404, 409 ; 503 simulation de remboursement indisponible |

CreateTrajet :

```json
{"zoneId":1,"vehiculeId":2,"depart":"2026-11-03T09:00:00Z","arrivee":"2026-11-03T10:00:00Z","type":"PUBLIC","origine":"Bruxelles-Midi","destination":"Namur"}
```

UpdateTrajet : mêmes champs sauf `zoneId` et `type`, qui ne sont pas modifiables. `vehiculeId`, `depart`, `arrivee`, `origine`, `destination` sont tous obligatoires. Les lieux sont normalisés aux extrémités, non vides, maximum 255 caractères. Arrivée strictement après départ.

Modification : PLANIFIE avant départ ; aucune confirmation active ni paiement historique, J-1 déjà effectué, vérification historique requise ou dette du trajet. Une demande EN_ATTENTE active, organisateur inclus, fige les lieux. Une valeur identique normalisée reste autorisée ; les annulations ne bloquent pas ce contrôle. Les horaires/véhicules restent soumis à l'éligibilité de tous les participants EN_ATTENTE, à la disponibilité et à la pause de 900 secondes. Codes 409 possibles : `TRIP_LOCATIONS_LOCKED`, `PENDING_RESERVATION_INCOMPATIBLE`, `TRIP_HAS_CONFIRMED_RESERVATION`, `TRIP_HAS_PAYMENT`, `TRIP_J1_ALREADY_PROCESSED`, `TRIP_J1_VERIFICATION_REQUIRED`, `TRIP_J1_DUE`, `TRIP_HAS_DEBT`, `TRIP_ALREADY_STARTED`, `TRIP_NOT_MODIFIABLE`, `VEHICLE_CONFLICT`.

Une demande n'est pas une place garantie : la capacité est de quatre confirmations payées, organisateur inclus. La création produit sa réservation EN_ATTENTE ; lui aussi doit payer pour confirmer. PUBLIC/PRIVE sont les types ; PLANIFIE/COMPLET/ANNULE/REALISE sont les statuts. L'annulation individuelle ne supprime pas l'historique ; une réinscription crée une nouvelle réservation.

### Exemples des réponses métier

```json
{
  "zone": {"zoneId":1,"nom":"Bruxelles","actif":true,"fuseauHoraire":"Europe/Brussels","ouverteOccasionnels":true},
  "vehicule": {"vehiculeId":2,"zoneId":1,"zoneNom":"Bruxelles","actif":true},
  "itineraire": {"itineraireId":1,"origine":"Bruxelles-Midi","destination":"Namur"},
  "trajet": {"trajetId":1,"zoneId":1,"vehiculeId":2,"organisateurId":2,"depart":"2026-11-03T09:00:00Z","arrivee":"2026-11-03T10:00:00Z","fuseauHoraire":"Europe/Brussels","type":"PUBLIC","statut":"PLANIFIE","origine":"Bruxelles-Midi","destination":"Namur","traiteJ1Le":null,"verificationJ1Requise":false},
  "reservation": {"reservationId":1,"trajetId":1,"clientId":2,"statut":"EN_ATTENTE","organisateur":true,"annuleLe":null}
}
```

Les clés enveloppantes zone/vehicule/etc. servent seulement à regrouper les exemples : chaque endpoint renvoie directement l'objet ou la page annoncé. Les anciens trajets peuvent avoir origine/destination nulles. Le détail ne donne ni liste de participants ni compteur de places libres.

## Paiements, remboursements et dettes

Tous exigent CLIENT et JWT. Pour chaque POST, en-tête obligatoire `Idempotency-Key: <UUID>`. Conserver la même clé pour rejouer la même opération ; ne pas produire une nouvelle clé à chaque tentative réseau.

| Méthode et URL | Paramètres / corps JSON | Réponse / succès | Erreurs |
|---|---|---|---|
| POST /api/reservations/{id}/paiements | Aucun corps ; UUID en en-tête | 200 Payment, confirmation de sa réservation, 20 EUR imposés | 400, 401, 403, 404, 409, 503 |
| GET /api/paiements/me | page, size | 200 Page<Payment>, places et dettes du propriétaire | 400, 401, 403 |
| GET /api/dettes/me | page, size | 200 Page<Dette> du propriétaire | 400, 401, 403 |
| POST /api/dettes/{id}/paiements | `{"montant":20.00}` ; UUID en en-tête | 200 Payment, règlement partiel ou intégral | 400, 401, 403, 404, 409, 503 |

Payment : `{"paiementId":1,"nature":"PLACE","ressourceId":1,"montant":20.00,"mode":"SIMULATION","valideLe":"2026-11-01T09:00:00Z","montantRembourse":0.00,"rembourseLe":null}`.
Nature PLACE ou DETTE ; ressourceId est respectivement la réservation ou la dette. Ne pas identifier un paiement uniquement par paiementId : les deux natures ont des espaces d'identifiants distincts.
Dette : `{"detteId":1,"trajetId":1,"montant":80.00,"regle":20.00,"statut":"ACTIVE"}` ; statut REGULARISEE après règlement intégral.

Conflits : `ALREADY_PAID`, `IDEMPOTENCY_CONFLICT`, `RESERVATION_NOT_PENDING`, `TRIP_FULL`, `ACTIVE_DEBT`, `BOOKING_HORIZON`, `INVALID_DEBT_AMOUNT`, etc. Les règles sont revérifiées sous verrou. Un rejeu idempotent restitue la preuve existante.

Remboursement automatique simulé de 20 EUR lors de l'annulation à H-24 inclus ou avant, aucun après cette limite ; annulation impossible dès le départ. Aucun POST de remboursement séparé ni remboursement bancaire. Consulter montantRembourse/rembourseLe dans l'historique.

## Administration

Tous exigent ADMIN et JWT. G = GLOBAL ; L = LOCAL dans sa zone. Les erreurs communes 400/401/403/404/409 s'appliquent aux écritures ; les lectures ont 400/401/403 et les détails 404. Aucun corps pour GET/DELETE ni pour les habilitations VIP.

| Méthode et URL | Périmètre | Paramètres / corps JSON | Réponse / succès |
|---|---|---|---|
| GET /api/admin/comptes | G/L | page, size, identifiant exact sans casse, role, categorie, zoneId, compteActif, clientActif facultatifs | 200 Page<Compte> |
| GET /api/admin/comptes/{id} | G/L | id | 200 Compte |
| POST /api/admin/clients | G ; L uniquement client LOCAL de sa zone | `{"email":"personne@example.test","categorie":"LOCAL","zoneClientId":1}` | 201 `{"compte":Compte,"invitationExpireA":"..."}` |
| POST /api/admin/administrateurs | G | `{"email":"admin@example.test","perimetreAdmin":"LOCAL","zoneAdminId":1}` | 201 même enveloppe d'invitation |
| POST /api/admin/comptes/{id}/invitations | G ; L clients LOCAL de sa zone | Aucun corps ou {} | 202 `{"statut":"PENDING","expireA":"..."}` |
| GET /api/admin/statistiques | G/L | zoneId facultatif, intersecté avec périmètre | 200 Statistics |
| GET /api/admin/zones | G/L | page, size | 200 Page<Zone> |
| GET /api/admin/zones/{id} | G/L | id | 200 Zone |
| POST /api/admin/zones | G | ZoneWrite ci-dessous | 201 Zone |
| PUT /api/admin/zones/{id} | G | ZoneWrite | 200 Zone |
| PATCH /api/admin/zones/{id}/statut | G | `{"actif":false}` | 200 Zone |
| PUT /api/admin/zones/{id}/clients-vip/{clientId} | G | Identifiants de chemin, aucun corps | 204 sans corps |
| DELETE /api/admin/zones/{id}/clients-vip/{clientId} | G | Identifiants de chemin, aucun corps | 204 sans corps |
| GET /api/admin/vehicules | G/L | page, size, zoneId facultatif | 200 Page<Vehicule> |
| GET /api/admin/vehicules/{id} | G/L | id | 200 Vehicule |
| POST /api/admin/vehicules | G/L | `{"zoneId":1}` | 201 Vehicule |
| PATCH /api/admin/vehicules/{id}/statut | G/L | `{"actif":false}` | 200 Vehicule |
| GET /api/admin/indisponibilites | G/L | page, size, zoneId, vehiculeId facultatifs | 200 Page<Indisponibilite> |
| GET /api/admin/indisponibilites/{id} | G/L | id | 200 Indisponibilite |
| POST /api/admin/indisponibilites | G/L | PeriodWrite ci-dessous | 201 Indisponibilite |
| PUT /api/admin/indisponibilites/{id} | G/L | PeriodWrite, cible inchangée | 200 Indisponibilite |
| DELETE /api/admin/indisponibilites/{id} | G/L | id | 204 sans corps |

Création/renouvellement d'invitation : 503 si la livraison n'est pas configurée ; PENDING signifie mise en file, sans garantie de réception immédiate. Aucun jeton retourné.
Les catégories sont VIP/LOCAL/OCCASIONNEL ; zoneClientId est imposée selon les règles de catégorie du service. Pour ADMIN GLOBAL, zoneAdminId doit être absent ; LOCAL exige sa zone.

ZoneWrite : `{"nom":"Bruxelles","fuseauHoraire":"Europe/Brussels","ouverteOccasionnels":true}`.
PeriodWrite : `{"zoneId":1,"debut":"2026-11-03T09:00:00Z","fin":"2026-11-03T10:00:00Z"}`, ou vehiculeId au lieu de zoneId ; exactement une cible. Intervalle [début,fin), fin après début. Une indisponibilité ne peut toucher un trajet existant.

Indisponibilite : `{"indisponibiliteId":1,"zoneId":1,"vehiculeId":null,"debut":"...","fin":"..."}`.
Compte : `{"compteId":1,"identifiant":"...","role":"CLIENT","clientId":2,"numeroClient":"...","categorie":"LOCAL","zoneClientId":1,"perimetreAdmin":null,"zoneAdminId":null,"compteActif":true,"clientActif":true,"etatIdentifiants":"INVITE","accesEffectif":false}`.

Statistics : `{"trajetsPlanifies":0,"trajetsComplets":0,"trajetsAnnules":0,"reservationsEnAttente":0,"reservationsConfirmees":0,"reservationsAnnulees":0,"paiementsPlaces":0,"paiementsDettes":0,"dettesRestantes":0}`.
Cumuls persistés, aucun filtre temporel ; les montants de places sont bruts, les remboursements ne sont pas déduits. Aucun compteur REALISE dans cette projection.

## Limites réelles du contrat

Pas d'API d'annulation complète d'un trajet, d'inscription publique, de véhicule personnel, de modification/désactivation de compte, de liste administrative détaillée des paiements/dettes, de consultation des logs ni de résolution administrative des historiques J-1 incertains.

La recherche de trajets filtre seulement zoneId côté serveur. origine/destination concernent le catalogue /itineraires, pas /trajets. Pas de filtres serveur par date, statut, organisateur ; ne pas présenter une page filtrée côté navigateur comme un résultat global. Un filtre optionnel organisateur connecté serait la plus petite extension à discuter pour une liste « Mes trajets » paginée efficace ; aucune extension n'est ajoutée sans besoin confirmé.
