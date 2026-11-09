# Architecture du TFE covoiturage

Le projet reprend l'organisation par couches d'AtelierConnect, adaptee au domaine
du covoiturage. Il conserve Java 21, Spring Boot, Angular, PostgreSQL et Flyway.

```text
src/main/java/com/plateforme/covoiturage/
  controller/       API HTTP : validation des entrees et codes de reponse
  dto/request/      Donnees entrantes validees
  dto/response/     Contrats de reponse de l'API
  entity/           Modele persistant et transitions d'etat
    enums/          Etats, categories et types du modele
  exception/        Erreurs applicatives et traduction HTTP centralisee
  mapper/           Conversion du modele vers les DTO
  repository/       Acces aux donnees et requetes JPA
  service/          Interfaces des services applicatifs
    impl/           Implementations, politiques, verrous et journalisation
  scheduler/        Declenchement des traitements J-1 et de fin de trajet
  security/         Authentification JWT et autorisations
  config/           Configuration technique
  bootstrap/        Initialisation du premier administrateur
  delivery/         Transport et protection des invitations
  payment/          Adaptateur de paiement
```

Les controleurs et les schedulers injectent les interfaces `service`. Les beans
Spring sont les classes `service/impl/*ServiceImpl`. Les annotations de transaction
restent sur ces implementations : les controles, verrous, ecritures et remboursements
conservent leurs limites transactionnelles.

Les composants internes `BookingPolicy`, `JourneyLocks`,
`AvailabilityService`, `ClientNumberGenerator` et `SensitiveOperationJournal`
restent des collaborateurs concrets dans `service/impl`. Ils ne constituent pas
des contrats HTTP et ne necessitent pas d'interface artificielle.

Les autorisations de zone sont maintenant dans `BookingPolicy` :
`ZoneAccessPolicy` a ete fusionne puis supprime. `JourneyLocks` conserve
l'ordre zone, vehicules par identifiant, trajet, client avec des instructions
simples, pour proteger les quatre places et les paiements concurrents.

La reservation dispose de son propre `ReservationController` et de son
`ReservationMapper`. Le `TrajetController` gere les trajets. Les URL existantes
restent les memes, notamment `/api/trajets/{id}/reservations`,
`/api/trajets/{id}/participants` et `/api/reservations/me`.

Le resultat interne `InvitationService.InvitationEmise` conserve la protection du
jeton (`@JsonIgnore` et `toString` masque). Les controles d'acces ne sont pas
remplaces par les regles du projet de padel.

```text
frontend/src/app/
  core/             Authentification, guards, interceptors et configuration API
  features/
    accueil/pages/
    connexion/pages/
    activation/pages/
    tableau-de-bord/pages/
    trajets/pages/
    reservations/pages/
    paiements/pages/
    administration/pages/
    erreurs/pages/
  layouts/          Cadres public et prive
  shared/
    components/     Composants reutilisables
    models/         Contrats TypeScript partages
    utils/          Normalisation commune des erreurs API
```

Les pages Angular utilisent la convention `nom.page.ts` et, si necessaire,
`nom.page.html`. Les imports, templates et routes chargees a la demande suivent
ces emplacements. Les anciennes routes restent disponibles ; les parcours
metier sont accessibles depuis la navigation du tableau de bord.

## Capacite et chauffeur

Le dossier metier existant decrit une flotte avec chauffeur. Le chauffeur n'est
pas automatiquement l'organisateur : l'organisateur est actuellement un client
qui reserve et paie une place. Le modele ne gere pas encore l'identite ni
l'affectation individuelle des chauffeurs.

Regle confirmee : chaque trajet prevoit **4 passagers + 1 chauffeur**, soit
**5 personnes au total**. Le chauffeur est deja prevu par la flotte ; il n'a pas
de reservation passager et n'entre pas dans le compteur de places payantes.
L'organisateur est l'un des quatre passagers et ne devient pas chauffeur.

Le contrat de D4 reste donc quatre reservations confirmees maximum, organisateur
compris (`BookingPolicy.MAX_CONFIRMED_PASSENGERS`). Une demande EN_ATTENTE ne bloque pas une place ;
la confirmation intervient apres paiement. Le nombre de places intervient aussi
dans les contraintes PostgreSQL, le controle J-1 et les tests de concurrence.
Une modification de ce nombre doit donc etre appliquee sur toutes ces couches,
avec une nouvelle migration si necessaire, sans modifier les migrations deja livrees.

## Verification

Depuis la racine : `./mvnw.cmd test` (Docker requis pour PostgreSQL Testcontainers).
Depuis `frontend` : `npm run build` puis `npm test -- --watch=false`.

Les migrations existantes restent conservees ; les noms qualifies des enums dans
les requetes JPQL suivent leur nouveau package `entity.enums`.

## Simplification des regles partagees

- `BookingPolicy.requireEligible` et `requireOpen` nomment explicitement les
  controles communs aux trajets, reservations et paiements.
- `BookingPolicy.j1Deadline` est l'unique calcul de la veille a 00:15 a Bruxelles.
  Il sert au traitement J-1 et aux controles de modification et d'annulation.
- `AvailabilityService.requireAvailableForPlanning` centralise la pause de
  quinze minutes et les conflits de vehicule en creation et modification.
- `DetteTrajet.requireValidPaymentAmount` porte la regle de montant positif,
  au centime, sans depassement du solde. Le service l'appelle avant le prestataire ;
  l'entite protege aussi ses propres changements d'etat avec la meme regle.
- La pagination des comptes utilise `ReferencePageResponse`, comme les autres
  ressources. Le DTO identique `ComptePageResponse` est supprime, sans changement
  des champs JSON.

Les annotations de validation HTTP, les verrous transactionnels et les contraintes
PostgreSQL protegent des points d'entree differents. Ils restent en place pour
garantir les regles meme en cas de requetes concurrentes.

Validation de la restructuration initiale, le 9 novembre 2026 :

- Suite backend complete : 826 tests, aucun echec, aucune erreur, aucun ignore.
- Apres les derniers deplacements de controleurs : 96 tests cibles sur
  `TrajetIntegrationTests`, `ReservationCancellationIntegrationTests` et
  `PaymentIntegrationTests`, sans echec ni erreur.
- Build Angular de production reussi ; 40 tests frontend reussis.
- Verification des espaces et fins de lignes par `git diff --check` reussie.

Validation apres simplification des regles : **272 tests cibles reussis**, aucun
echec, aucune erreur et aucun ignore. Les 12 classes couvrent les comptes,
les paiements et leur integrite, les dettes, les reservations, J-1, les politiques
partagees et les modifications de trajets. Huit tests de dette verifient notamment
les montants invalides et les reglements partiels. La commande Maven `verify`
genere aussi le JAR executable.
