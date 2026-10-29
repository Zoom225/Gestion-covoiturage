# Recherche et consultation des trajets — étape 2

Suivi : [issue #60](https://github.com/Zoom225/Gestion-covoiturage/issues/60). Branche : `fonctionnalite/frontend-recherche-trajets`. La PR #59 a été vérifiée puis fusionnée avant le développement ; la nouvelle PR doit rester ouverte jusqu’à autorisation.

## Contrat vérifié

| Endpoint réel | Requête et rôle | Données exploitées |
|---|---|---|
| GET /api/zones | CLIENT + JWT ; page, size | zoneId, nom, actif, fuseauHoraire, ouverteOccasionnels |
| GET /api/trajets | CLIENT + JWT ; page, size, zoneId facultatif | Page de TrajetResponse, filtrée par visibilité avant pagination |
| GET /api/trajets/{id} | CLIENT + JWT ; identifiant positif | TrajetResponse autorisé ou 404 générique si absent/invisible |

Les sources vérifiées sont `ClientReferentielController`, `TrajetController`, leurs DTO, `ReferentielService`, `TrajetService`, `ZoneAccessRepository` et la politique de réservation existante. Aucun backend, migration, volume ou secret n’est modifié.

Le backend ne recherche les trajets que par **zone**, avec pagination. Les filtres origine/destination de `/itineraires` concernent un catalogue distinct ; aucun filtre par lieu/date/statut n’est ajouté ici, même côté navigateur sur une seule page.

## Parcours utilisateur

- `/trajets` : chargement de toutes les pages de zones, choix « Toutes mes zones » ou zone autorisée, recherche automatique initiale, formulaire, actualisation et pagination serveur à 12 résultats.
- `/trajets/{id}` : informations autorisées, zone et véhicule identifiés, horaires, visibilité, statut et retour vers les mêmes critères/page.
- Critères et page dans l’URL : une navigation arrière ou un retour du détail retrouve la recherche ; une nouvelle zone remet la page à zéro. Une page devenue hors limites est ramenée vers la dernière page existante.
- Les recherches remplacées sont annulées avec `switchMap`. Les anciens résultats disparaissent pendant une nouvelle demande ou après un refus ; aucun résultat fictif n’est utilisé comme repli.
- Sans zone : état dédié et possibilité d’actualiser. Sans trajet : message invitant à changer de zone ou actualiser. Une panne de zones ou de résultats peut être réessayée.
- 401 : traitement de session existant ; 403 : refus compréhensible ; 404 détail : même message pour trajet absent ou invisible. Aucun message technique brut affiché.

## Informations affichées et limites

TrajetResponse expose trajetId, zoneId, vehiculeId, organisateurId, depart, arrivee, fuseauHoraire, type, statut, origine/destination parfois nulles, traiteJ1Le et verificationJ1Requise. Les champs techniques ne sont pas tous destinés à l’écran : aucun identifiant d’organisateur ni donnée d’autres participants n’est affiché.

Les horaires sont formatés en français avec `Intl.DateTimeFormat` et le fuseau IANA du trajet, y compris le changement d’heure et une arrivée le lendemain. Les lieux historiques absents restent explicitement « non renseignés ».

Les statuts PLANIFIE, COMPLET, ANNULE et REALISE disposent d’un libellé français et d’une couleur accompagnée de texte. Le détail explique un trajet complet, annulé, terminé ou dont le départ est passé. Un résultat PLANIFIE n’est jamais une garantie d’éligibilité ou de place.

**Aucun compteur de places restantes ni prix n’est exposé par ces DTO.** L’interface affiche donc que les places ne sont pas communiquées et n’invente ni zéro place, ni tarif, ni durée/distance. Le véhicule est présenté par son numéro autorisé, sans champ de flotte supplémentaire inventé.

La réservation est une indication non interactive « Réservation bientôt disponible ». Aucun POST de réservation ni bouton actif trompeur ; les règles complètes de demande, confirmation et paiement restent à la prochaine étape.

## Architecture

`core/services/zones-api.ts` est partagé par recherche et détail, avec le modèle ZoneResponse et le contrat générique PageResponse. Il charge toutes les pages sans cache entre comptes, et ne publie pas une liste partielle si une page échoue.

`features/trajets/` contient les deux pages standalone, `TrajetsApi`, le DTO TrajetResponse, les critères validés, une carte réutilisée pour les résultats, un badge de statut commun et un pipe de date. Les styles du module prolongent les tokens Tailwind existants. Les états de page utilisent les Signals et les abonnements sont arrêtés à la destruction des composants.

AuthService, AuthApi, interceptor, guards, messages, icônes et cadre privé existants sont réutilisés. Les scénarios navigateur partagent leur préparation d’authentification dans `e2e/support/`, jamais dans le code applicatif.

## Recette d’authentification réelle

Le mécanisme administratif existe : création par `/api/admin/clients`, invitation par `/api/admin/comptes/{id}/invitations`, livraison chiffrée puis courrier avec `#invitation=...`. Un amorçage opérateur séparé existe dans `BootstrapGlobal`, avec opt-in et configuration propres ; il ne constitue pas un endpoint public.

Le contrôle en lecture seule de la base locale n’a trouvé aucun compte administrateur de test. Aucun compte, mot de passe, invitation ou JWT n’est donc créé ou injecté artificiellement ; aucune donnée existante n’est modifiée. Aucun administrateur d’un environnement isolé préconfiguré n’est disponible.

Reste à effectuer avec cet environnement et ses identifiants privés : inviter le client via un administrateur autorisé, ouvrir son lien dans Angular, activer, se connecter, vérifier `/auth/me`, tableau de bord, recherche puis déconnexion. Les secrets doivent rester hors fichiers versionnés, journaux et captures partagées.

Cette recette différée ne remplace pas les tests du transport, de l’interface et des droits. Les réussites avec réponses contrôlées des tests d’interface ne sont pas annoncées comme une authentification réussie sur PostgreSQL local.

## Validation

Commandes depuis `frontend/` : `npm run build`, `npm test -- --watch=false`, `npm run test:e2e`. Les tests couvrent le contrat HTTP, les zones paginées, les paramètres, la navigation, les états, l’annulation des requêtes obsolètes, les fuseaux et les droits. Les contrôles navigateur couvrent les largeurs 360/390/768/1024/1440, les parcours réels de l’interface et les règles automatiques axe sélectionnées. Les résultats définitifs et liens CI sont consignés dans la PR.

Le build de production réussit (311,87 ko initiaux, 84,73 ko transférés estimés). La suite unitaire compte **80 tests réussis** contre 40 pour le socle initial. Les tests sont exécutés séquentiellement sur le poste local pour éviter la saturation mémoire ; aucun délai unitaire n’est augmenté et aucun test n’est désactivé.

`npm run test:api`, avec Angular et Spring Boot démarrés, vérifie aussi les refus réels sans JWT des trois nouveaux endpoints, en plus des refus de connexion et d’invitation du socle. Ce contrôle en lecture seule ne prétend pas valider une connexion réussie.

Les captures de recherche et de détail ont été examinées sur mobile et ordinateur. Aucun débordement horizontal ni violation des règles axe sélectionnées n’a été détecté sur les cinq largeurs ; cela ne constitue pas un audit d’accessibilité humain exhaustif.

## Explication pour le jury — environ une minute

« Le client choisit une zone autorisée. Angular envoie uniquement les critères que le backend comprend : la zone et la pagination. Le serveur applique les droits avant de renvoyer les résultats. Un service centralise ces requêtes et les composants affichent les cartes, les états vides ou les erreurs. Les critères restent dans l’URL pour retrouver la bonne page après consultation du détail. Lorsqu’une nouvelle recherche commence, l’ancienne requête est annulée. Les heures sont affichées dans le fuseau du trajet, et les informations manquantes restent indiquées comme telles. Le backend ne fournit pas le nombre de places restantes ni le prix dans cette réponse : je ne les calcule pas artificiellement. La réservation n’est pas encore ouverte dans cette interface. Les tests vérifient le contrat, les droits, les erreurs et le comportement sur mobile et ordinateur. »
