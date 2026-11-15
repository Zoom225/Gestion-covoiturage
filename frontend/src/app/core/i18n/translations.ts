export type Locale = 'fr' | 'en';

export type TranslationKey =
  | 'seo.description'
  | 'carousel.label'
  | 'carousel.description'
  | 'carousel.slide'
  | 'carousel.position'
  | 'carousel.previous'
  | 'carousel.next'
  | 'carousel.pause'
  | 'carousel.play'
  | 'carousel.motionOff'
  | 'carousel.controls'
  | 'carousel.pagination'
  | 'carousel.goTo'
  | 'carousel.subtitle'
  | 'carousel.discover'
  | 'carousel.illustration'
  | 'carousel.roadAlt'
  | 'carousel.familyAlt'
  | 'carousel.sharedAlt'
  | 'carousel.brusselsAlt'
  | 'benefits.label'
  | 'benefits.driverTitle'
  | 'benefits.driverBody'
  | 'benefits.priceTitle'
  | 'benefits.priceBody'
  | 'benefits.simpleTitle'
  | 'benefits.simpleBody'
  | 'design.bannerLabel'
  | 'design.bannerTitle'
  | 'design.bannerSubtitle'
  | 'design.adminSubtitle'
  | 'design.carAlt'
  | 'design.adminMenu'
  | 'transport.differentCities'
  | 'vehicleChoice.placeholder'
  | 'vehicleChoice.hint'
  | 'vehicleChoice.loading'
  | 'vehicleChoice.chooseAgence'
  | 'vehicleChoice.empty'
  | 'vehicleChoice.manage'
  | 'premium.adminAccess'
  | 'premium.global'
  | 'premium.local'
  | 'premium.peopleRestrictions'
  | 'premium.tripsBookings'
  | 'premium.photoAlt'
  | 'ui.sessionExpired'
  | 'ui.networkError'
  | 'ui.accessDenied'
  | 'ui.expired'
  | 'ui.driverAuthorized'
  | 'voyageur.create.driverNotice'
  | 'ui.restrictionHistory'
  | 'ui.noRestrictions'
  | 'ui.active'
  | 'ui.inactive'
  | 'ui.unknownStart'
  | 'ui.reportAbsence'
  | 'ui.absenceReason'
  | 'ui.saveReport'
  | 'ui.absenceReported'
  | 'ui.antwerp'
  | 'ui.skip'
  | 'ui.menu'
  | 'ui.mainNav'
  | 'ui.searchNav'
  | 'ui.details'
  | 'ui.tripOverview'
  | 'ui.vehicle'
  | 'ui.availability'
  | 'ui.unitPrice'
  | 'ui.close'
  | 'ui.cityMissing'
  | 'ui.pendingNote'
  | 'ui.footerTagline'
  | 'ui.illustration'
  | 'ui.loadError'
  | 'ui.loadCities'
  | 'transport.reserve'
  | 'transport.eyebrow'
  | 'transport.together'
  | 'transport.simply'
  | 'transport.lead'
  | 'transport.driverKnown'
  | 'transport.maximum'
  | 'transport.price'
  | 'transport.lessCars'
  | 'transport.moreMeetings'
  | 'transport.startHere'
  | 'transport.onRoad'
  | 'transport.find'
  | 'transport.paymentDemo'
  | 'transport.searching'
  | 'transport.retry'
  | 'transport.noRides'
  | 'transport.tryOther'
  | 'transport.belgium'
  | 'transport.popular'
  | 'transport.suggestions'
  | 'transport.withYou'
  | 'transport.how'
  | 'transport.findStep'
  | 'transport.findBody'
  | 'transport.payStep'
  | 'transport.payBody'
  | 'transport.travelStep'
  | 'transport.travelBody'
  | 'transport.shareWheel'
  | 'transport.driverBody'
  | 'transport.myArea'
  | 'transport.footer'
  | 'transport.admin'
  | 'transport.departure'
  | 'transport.destination'
  | 'transport.allCities'
  | 'transport.travelDate'
  | 'transport.search'
  | 'transport.driver'
  | 'transport.remaining'
  | 'transport.perPerson'
  | 'transport.full'
  | 'nav.home'
  | 'nav.admin'
  | 'nav.voyageur'
  | 'nav.profile'
  | 'nav.trajets'
  | 'nav.reservations'
  | 'nav.payments'
  | 'nav.create'
  | 'nav.public'
  | 'nav.private'
  | 'nav.logout'
  | 'nav.voyageurArea'
  | 'nav.adminLogin'
  | 'nav.dashboard'
  | 'nav.voyageurs'
  | 'nav.agences'
  | 'nav.vehicules'
  | 'nav.indisponibilites'
  | 'common.back'
  | 'common.save'
  | 'common.create'
  | 'common.edit'
  | 'common.delete'
  | 'common.reset'
  | 'common.cancel'
  | 'common.confirm'
  | 'common.loading'
  | 'common.none'
  | 'common.trajetPlanned'
  | 'common.trajetFull'
  | 'common.trajetCanceled'
  | 'common.reservationPending'
  | 'common.reservationConfirmed'
  | 'common.reservationCanceled'
  | 'common.paymentPending'
  | 'common.paymentPaid'
  | 'common.paymentRefunded'
  | 'common.paymentCanceled'
  | 'common.allAgences'
  | 'common.profile'
  | 'common.refresh'
  | 'switcher.label'
  | 'switcher.fr'
  | 'switcher.en'
  | 'landing.eyebrow'
  | 'landing.title'
  | 'landing.lead'
  | 'landing.voyageurCta'
  | 'landing.voyageurCtaConnected'
  | 'landing.adminCta'
  | 'landing.testApi'
  | 'landing.loadingApi'
  | 'landing.apiSuccess'
  | 'landing.apiError'
  | 'landing.feature1.title'
  | 'landing.feature1.body'
  | 'landing.feature2.title'
  | 'landing.feature2.body'
  | 'landing.feature3.title'
  | 'landing.feature3.body'
  | 'landing.feature4.title'
  | 'landing.feature4.body'
  | 'landing.trajetPublic'
  | 'landing.trajetAvailable'
  | 'landing.reservation'
  | 'landing.reservationBody'
  | 'landing.simpleReservation'
  | 'landing.trajetPreviewLabel'
  | 'admin.login.heroTitle'
  | 'admin.login.heroSubtitle'
  | 'admin.login.formTitle'
  | 'admin.login.formSubtitle'
  | 'admin.login.email'
  | 'admin.login.password'
  | 'admin.login.submit'
  | 'admin.login.back'
  | 'admin.login.error'
  | 'admin.home.eyebrow'
  | 'admin.home.title'
  | 'admin.home.subtitle'
  | 'admin.home.loading'
  | 'admin.home.trajets'
  | 'admin.home.reservations'
  | 'admin.home.voyageurs'
  | 'admin.home.revenue'
  | 'admin.home.occupancy'
  | 'admin.home.occupancySub'
  | 'admin.home.resources'
  | 'admin.home.resourcesSub'
  | 'admin.home.empty'
  | 'admin.home.waiting'
  | 'admin.home.visible'
  | 'admin.home.validPayments'
  | 'admin.home.reservationsLoadError'
  | 'admin.home.dashboardLoadError'
  | 'admin.trajets.title'
  | 'admin.trajets.subtitle'
  | 'admin.voyageurs.title'
  | 'admin.voyageurs.subtitle'
  | 'admin.voyageurs.matricule'
  | 'admin.voyageurs.matriculeHint'
  | 'admin.voyageurs.type'
  | 'admin.voyageurs.typeGlobal'
  | 'admin.voyageurs.typeAgence'
  | 'admin.voyageurs.typeLibre'
  | 'admin.voyageurs.nom'
  | 'admin.voyageurs.prenom'
  | 'admin.voyageurs.email'
  | 'admin.voyageurs.agence'
  | 'admin.voyageurs.agenceNone'
  | 'admin.voyageurs.searchPlaceholder'
  | 'admin.voyageurs.filterAll'
  | 'admin.voyageurs.filterGlobal'
  | 'admin.voyageurs.filterAgence'
  | 'admin.voyageurs.filterLibre'
  | 'admin.voyageurs.empty'
  | 'admin.voyageurs.emptySub'
  | 'admin.voyageurs.restriction'
  | 'admin.voyageurs.emailMissing'
  | 'admin.voyageurs.agenceAll'
  | 'admin.voyageurs.balance'
  | 'admin.voyageurs.edit'
  | 'admin.voyageurs.delete'
  | 'admin.voyageurs.count'
  | 'admin.agences.title'
  | 'admin.agences.subtitle'
  | 'admin.vehicules.title'
  | 'admin.vehicules.subtitle'
  | 'admin.indisponibilites.title'
  | 'admin.indisponibilites.subtitle'
  | 'voyageur.home.title'
  | 'voyageur.home.subtitle'
  | 'voyageur.home.identification'
  | 'voyageur.home.loginHint'
  | 'voyageur.home.password'
  | 'voyageur.home.cta'
  | 'voyageur.home.invalid'
  | 'voyageur.home.global'
  | 'voyageur.home.agence'
  | 'voyageur.home.livre'
  | 'voyageur.home.tousAgences'
  | 'voyageur.create.title'
  | 'voyageur.create.subtitle'
  | 'voyageur.create.rappel'
  | 'voyageur.create.public'
  | 'voyageur.create.private'
  | 'voyageur.create.back'
  | 'voyageur.create.submit'
  | 'voyageur.create.mode'
  | 'voyageur.create.agence'
  | 'voyageur.create.vehicule'
  | 'voyageur.create.date'
  | 'voyageur.create.dateHint'
  | 'voyageur.create.time'
  | 'voyageur.create.trajetType'
  | 'voyageur.create.privatePassagers'
  | 'voyageur.create.passager2'
  | 'voyageur.create.passager3'
  | 'voyageur.create.passager4'
  | 'voyageur.create.noAgence'
  | 'voyageur.create.noVehicule'
  | 'voyageur.create.selectedAgence'
  | 'voyageur.create.bookingLimit'
  | 'voyageur.create.bookingLimitProfileError'
  | 'voyageur.create.ruleGlobal'
  | 'voyageur.create.ruleAgence'
  | 'voyageur.create.ruleLibre'
  | 'voyageur.create.noAgenceLoaded'
  | 'voyageur.create.noVehiculeLoaded'
  | 'voyageur.create.success'
  | 'voyageur.create.successPrivate'
  | 'voyageur.create.partialSuccess'
  | 'voyageur.public.title'
  | 'voyageur.public.subtitle'
  | 'voyageur.public.filter'
  | 'voyageur.public.search'
  | 'voyageur.public.create'
  | 'voyageur.public.join'
  | 'voyageur.public.modify'
  | 'voyageur.public.delete'
  | 'voyageur.public.myProfile'
  | 'voyageur.public.myTrajet'
  | 'voyageur.public.available'
  | 'voyageur.public.completion'
  | 'voyageur.public.none'
  | 'voyageur.public.organizer'
  | 'voyageur.public.status'
  | 'voyageur.public.type'
  | 'voyageur.public.passagers'
  | 'voyageur.public.price'
  | 'voyageur.public.registering'
  | 'voyageur.public.editTitle'
  | 'voyageur.public.editHint'
  | 'voyageur.public.noTrajets'
  | 'voyageur.public.searchPlaceholder'
  | 'voyageur.public.noTrajetPrompt'
  | 'voyageur.public.createBody'
  | 'voyageur.public.date'
  | 'voyageur.public.time'
  | 'voyageur.public.typeLabel'
  | 'voyageur.reservations.title'
  | 'voyageur.reservations.subtitle'
  | 'voyageur.reservations.createPublic'
  | 'voyageur.reservations.createPrivate'
  | 'voyageur.reservations.trajets'
  | 'voyageur.reservations.select'
  | 'voyageur.reservations.addPassager'
  | 'voyageur.reservations.noOrganized'
  | 'voyageur.reservations.edit'
  | 'voyageur.reservations.confirmation'
  | 'voyageur.reservations.passagers'
  | 'voyageur.reservations.empty'
  | 'voyageur.reservations.passager'
  | 'voyageur.reservations.trajet'
  | 'voyageur.reservations.reservation'
  | 'voyageur.reservations.payment'
  | 'voyageur.reservations.amount'
  | 'voyageur.reservations.pay'
  | 'voyageur.reservations.cancel'
  | 'voyageur.reservations.organizedTitle'
  | 'voyageur.reservations.organizedSub'
  | 'voyageur.reservations.chooseTrajet'
  | 'voyageur.reservations.inviteMatricule'
  | 'voyageur.reservations.noPassager'
  | 'voyageur.reservations.reservationLabel'
  | 'voyageur.reservations.paymentLabel'
  | 'voyageur.reservations.removePassager'
  | 'voyageur.reservations.emptyBody'
  | 'voyageur.payments.title'
  | 'voyageur.payments.subtitle'
  | 'voyageur.payments.reservations'
  | 'voyageur.payments.total'
  | 'voyageur.payments.paid'
  | 'voyageur.payments.pending'
  | 'voyageur.payments.refunded'
  | 'voyageur.payments.none'
  | 'voyageur.payments.notSet'
  | 'voyageur.profile.title'
  | 'voyageur.profile.subtitle'
  | 'voyageur.profile.createQuick'
  | 'voyageur.profile.publicTrajet'
  | 'voyageur.profile.privateTrajet'
  | 'voyageur.profile.quickPublic'
  | 'voyageur.profile.quickPrivate'
  | 'voyageur.profile.quickPublicBody'
  | 'voyageur.profile.quickPrivateBody'
  | 'voyageur.profile.explore'
  | 'voyageur.profile.followup'
  | 'voyageur.profile.finance'
  | 'voyageur.profile.refresh'
  | 'voyageur.profile.logout'
  | 'voyageur.profile.nom'
  | 'voyageur.profile.prenom'
  | 'voyageur.profile.email'
  | 'voyageur.profile.matricule'
  | 'voyageur.profile.type'
  | 'voyageur.profile.agence'
  | 'voyageur.profile.solde'
  | 'voyageur.profile.restrictionActive'
  | 'voyageur.profile.balancePending'
  | 'voyageur.profile.yes'
  | 'voyageur.profile.no'
  | 'voyageur.profile.exploreBody'
  | 'voyageur.profile.followupBody'
  | 'voyageur.profile.financeBody'
  | 'voyageur.payments.payment'
  | 'voyageur.payments.loading'
  | 'voyageur.payments.error'
  | 'voyageur.payments.noVoyageur'
  | 'voyageur.payments.idLabel'
  | 'voyageur.payments.noPayments'
  | 'voyageur.payments.loadingState'
  | 'voyageur.payments.errorState'
  | 'voyageur.payments.noVoyageurState'
  | 'admin.trajets.organizer'
  | 'admin.trajets.status'
  | 'admin.trajets.passagers'
  | 'admin.trajets.reservations'
  | 'admin.trajets.noReservations'
  | 'admin.trajets.hideDetails'
  | 'admin.trajets.showDetails'
  | 'admin.trajets.convert'
  | 'admin.trajets.convertSuccess'
  | 'admin.trajets.loadError'
  | 'admin.trajets.reservationsError'
  | 'admin.trajets.convertError'
  | 'admin.trajets.empty'
  | 'admin.indisponibilites.date'
  | 'admin.indisponibilites.reason'
  | 'admin.indisponibilites.agence'
  | 'admin.indisponibilites.noneGlobal'
  | 'admin.indisponibilites.global'
  | 'admin.indisponibilites.add'
  | 'admin.indisponibilites.reset'
  | 'admin.indisponibilites.reasonEmpty'
  | 'admin.indisponibilites.empty'
  | 'common.notProvided'
  | 'admin.home.allStatuses'
  | 'admin.home.trajetCount'
  | 'admin.home.agencesVisible'
  | 'admin.home.vehiculesVisible'
  | 'admin.home.completeTrajets'
  | 'admin.home.pendingReservations'
  | 'admin.login.globalBadge'
  | 'admin.login.agenceBadge'
  | 'admin.login.showPassword'
  | 'admin.login.hidePassword'
  | 'admin.voyageurs.searchNoResult'
  | 'admin.voyageurs.loadError'
  | 'admin.voyageurs.saveError'
  | 'admin.voyageurs.deleteError'
  | 'admin.voyageurs.updateSuccess'
  | 'admin.voyageurs.createSuccess'
  | 'admin.voyageurs.deleteSuccess'
  | 'admin.voyageurs.deleteConfirm'
  | 'admin.agences.name'
  | 'admin.agences.address'
  | 'admin.agences.opening'
  | 'admin.agences.closing'
  | 'admin.agences.trajetDuration'
  | 'admin.agences.breakDuration'
  | 'admin.agences.civilYear'
  | 'admin.agences.hours'
  | 'admin.agences.duration'
  | 'admin.agences.break'
  | 'admin.agences.year'
  | 'admin.agences.empty'
  | 'admin.agences.agenceAdminEditBlocked'
  | 'admin.agences.agenceAdminDeleteBlocked'
  | 'admin.agences.loadError'
  | 'admin.agences.saveError'
  | 'admin.agences.deleteError'
  | 'admin.agences.deleteConfirm'
  | 'admin.agences.updateSuccess'
  | 'admin.agences.createSuccess'
  | 'admin.agences.deleteSuccess'
  | 'admin.vehicules.name'
  | 'admin.vehicules.agence'
  | 'admin.vehicules.empty'
  | 'admin.vehicules.loadError'
  | 'admin.vehicules.saveError'
  | 'admin.vehicules.deleteError'
  | 'admin.vehicules.deleteConfirm'
  | 'admin.vehicules.updateSuccess'
  | 'admin.vehicules.createSuccess'
  | 'admin.vehicules.deleteSuccess'
  | 'admin.indisponibilites.loadError'
  | 'admin.indisponibilites.addSuccess'
  | 'admin.indisponibilites.addError'
  | 'admin.indisponibilites.deleteSuccess'
  | 'admin.indisponibilites.deleteError'
  | 'voyageur.home.matricule'
  | 'voyageur.home.matriculePlaceholder'
  | 'voyageur.home.showPassword'
  | 'voyageur.home.hidePassword'
  | 'voyageur.home.globalHint'
  | 'voyageur.home.agenceHint'
  | 'voyageur.home.livreHint'
  | 'voyageur.home.allAgences'
  | 'voyageur.profile.loadError'
  | 'voyageur.create.bookingLimitError'
  | 'voyageur.create.invalidType'
  | 'voyageur.create.createError'
  | 'voyageur.create.outstandingBalance'
  | 'voyageur.create.activeRestriction'
  | 'voyageur.create.slotTaken'
  | 'voyageur.create.agenceClosed'
  | 'voyageur.create.outOfHours'
  | 'voyageur.create.agenceOnly'
  | 'voyageur.create.internalError'
  | 'voyageur.create.addPassagersError'
  | 'voyageur.public.loadError'
  | 'voyageur.public.noVoyageur'
  | 'voyageur.public.joinBlocked'
  | 'voyageur.public.joinSuccess'
  | 'voyageur.public.joinError'
  | 'voyageur.public.modifyTooLate'
  | 'voyageur.public.updateSuccess'
  | 'voyageur.public.updateError'
  | 'voyageur.public.deleteConfirm'
  | 'voyageur.public.deleteSuccess'
  | 'voyageur.public.deleteError'
  | 'voyageur.reservations.reservationTitle'
  | 'voyageur.reservations.noVoyageur'
  | 'voyageur.reservations.loadError'
  | 'voyageur.reservations.privateOnly'
  | 'voyageur.reservations.passagersLoadError'
  | 'voyageur.reservations.updateSuccess'
  | 'voyageur.reservations.updateError'
  | 'voyageur.reservations.deleteSuccess'
  | 'voyageur.reservations.deleteError'
  | 'voyageur.reservations.passagerAdded'
  | 'voyageur.reservations.passagerAddError'
  | 'voyageur.reservations.passagerRemoved'
  | 'voyageur.reservations.passagerRemoveError'
  | 'voyageur.reservations.paymentSuccess'
  | 'voyageur.reservations.paymentError'
  | 'voyageur.reservations.reservationCanceled'
  | 'voyageur.reservations.reservationCancelError';

export type TranslationParams = Record<string, string | number>;

export const TRANSLATIONS: Record<Locale, Partial<Record<TranslationKey, string>>> = {
  fr: {
    'seo.description': 'Covoiturage entre villes belges : chauffeur identifié, quatre passagers maximum et 25 € par personne. Paiement simulé dans cette version académique.',
    'carousel.label': 'Voyager ensemble en Belgique',
    'carousel.description': 'carrousel',
    'carousel.slide': 'photographie',
    'carousel.position': 'Photographie {current} sur {total}',
    'carousel.previous': 'Photographie précédente',
    'carousel.next': 'Photographie suivante',
    'carousel.pause': 'Mettre le carrousel en pause',
    'carousel.play': 'Relancer le défilement automatique',
    'carousel.motionOff': 'Défilement automatique désactivé : mouvement réduit',
    'carousel.controls': 'Commandes du carrousel',
    'carousel.pagination': 'Choisir une photographie',
    'carousel.goTo': 'Afficher la photographie {current} sur {total}',
    'carousel.subtitle': 'Découvrez une nouvelle façon de voyager entre les villes belges.',
    'carousel.discover': 'Découvrir le fonctionnement',
    'carousel.illustration': 'Photos d’illustration · hors flotte réelle',
    'carousel.roadAlt': 'Berline compacte sur une route à la campagne',
    'carousel.familyAlt': 'Famille près d’une voiture compacte pendant un voyage',
    'carousel.sharedAlt': 'Conducteur et passager vus depuis l’arrière d’une voiture',
    'carousel.brusselsAlt': 'Voitures dans une rue résidentielle à Bruxelles',
    'benefits.label': 'Un voyage en toute simplicité',
    'benefits.driverTitle': 'Un chauffeur identifié',
    'benefits.driverBody': 'Vous savez qui conduit avant de réserver votre place.',
    'benefits.priceTitle': 'Un prix clair',
    'benefits.priceBody': '25 € par personne. Les places inoccupées ne vous sont pas facturées.',
    'benefits.simpleTitle': 'Une réservation suivie',
    'benefits.simpleBody': 'Retrouvez vos paiements et votre confirmation dans votre espace.',
    'design.bannerLabel': 'Votre espace Transport',
    'design.bannerTitle': 'Votre prochain départ commence ici.',
    'design.bannerSubtitle': 'Des villes qui se rapprochent, des voyages qui se partagent. Trouvez votre place et voyagez ensemble.',
    'design.adminSubtitle': 'Pilotez votre activité de transport et vos trajets, dans le périmètre de vos autorisations.',
    'design.carAlt': 'Illustration originale d’une voiture de covoiturage sur une route entre villes',
    'design.adminMenu': 'Menu d’administration',
    'transport.differentCities': 'Choisissez deux villes différentes : la ville de départ et la destination ne peuvent pas être identiques.',
    'vehicleChoice.placeholder': 'Sélectionner un véhicule',
    'vehicleChoice.hint': 'Véhicules disponibles de cette agence · 4 places clients, chauffeur exclu.',
    'vehicleChoice.loading': 'Chargement des véhicules de cette agence…',
    'vehicleChoice.chooseAgence': 'Choisissez une agence pour consulter ses véhicules disponibles.',
    'vehicleChoice.empty': 'Les ressources indisponibles restent exclues. Contactez votre agence pour disposer d’un véhicule.',
    'vehicleChoice.manage': 'Gérer les véhicules',
    'premium.adminAccess': 'Accès administrateur',
    'premium.global': 'Administration globale',
    'premium.local': 'Administration de mon agence',
    'premium.peopleRestrictions': 'Voyageurs · Restrictions et absences',
    'premium.tripsBookings': 'Trajets · Réservations',
    'premium.photoAlt': 'Automobile en déplacement sur une route, illustration de mobilité partagée',
    'ui.sessionExpired': 'Votre session a expiré. Reconnectez-vous pour continuer.',
    'ui.networkError': 'Connexion impossible. Vérifiez votre connexion puis réessayez.',
    'ui.accessDenied': 'Cette action n’est pas autorisée pour votre compte.',
    'ui.expired': 'Expirée',
    'ui.driverAuthorized': 'Chauffeur autorisé',
    'voyageur.create.driverNotice': 'Vous êtes le chauffeur de ce trajet : {name}. Proposez jusqu’à 4 places passagers à 25 € par personne.',
    'ui.restrictionHistory': 'Historique des restrictions',
    'ui.noRestrictions': 'Aucune restriction enregistrée.',
    'ui.active': 'Active',
    'ui.inactive': 'Inactive',
    'ui.unknownStart': 'Début historique non renseigné',
    'ui.reportAbsence': 'Déclarer une absence',
    'ui.absenceReason': 'Motif vérifié de l’absence injustifiée',
    'ui.saveReport': 'Enregistrer la déclaration',
    'ui.absenceReported': 'Absence déclarée le',
    'ui.antwerp': 'Anvers',
    'ui.skip': 'Aller au contenu',
    'ui.menu': 'Menu',
    'ui.mainNav': 'Navigation principale',
    'ui.searchNav': 'Rechercher un trajet',
    'ui.details': 'Voir le trajet',
    'ui.tripOverview': 'Votre trajet en un coup d’œil',
    'ui.vehicle': 'Véhicule',
    'ui.availability': 'Places clients disponibles',
    'ui.unitPrice': 'Prix par personne · une place',
    'ui.close': 'Fermer',
    'ui.cityMissing': 'Ville à préciser',
    'ui.pendingNote': 'Votre place sera confirmée après validation du paiement.',
    'ui.footerTagline': 'Le covoiturage belge, en toute simplicité.',
    'ui.illustration': 'Le covoiturage entre villes belges',
    'ui.loadError':
      'Les trajets ne sont pas disponibles pour le moment. Réessayez dans quelques instants.',
    'ui.loadCities': 'Les villes ne peuvent pas être chargées. Réessayez dans quelques instants.',
    'transport.reserve': 'Réserver',
    'transport.eyebrow': 'LE COVOITURAGE, À LA BELGE',
    'transport.together': 'Voyagez ensemble,',
    'transport.simply': 'simplement.',
    'transport.lead':
      'Trouvez votre prochain covoiturage entre Bruxelles et les villes belges, en quelques clics.',
    'transport.driverKnown': 'Un chauffeur identifié',
    'transport.maximum': '4 clients maximum',
    'transport.price': '25 € par personne',
    'transport.lessCars': 'Moins de voitures.',
    'transport.moreMeetings': 'Plus de rencontres.',
    'transport.startHere': 'Votre prochain départ commence ici.',
    'transport.onRoad': 'PRENEZ LA ROUTE',
    'transport.find': 'Trouvez votre prochain trajet',
    'transport.paymentDemo': '25 € / personne · paiement simulé',
    'transport.searching': 'Recherche des trajets…',
    'transport.retry': 'Réessayer',
    'transport.noRides': 'Aucun départ pour le moment',
    'transport.tryOther':
      'Essayez une autre ville ou une autre date. Les trajets publiés apparaîtront ici.',
    'transport.belgium': 'UNE BELGIQUE PLUS PROCHE',
    'transport.popular': 'Trajets populaires',
    'transport.suggestions': 'Des idées de recherche, selon les départs réellement publiés.',
    'transport.withYou': 'ON VOUS ACCOMPAGNE',
    'transport.how': 'Comment ça marche ?',
    'transport.findStep': 'Trouvez votre trajet',
    'transport.findBody':
      'Choisissez vos villes et votre date, puis découvrez le chauffeur et les places disponibles.',
    'transport.payStep': 'Réservez et payez',
    'transport.payBody':
      'Votre paiement simulé confirme une place disponible. Une demande impayée ne garantit pas la place.',
    'transport.travelStep': "Voyagez l'esprit libre",
    'transport.travelBody':
      'Retrouvez votre confirmation, vos prochains voyages et votre historique dans votre espace.',
    'transport.shareWheel': 'Envie de partager le volant ?',
    'transport.driverBody':
      'Les chauffeurs autorisés proposent leurs trajets, publics ou sur invitation.',
    'transport.myArea': 'Mon espace voyageur',
    'transport.footer': 'Le covoiturage entre villes belges.',
    'transport.admin': 'Administration',
    'transport.departure': 'Ville de départ',
    'transport.destination': 'Destination',
    'transport.allCities': 'Toutes les villes',
    'transport.travelDate': 'Date de voyage',
    'transport.search': 'Rechercher un trajet',
    'transport.driver': 'Votre chauffeur',
    'transport.remaining': 'Places passagers disponibles : {count} sur {maximum}',
    'transport.perPerson': '/ personne',
    'transport.full': 'Complet',
    'nav.home': 'Accueil',
    'nav.admin': 'Admin',
    'nav.voyageur': 'Espace voyageur',
    'nav.profile': 'Profil',
    'nav.trajets': 'Trajets',
    'nav.reservations': 'Reservations',
    'nav.payments': 'Paiements',
    'nav.create': 'Creer',
    'nav.public': 'Public',
    'nav.private': 'Prive',
    'nav.logout': 'Deconnexion',
    'nav.voyageurArea': 'Espace voyageur',
    'nav.adminLogin': 'Connexion admin',
    'nav.dashboard': 'Dashboard',
    'nav.voyageurs': 'Voyageurs',
    'nav.agences': 'Agences',
    'nav.vehicules': 'Vehicules',
    'nav.indisponibilites': 'Indisponibilités',
    'common.back': 'Retour',
    'common.save': 'Enregistrer',
    'common.create': 'Creer',
    'common.edit': 'Modifier',
    'common.delete': 'Supprimer',
    'common.reset': 'Reinitialiser',
    'common.cancel': 'Annuler',
    'common.confirm': 'Confirmer',
    'common.loading': 'Chargement...',
    'common.none': 'Aucun',
    'common.trajetPlanned': 'Planifie',
    'common.trajetFull': 'Complet',
    'common.trajetCanceled': 'Annule',
    'common.reservationPending': 'En attente',
    'common.reservationConfirmed': 'Confirmee',
    'common.reservationCanceled': 'Annulee',
    'common.paymentPending': 'En attente',
    'common.paymentPaid': 'Paye',
    'common.paymentRefunded': 'Rembourse',
    'common.paymentCanceled': 'Annule',
    'common.allAgences': 'Tous les agences',
    'common.profile': 'Profil',
    'common.refresh': 'Rafraichir',
    'switcher.label': 'Choix de langue',
    'switcher.fr': 'Francais',
    'switcher.en': 'English',
    'landing.eyebrow': 'Gestion du transport public et privé',
    'landing.title': 'GestionTransport',
    'landing.lead':
      'Une interface claire pour reserver, rejoindre un trajet, suivre les paiements et piloter les agences depuis un tableau de bord admin.',
    'landing.voyageurCta': 'Entrer avec un matricule',
    'landing.voyageurCtaConnected': 'Ouvrir mon espace',
    'landing.adminCta': 'Connexion admin',
    'landing.testApi': "Tester l'API",
    'landing.loadingApi': 'Verification de /api/trajets/public...',
    'landing.apiSuccess': 'Proxy OK - {count} trajet(s) public(s) recupere(s)',
    'landing.apiError': "Echec de l'appel API. Verifiez que le backend tourne sur le port 8080.",
    'landing.feature1.title': 'Reservations',
    'landing.feature1.body': 'Choix du vehicule, controle des disponibilites et suivi du statut.',
    'landing.feature2.title': 'Trajets publics et prives',
    'landing.feature2.body': 'Creation rapide avec des regles claires pour les participants.',
    'landing.feature3.title': 'Paiements',
    'landing.feature3.body': 'Vue propre des paiements, restrictions et confirmations.',
    'landing.feature4.title': 'Administration',
    'landing.feature4.body':
      'Indicateurs, voyageurs, agences, vehicules et indisponibilités au meme endroit.',
    'landing.trajetPublic': 'Trajet public',
    'landing.trajetAvailable': 'trajet(s) disponible(s)',
    'landing.reservation': 'Reservation',
    'landing.reservationBody': 'voyageur, vehicule, paiement',
    'landing.simpleReservation': 'Simple',
    'landing.trajetPreviewLabel': 'Apercu des trajets',
    'admin.login.heroTitle': 'GestionTransport Admin',
    'admin.login.heroSubtitle':
      'Espace reserve aux administrateurs de agences et superviseurs globaux',
    'admin.login.formTitle': 'Connexion',
    'admin.login.formSubtitle': 'Acces reserve aux administrateurs',
    'admin.login.email': 'Email',
    'admin.login.password': 'Mot de passe',
    'admin.login.submit': 'Se connecter',
    'admin.login.back': 'Retour',
    'admin.login.error': "Echec de connexion. Verifiez l'e-mail et le mot de passe.",
    'admin.home.eyebrow': 'Tableau de bord',
    'admin.home.title': 'Administration GestionTransport',
    'admin.home.subtitle':
      'Vue {scope} des trajets, reservations, voyageurs et ressources du réseau.',
    'admin.home.loading': 'Chargement du tableau de bord...',
    'admin.home.trajets': 'Trajets',
    'admin.home.reservations': 'Reservations',
    'admin.home.voyageurs': 'Voyageurs',
    'admin.home.revenue': "Chiffre d'affaires",
    'admin.home.occupancy': 'Occupation par agence',
    'admin.home.occupancySub': 'Nombre de trajets rattaches a chaque agence visible',
    'admin.home.resources': 'Ressources',
    'admin.home.resourcesSub': "Controle rapide des elements geres par l'administration",
    'admin.home.empty': 'Aucune donnee.',
    'admin.home.waiting': 'en attente',
    'admin.home.visible': 'visibles',
    'admin.home.validPayments': 'paiements valides',
    'admin.home.reservationsLoadError': 'Impossible de charger les reservations administrateur.',
    'admin.home.dashboardLoadError': 'Impossible de charger le tableau de bord administrateur.',
    'admin.trajets.title': 'Gestion des trajets',
    'admin.trajets.subtitle': 'Consultation, details et conversion en public',
    'admin.voyageurs.title': 'Gestion des voyageurs',
    'admin.voyageurs.subtitle': 'Creation, modification et suppression des voyageurs du réseau',
    'admin.voyageurs.matricule': 'Matricule',
    'admin.voyageurs.matriculeHint': 'VIP: V+4ch · LOCAL: L+5ch · LIBRE: F+5ch',
    'admin.voyageurs.type': 'Type de voyageur',
    'admin.voyageurs.typeGlobal': 'VIP - Tous les agences',
    'admin.voyageurs.typeAgence': 'Local - Agence dédié',
    'admin.voyageurs.typeLibre': 'Libre - Accès libre',
    'admin.voyageurs.nom': 'Nom',
    'admin.voyageurs.prenom': 'Prenom',
    'admin.voyageurs.email': 'Email',
    'admin.voyageurs.agence': 'Agence',
    'admin.voyageurs.agenceNone': 'Aucune agence (VIP / LIBRE)',
    'admin.voyageurs.searchPlaceholder': 'Rechercher par matricule, nom ou prenom...',
    'admin.voyageurs.filterAll': 'Tous',
    'admin.voyageurs.filterGlobal': 'VIP',
    'admin.voyageurs.filterAgence': 'Local',
    'admin.voyageurs.filterLibre': 'Libre',
    'admin.voyageurs.empty': 'Aucun voyageur trouve',
    'admin.voyageurs.emptySub': 'Creer votre premier voyageur avec le formulaire ci-dessus.',
    'admin.voyageurs.restriction': 'Pénalité active',
    'admin.voyageurs.emailMissing': 'Non renseigne',
    'admin.voyageurs.agenceAll': 'Tous les agences',
    'admin.voyageurs.balance': 'Solde',
    'admin.voyageurs.edit': 'Modifier',
    'admin.voyageurs.delete': 'Supprimer',
    'admin.voyageurs.count': 'voyageur(s)',
    'admin.agences.title': 'Gestion des agences',
    'admin.agences.subtitle': 'Creation, edition et suppression des agences',
    'admin.vehicules.title': 'Gestion des vehicules',
    'admin.vehicules.subtitle': 'Creation et gestion des vehicules par agence',
    'admin.indisponibilites.title': 'Indisponibilités de service',
    'admin.indisponibilites.subtitle': 'Gestion des indisponibilités globales et par agence',
    'voyageur.home.title': 'Espace Voyageur',
    'voyageur.home.subtitle': 'Entrez votre matricule pour acceder a votre espace voyageur',
    'voyageur.home.identification': 'Identification',
    'voyageur.home.loginHint':
      'Saisissez votre matricule et mot de passe pour rejoindre le vehicule',
    'voyageur.home.password': 'Mot de passe',
    'voyageur.home.cta': 'Acceder a mon espace',
    'voyageur.home.invalid': 'Identifiants invalides. Verifiez le matricule et le mot de passe.',
    'voyageur.home.global': 'VIP',
    'voyageur.home.agence': 'Local',
    'voyageur.home.livre': 'Libre',
    'voyageur.home.tousAgences': 'Tous les agences',
    'voyageur.create.title': 'Creer un trajet',
    'voyageur.create.subtitle': 'Public ou prive, selon les regles du backend.',
    'voyageur.create.rappel': 'Rappel',
    'voyageur.create.public': 'PUBLIC',
    'voyageur.create.private': 'PRIVE',
    'voyageur.create.back': 'Retour profil',
    'voyageur.create.submit': 'Creer le trajet',
    'voyageur.create.mode': 'Mode creation',
    'voyageur.create.agence': 'Agence',
    'voyageur.create.vehicule': 'Vehicule',
    'voyageur.create.date': 'Date',
    'voyageur.create.dateHint': 'Entre',
    'voyageur.create.time': 'Heure debut',
    'voyageur.create.trajetType': 'Type de trajet',
    'voyageur.create.privatePassagers': 'Passagers a inviter maintenant (optionnel)',
    'voyageur.create.passager2': 'Matricule client 1',
    'voyageur.create.passager3': 'Matricule client 2',
    'voyageur.create.passager4': 'Matricule client 3',
    'voyageur.create.noAgence': 'Aucune agence disponible pour creer un trajet.',
    'voyageur.create.noVehicule': 'Aucun vehicule disponible pour cette agence selectionne.',
    'voyageur.create.selectedAgence': 'Agence selectionne',
    'voyageur.create.bookingLimit': 'Derniere date autorisee selon votre profil :',
    'voyageur.create.ruleGlobal': "VIP : jusqu'a 3 semaines a l'avance",
    'voyageur.create.ruleAgence': "LOCAL : jusqu'a 2 semaines a l'avance sur son agence",
    'voyageur.create.ruleLibre': "LIBRE : jusqu'a 5 jours a l'avance",
    'voyageur.create.noAgenceLoaded': 'Aucune agence charge.',
    'voyageur.create.noVehiculeLoaded': 'Aucun vehicule charge.',
    'voyageur.create.success':
      'Trajet créé : le chauffeur est associé et quatre places passagers sont proposées.',
    'voyageur.create.successPrivate':
      'Trajet privé créé. Les invitations sont en attente de paiement des passagers.',
    'voyageur.create.partialSuccess':
      "Trajet cree, mais certains passagers n'ont pas pu etre ajoutes.",
    'voyageur.public.title': 'Trouvez votre prochain trajet',
    'voyageur.public.subtitle': 'Comparez les départs et réservez votre place facilement.',
    'voyageur.public.filter': 'Filtrer par agence',
    'voyageur.public.search': 'Recherche',
    'voyageur.public.create': 'Creer un trajet',
    'voyageur.public.join': 'Rejoindre',
    'voyageur.public.modify': 'Modifier',
    'voyageur.public.delete': 'Supprimer',
    'voyageur.public.myProfile': 'Mon profil',
    'voyageur.public.myTrajet': 'Mon trajet',
    'voyageur.public.available': 'Disponible',
    'voyageur.public.completion': 'Complet',
    'voyageur.public.none': 'Non rejoignable',
    'voyageur.public.organizer': 'Organisateur',
    'voyageur.public.status': 'Statut',
    'voyageur.public.type': 'Type',
    'voyageur.public.passagers': 'Passagers',
    'voyageur.public.price': 'Prix par passager',
    'voyageur.public.registering': 'Reservation...',
    'voyageur.public.editTitle': 'Modifier le trajet',
    'voyageur.public.editHint':
      'Modification indisponible : un trajet ne peut plus etre modifie a moins de 24 h du debut.',
    'voyageur.public.noTrajets': 'Aucun trajet public ne correspond aux filtres.',
    'voyageur.public.searchPlaceholder': 'Vehicule, agence, organisateur',
    'voyageur.public.noTrajetPrompt': 'Tu ne trouves pas de trajet ?',
    'voyageur.public.createBody':
      'En public ou en prive, avec choix de cette agence et du vehicule.',
    'voyageur.public.date': 'Date',
    'voyageur.public.time': 'Heure debut',
    'voyageur.public.typeLabel': 'Type',
    'voyageur.reservations.title': 'Mes reservations',
    'voyageur.reservations.subtitle': 'Paiement et annulation de vos inscriptions.',
    'voyageur.reservations.createPublic': 'Creer un trajet PUBLIC',
    'voyageur.reservations.createPrivate': 'Creer un trajet PRIVE',
    'voyageur.reservations.trajets': 'Trajets publics',
    'voyageur.reservations.select': 'Selectionner un trajet organise',
    'voyageur.reservations.addPassager': 'Ajouter passager',
    'voyageur.reservations.noOrganized':
      "Aucun trajet organise pour l'instant. Creez un trajet PUBLIC ou PRIVE via les boutons ci-dessus.",
    'voyageur.reservations.edit': 'Modifier le trajet',
    'voyageur.reservations.confirmation': 'Confirmation requise',
    'voyageur.reservations.passagers': 'Passagers inscrits :',
    'voyageur.reservations.empty': 'Aucune reservation trouvee.',
    'voyageur.reservations.passager': 'Passager',
    'voyageur.reservations.trajet': 'Trajet',
    'voyageur.reservations.reservation': 'Reservation',
    'voyageur.reservations.payment': 'Paiement',
    'voyageur.reservations.amount': 'Montant',
    'voyageur.reservations.pay': 'Payer',
    'voyageur.reservations.cancel': 'Annuler',
    'voyageur.reservations.organizedTitle': 'Mes trajets organises',
    'voyageur.reservations.organizedSub':
      'Modifier, supprimer ou gerer les passagers de vos trajets.',
    'voyageur.reservations.chooseTrajet': '-- Choisir un trajet --',
    'voyageur.reservations.inviteMatricule': 'Matricule passager a ajouter',
    'voyageur.reservations.noPassager': 'Aucun passager inscrit sur ce trajet.',
    'voyageur.reservations.reservationLabel': 'Reservation',
    'voyageur.reservations.paymentLabel': 'Paiement',
    'voyageur.reservations.removePassager': 'Retirer',
    'voyageur.reservations.emptyBody':
      'Creez un trajet ou rejoignez un trajet public pour voir vos reservations ici.',
    'voyageur.payments.title': 'Mes paiements',
    'voyageur.payments.subtitle': 'Historique des paiements et remboursements',
    'voyageur.payments.reservations': 'Mes reservations',
    'voyageur.payments.total': 'Total paiements',
    'voyageur.payments.paid': 'Total paye',
    'voyageur.payments.pending': 'En attente',
    'voyageur.payments.refunded': 'Rembourses',
    'voyageur.payments.none': 'Aucun paiement trouve',
    'voyageur.payments.notSet': 'Pas encore regle',
    'voyageur.payments.idLabel': 'Paiement',
    'voyageur.payments.noPayments': 'Aucun paiement trouve',
    'voyageur.payments.loadingState': 'Chargement des paiements...',
    'voyageur.payments.errorState': 'Impossible de charger les paiements.',
    'voyageur.payments.noVoyageurState': 'Aucun voyageur connecte.',
    'voyageur.profile.title': 'Mon profil voyageur',
    'voyageur.profile.subtitle': 'Informations rechargees depuis le backend',
    'voyageur.profile.createQuick': 'Creer un trajet rapidement',
    'voyageur.profile.publicTrajet': 'Trajet ouvert',
    'voyageur.profile.privateTrajet': 'Trajet sur invitation',
    'voyageur.profile.quickPublic': 'Creer un trajet PUBLIC',
    'voyageur.profile.quickPrivate': 'Creer un trajet PRIVE',
    'voyageur.profile.quickPublicBody':
      'Voyageurs autorisés · 25 EUR/client · quatre places clients maximum',
    'voyageur.profile.quickPrivateBody':
      'Invitez jusqu’à quatre clients · Publication à H-24 uniquement avec votre consentement',
    'voyageur.profile.explore': 'Explorer',
    'voyageur.profile.followup': 'Suivi',
    'voyageur.profile.finance': 'Finances',
    'voyageur.profile.refresh': 'Rafraichir',
    'voyageur.profile.logout': 'Deconnexion voyageur',
    'voyageur.profile.nom': 'Nom',
    'voyageur.profile.prenom': 'Prenom',
    'voyageur.profile.email': 'Email',
    'voyageur.profile.matricule': 'Matricule',
    'voyageur.profile.type': 'Type',
    'voyageur.profile.agence': 'Agence',
    'voyageur.profile.solde': 'Solde',
    'voyageur.profile.restrictionActive': 'Restriction active',
    'voyageur.profile.balancePending': 'Solde en attente',
    'voyageur.profile.yes': 'Oui',
    'voyageur.profile.no': 'Non',
    'voyageur.profile.exploreBody': 'Rejoins rapidement une partie disponible.',
    'voyageur.profile.followupBody': 'Paie, annule ou suis tes inscriptions.',
    'voyageur.profile.financeBody': 'Historique de tes paiements.',
    'voyageur.payments.payment': 'Paiement',
    'voyageur.payments.loading': 'Chargement des paiements...',
    'voyageur.payments.error': 'Impossible de charger les paiements.',
    'voyageur.payments.noVoyageur': 'Aucun voyageur connecte.',
    'admin.trajets.organizer': 'Organisateur',
    'admin.trajets.status': 'Statut',
    'admin.trajets.passagers': 'Passagers',
    'admin.trajets.reservations': 'Reservations du trajet',
    'admin.trajets.noReservations': 'Aucune reservation.',
    'admin.trajets.hideDetails': 'Masquer',
    'admin.trajets.showDetails': 'Voir details',
    'admin.trajets.convert': 'Convertir en public',
    'admin.trajets.convertSuccess': 'Trajet converti en public.',
    'admin.trajets.loadError': 'Impossible de charger les trajets admin.',
    'admin.trajets.reservationsError': 'Impossible de charger les reservations du trajet.',
    'admin.trajets.convertError': 'Conversion impossible.',
    'admin.trajets.empty': 'Aucun trajet disponible',
    'admin.indisponibilites.date': 'Date',
    'admin.indisponibilites.reason': 'Raison (optionnel)',
    'admin.indisponibilites.agence': 'Agence concerne',
    'admin.indisponibilites.noneGlobal': 'Aucun (global)',
    'admin.indisponibilites.global': 'Indisponibilité globale (toutes les agences)',
    'admin.indisponibilites.add': 'Ajouter la indisponibilité',
    'admin.indisponibilites.reset': 'Reinitialiser',
    'admin.indisponibilites.reasonEmpty': 'Raison non precisee',
    'admin.indisponibilites.empty': 'Aucun indisponibilité de service configure',
    'common.notProvided': 'Non renseigne',
    'admin.home.allStatuses': 'tous statuts',
    'admin.home.trajetCount': 'trajet(s)',
    'admin.home.agencesVisible': 'Agences visibles',
    'admin.home.vehiculesVisible': 'Vehicules visibles',
    'admin.home.completeTrajets': 'Trajets complets',
    'admin.home.pendingReservations': 'Reservations en attente',
    'admin.login.globalBadge': '🌐 Admin global',
    'admin.login.agenceBadge': '🏢 Admin agence',
    'admin.login.showPassword': 'Afficher le mot de passe admin',
    'admin.login.hidePassword': 'Masquer le mot de passe admin',
    'admin.voyageurs.searchNoResult': 'Aucun resultat pour "{query}"',
    'admin.voyageurs.loadError': 'Impossible de charger les voyageurs.',
    'admin.voyageurs.saveError': 'Sauvegarde du voyageur impossible.',
    'admin.voyageurs.deleteError': 'Suppression impossible.',
    'admin.voyageurs.updateSuccess': 'Voyageur mis à jour.',
    'admin.voyageurs.createSuccess': 'Voyageur cree avec succes.',
    'admin.voyageurs.deleteSuccess': 'Voyageur supprimé.',
    'admin.voyageurs.deleteConfirm': 'Confirmer la suppression de ce voyageur ?',
    'admin.agences.name': 'Nom',
    'admin.agences.address': 'Adresse',
    'admin.agences.opening': 'Ouverture',
    'admin.agences.closing': 'Fermeture',
    'admin.agences.trajetDuration': 'Duree trajet (min)',
    'admin.agences.breakDuration': 'Pause entre trajets (min)',
    'admin.agences.civilYear': 'Annee civile',
    'admin.agences.hours': 'Horaires',
    'admin.agences.duration': 'Duree trajet',
    'admin.agences.break': 'Pause',
    'admin.agences.year': 'Annee',
    'admin.agences.empty': 'Aucune agence configure',
    'admin.agences.agenceAdminEditBlocked': 'Un admin AGENCE ne peut pas modifier les agences.',
    'admin.agences.agenceAdminDeleteBlocked': 'Un admin AGENCE ne peut pas supprimer de agence.',
    'admin.agences.loadError': 'Impossible de charger les agences.',
    'admin.agences.saveError': 'Sauvegarde de cette agence impossible.',
    'admin.agences.deleteError': 'Suppression de cette agence impossible.',
    'admin.agences.deleteConfirm': 'Confirmer la suppression de ce agence ?',
    'admin.agences.updateSuccess': 'Agence mis a jour.',
    'admin.agences.createSuccess': 'Agence cree.',
    'admin.agences.deleteSuccess': 'Agence supprime.',
    'admin.vehicules.name': 'Nom du vehicule',
    'admin.vehicules.agence': 'Agence',
    'admin.vehicules.empty': 'Aucun vehicule configure',
    'admin.vehicules.loadError': 'Impossible de charger les vehicules.',
    'admin.vehicules.saveError': 'Sauvegarde du vehicule impossible.',
    'admin.vehicules.deleteError': 'Suppression du vehicule impossible.',
    'admin.vehicules.deleteConfirm': 'Confirmer la suppression de ce vehicule ?',
    'admin.vehicules.updateSuccess': 'Vehicule mis a jour.',
    'admin.vehicules.createSuccess': 'Vehicule cree.',
    'admin.vehicules.deleteSuccess': 'Vehicule supprime.',
    'admin.indisponibilites.loadError': 'Impossible de charger les indisponibilités de service.',
    'admin.indisponibilites.addSuccess': 'Jour de indisponibilité ajoute.',
    'admin.indisponibilites.addError': 'Ajout impossible.',
    'admin.indisponibilites.deleteSuccess': 'Jour de indisponibilité supprime.',
    'admin.indisponibilites.deleteError': 'Suppression impossible.',
    'voyageur.home.matricule': 'Matricule',
    'voyageur.home.matriculePlaceholder': 'Ex: V1234, L12345, F12345',
    'voyageur.home.showPassword': 'Afficher le mot de passe voyageur',
    'voyageur.home.hidePassword': 'Masquer le mot de passe voyageur',
    'voyageur.home.globalHint': 'V + 4 chiffres · Tous les agences',
    'voyageur.home.agenceHint': 'L + 5 chiffres · Agence dédié',
    'voyageur.home.livreHint': 'F + 5 chiffres · Accès libre',
    'voyageur.home.allAgences': 'Tous les agences',
    'voyageur.profile.loadError': 'Impossible de charger le profil voyageur.',
    'voyageur.create.bookingLimitError': 'Dernière date autorisée : {date}.',
    'voyageur.create.bookingLimitProfileError':
      "Le profil {type} ne peut pas réserver plus de {days} jours à l'avance. Date limite : {date}.",
    'voyageur.create.invalidType': 'Type de trajet invalide.',
    'voyageur.create.createError': 'Creation du trajet impossible.',
    'voyageur.create.outstandingBalance':
      'Vous avez un solde impaye. Reglez vos dettes avant de creer un trajet.',
    'voyageur.create.activeRestriction':
      'Vous avez une restriction active. Attendez avant de creer un trajet.',
    'voyageur.create.slotTaken': 'Ce creneau est deja reserve. Choisissez un autre horaire.',
    'voyageur.create.agenceClosed': 'Le agence est ferme a cette date.',
    'voyageur.create.outOfHours':
      "Cet horaire est en dehors des heures d'ouverture de cette agence.",
    'voyageur.create.agenceOnly':
      'Un voyageur LOCAL ne peut creer un trajet que sur sa propre agence.',
    'voyageur.create.internalError':
      'Une erreur interne du serveur est survenue lors de la creation du trajet.',
    'voyageur.create.addPassagersError': "Impossible d'ajouter tous les passagers.",
    'voyageur.public.loadError': 'Impossible de charger les trajets publics.',
    'voyageur.public.noVoyageur': 'Aucun voyageur connecte.',
    'voyageur.public.joinBlocked':
      'Ce trajet ne peut pas etre rejoint (complet, annule ou non autorise).',
    'voyageur.public.joinSuccess':
      "Demande créée. Votre place ne sera confirmée qu'après paiement.",
    'voyageur.public.joinError': 'Impossible de reserver ce trajet.',
    'voyageur.public.modifyTooLate': 'Modification impossible a moins de 24 h du debut du trajet.',
    'voyageur.public.updateSuccess': 'Trajet modifie avec succes.',
    'voyageur.public.updateError': 'Modification du trajet impossible.',
    'voyageur.public.deleteConfirm': 'Confirmer la suppression (annulation) du trajet #{id} ?',
    'voyageur.public.deleteSuccess': 'Trajet annule avec succes.',
    'voyageur.public.deleteError': 'Suppression du trajet impossible.',
    'voyageur.reservations.reservationTitle': 'Reservation',
    'voyageur.reservations.noVoyageur': 'Aucun voyageur connecte.',
    'voyageur.reservations.loadError': 'Impossible de charger les reservations.',
    'voyageur.reservations.privateOnly':
      'La gestion des passagers est réservée aux trajets PRIVÉS.',
    'voyageur.reservations.passagersLoadError': 'Impossible de charger les passagers du trajet.',
    'voyageur.reservations.updateSuccess': 'Trajet mis à jour avec succès.',
    'voyageur.reservations.updateError': 'Modification du trajet impossible.',
    'voyageur.reservations.deleteSuccess': 'Trajet annule avec succes.',
    'voyageur.reservations.deleteError': 'Suppression du trajet impossible.',
    'voyageur.reservations.passagerAdded': 'Passager ajoute avec succes.',
    'voyageur.reservations.passagerAddError': 'Ajout du passager impossible.',
    'voyageur.reservations.passagerRemoved': 'Passager retire du trajet.',
    'voyageur.reservations.passagerRemoveError': 'Suppression du passager impossible.',
    'voyageur.reservations.paymentSuccess': 'Paiement effectue avec succes.',
    'voyageur.reservations.paymentError': 'Paiement impossible.',
    'voyageur.reservations.reservationCanceled': 'Reservation annulee.',
    'voyageur.reservations.reservationCancelError': 'Annulation impossible.',
  },
  en: {
    'seo.description': 'Carpooling between Belgian cities: an identified driver, up to four passengers and €25 per person. Simulated payments in this academic version.',
    'carousel.label': 'Travelling together in Belgium',
    'carousel.description': 'carousel',
    'carousel.slide': 'photograph',
    'carousel.position': 'Photograph {current} of {total}',
    'carousel.previous': 'Previous photograph',
    'carousel.next': 'Next photograph',
    'carousel.pause': 'Pause the carousel',
    'carousel.play': 'Resume automatic rotation',
    'carousel.motionOff': 'Automatic rotation disabled: reduced motion',
    'carousel.controls': 'Carousel controls',
    'carousel.pagination': 'Choose a photograph',
    'carousel.goTo': 'Show photograph {current} of {total}',
    'carousel.subtitle': 'Discover a new way to travel between Belgian cities.',
    'carousel.discover': 'See how it works',
    'carousel.illustration': 'Illustrative photos · not the actual fleet',
    'carousel.roadAlt': 'Compact saloon on a country road',
    'carousel.familyAlt': 'Family beside a compact car during a journey',
    'carousel.sharedAlt': 'Driver and passenger viewed from the back of a car',
    'carousel.brusselsAlt': 'Cars on a residential street in Brussels',
    'benefits.label': 'A simpler way to travel',
    'benefits.driverTitle': 'An identified driver',
    'benefits.driverBody': 'Know who is driving before you book your seat.',
    'benefits.priceTitle': 'A clear price',
    'benefits.priceBody': '€25 per person. You are not charged for unoccupied seats.',
    'benefits.simpleTitle': 'An easy-to-follow booking',
    'benefits.simpleBody': 'Find your payments and confirmation in your personal area.',
    'design.bannerLabel': 'Your Transport space',
    'design.bannerTitle': 'Your next journey starts here.',
    'design.bannerSubtitle': 'Connecting cities, sharing journeys. Find your seat and travel together.',
    'design.adminSubtitle': 'Manage your agencies, vehicles and trips in one place, within your permissions.',
    'design.carAlt': 'Original illustration of a shared car on a road between cities',
    'design.adminMenu': 'Administration menu',
    'transport.differentCities': 'Choose two different cities: departure and destination cannot be the same.',
    'vehicleChoice.placeholder': 'Select a vehicle',
    'vehicleChoice.hint': 'Available vehicles from this agency · 4 passenger places, excluding the driver.',
    'vehicleChoice.loading': 'Loading vehicles for this agency…',
    'vehicleChoice.chooseAgence': 'Choose an agency to view its available vehicles.',
    'vehicleChoice.empty': 'Unavailable resources remain excluded. Contact your agency to arrange a vehicle.',
    'vehicleChoice.manage': 'Manage vehicles',
    'premium.adminAccess': 'Administrator access',
    'premium.global': 'Global administration',
    'premium.local': 'My agency administration',
    'premium.peopleRestrictions': 'Passengers · Restrictions and absences',
    'premium.tripsBookings': 'Trips · Bookings',
    'premium.photoAlt': 'Car travelling on a road, illustration of shared mobility',
    'ui.sessionExpired': 'Your session has expired. Sign in again to continue.',
    'ui.networkError': 'Unable to connect. Check your connection and try again.',
    'ui.accessDenied': 'This action is not allowed for your account.',
    'ui.expired': 'Expired',
    'ui.driverAuthorized': 'Authorized driver',
    'voyageur.create.driverNotice': 'You are the driver for this ride: {name}. Offer up to 4 passenger seats at €25 per person.',
    'ui.restrictionHistory': 'Restriction history',
    'ui.noRestrictions': 'No restrictions recorded.',
    'ui.active': 'Active',
    'ui.inactive': 'Inactive',
    'ui.unknownStart': 'Historical start date unknown',
    'ui.reportAbsence': 'Report an absence',
    'ui.absenceReason': 'Verified reason for the unjustified absence',
    'ui.saveReport': 'Save report',
    'ui.absenceReported': 'Absence reported on',
    'ui.antwerp': 'Antwerp',
    'ui.skip': 'Skip to content',
    'ui.menu': 'Menu',
    'ui.mainNav': 'Main navigation',
    'ui.searchNav': 'Find a ride',
    'ui.details': 'View ride',
    'ui.tripOverview': 'Your ride at a glance',
    'ui.vehicle': 'Vehicle',
    'ui.availability': 'Available passenger places',
    'ui.unitPrice': 'Price per person · one place',
    'ui.close': 'Close',
    'ui.cityMissing': 'City not specified',
    'ui.pendingNote': 'Your place is confirmed after payment is validated.',
    'ui.footerTagline': 'Belgian ridesharing, made simple.',
    'ui.illustration': 'Ridesharing between Belgian cities',
    'ui.loadError': 'Rides are temporarily unavailable. Please try again shortly.',
    'ui.loadCities': 'Cities could not be loaded. Please try again shortly.',
    'transport.reserve': 'Book',
    'transport.eyebrow': 'RIDESHARING, THE BELGIAN WAY',
    'transport.together': 'Travel together,',
    'transport.simply': 'simply.',
    'transport.lead':
      'Find your next shared ride between Brussels and Belgian cities in just a few clicks.',
    'transport.driverKnown': 'An identified driver',
    'transport.maximum': '4 clients maximum',
    'transport.price': '€25 per person',
    'transport.lessCars': 'Fewer cars.',
    'transport.moreMeetings': 'More connections.',
    'transport.startHere': 'Your next journey starts here.',
    'transport.onRoad': 'HIT THE ROAD',
    'transport.find': 'Find your next ride',
    'transport.paymentDemo': '€25 / person · simulated payment',
    'transport.searching': 'Searching for rides…',
    'transport.retry': 'Try again',
    'transport.noRides': 'No rides at the moment',
    'transport.tryOther': 'Try another city or date. Published rides will appear here.',
    'transport.belgium': 'BRINGING BELGIUM CLOSER',
    'transport.popular': 'Popular journeys',
    'transport.suggestions': 'Search suggestions, subject to published departures.',
    'transport.withYou': 'HERE TO HELP',
    'transport.how': 'How does it work?',
    'transport.findStep': 'Find your ride',
    'transport.findBody':
      'Choose your cities and date, then discover the driver and available places.',
    'transport.payStep': 'Book and pay',
    'transport.payBody':
      'Your simulated payment confirms an available place. An unpaid request does not guarantee a place.',
    'transport.travelStep': 'Travel with peace of mind',
    'transport.travelBody':
      'Find your confirmation, upcoming journeys and history in your account.',
    'transport.shareWheel': 'Want to offer a ride?',
    'transport.driverBody': 'Authorized drivers offer public or invitation-only rides.',
    'transport.myArea': 'My passenger account',
    'transport.footer': 'Ridesharing between Belgian cities.',
    'transport.admin': 'Administration',
    'transport.departure': 'Departure city',
    'transport.destination': 'Destination',
    'transport.allCities': 'All cities',
    'transport.travelDate': 'Travel date',
    'transport.search': 'Search for a ride',
    'transport.driver': 'Your driver',
    'transport.remaining': 'Passenger seats available: {count} of {maximum}',
    'transport.perPerson': '/ person',
    'transport.full': 'Full',
    'common.back': 'Back',
    'common.save': 'Save',
    'common.create': 'Create',
    'common.edit': 'Edit',
    'common.delete': 'Delete',
    'common.reset': 'Reset',
    'common.cancel': 'Cancel',
    'common.confirm': 'Confirm',
    'common.loading': 'Loading...',
    'common.none': 'None',
    'common.trajetPlanned': 'Planned',
    'common.trajetFull': 'Full',
    'common.trajetCanceled': 'Canceled',
    'common.reservationPending': 'Pending',
    'common.reservationConfirmed': 'Confirmed',
    'common.reservationCanceled': 'Canceled',
    'common.paymentPending': 'Pending',
    'common.paymentPaid': 'Paid',
    'common.paymentRefunded': 'Refunded',
    'common.paymentCanceled': 'Canceled',
    'common.allAgences': 'All agencies',
    'common.profile': 'Profile',
    'common.refresh': 'Refresh',
    'nav.home': 'Home',
    'nav.admin': 'Admin',
    'nav.voyageur': 'Passenger area',
    'nav.profile': 'Profile',
    'nav.trajets': 'Trips',
    'nav.reservations': 'Reservations',
    'nav.payments': 'Payments',
    'nav.create': 'Create',
    'nav.public': 'Public',
    'nav.private': 'Private',
    'nav.logout': 'Logout',
    'nav.voyageurArea': 'Passenger area',
    'nav.adminLogin': 'Admin login',
    'nav.dashboard': 'Dashboard',
    'nav.voyageurs': 'Passengers',
    'nav.agences': 'Agencies',
    'nav.vehicules': 'Vehicles',
    'nav.indisponibilites': 'Service unavailability',
    'switcher.label': 'Language switch',
    'switcher.fr': 'French',
    'switcher.en': 'English',
    'landing.eyebrow': 'Public and private transport management',
    'landing.title': 'GestionTransport',
    'landing.lead':
      'A clear interface to book, join a trip, track payments, and run the network from an admin dashboard.',
    'landing.voyageurCta': 'Enter with a passenger ID',
    'landing.voyageurCtaConnected': 'Open my area',
    'landing.adminCta': 'Admin login',
    'landing.testApi': 'Test the API',
    'landing.loadingApi': 'Checking /api/trajets/public...',
    'landing.apiSuccess': 'Proxy OK - {count} public trips fetched',
    'landing.apiError': 'API call failed. Check that the backend is running on port 8080.',
    'landing.feature1.title': 'Reservations',
    'landing.feature1.body': 'Vehicle selection, availability checks, and status tracking.',
    'landing.feature2.title': 'Public and private trips',
    'landing.feature2.body': 'Fast creation with clear rules for participants.',
    'landing.feature3.title': 'Payments',
    'landing.feature3.body': 'Clean view of payments, restrictions, and confirmations.',
    'landing.feature4.title': 'Administration',
    'landing.feature4.body':
      'Metrics, passengers, agencies, vehicles, and service unavailability in one place.',
    'landing.trajetPublic': 'Public trip',
    'landing.trajetAvailable': 'trips available',
    'landing.reservation': 'Reservation',
    'landing.reservationBody': 'passenger, vehicle, payment',
    'landing.simpleReservation': 'Simple',
    'landing.trajetPreviewLabel': 'Trip preview',
    'admin.login.heroTitle': 'GestionTransport Admin',
    'admin.login.heroSubtitle': 'Area reserved for agency administrators and global supervisors',
    'admin.login.formTitle': 'Login',
    'admin.login.formSubtitle': 'Access reserved for administrators',
    'admin.login.email': 'Email',
    'admin.login.password': 'Password',
    'admin.login.submit': 'Sign in',
    'admin.login.back': 'Back',
    'admin.login.error': 'Login failed. Check the email and password.',
    'admin.home.eyebrow': 'Dashboard',
    'admin.home.title': 'GestionTransport administration',
    'admin.home.subtitle':
      '{scope} view of network trips, reservations, passengers, and resources.',
    'admin.home.loading': 'Loading dashboard...',
    'admin.home.trajets': 'Trips',
    'admin.home.reservations': 'Reservations',
    'admin.home.voyageurs': 'Passengers',
    'admin.home.revenue': 'Revenue',
    'admin.home.occupancy': 'Occupancy by agency',
    'admin.home.occupancySub': 'Number of trips linked to each visible agency',
    'admin.home.resources': 'Resources',
    'admin.home.resourcesSub': 'Quick control of elements managed by the administration',
    'admin.home.empty': 'No data.',
    'admin.home.waiting': 'waiting',
    'admin.home.visible': 'visible',
    'admin.home.validPayments': 'valid payments',
    'admin.home.reservationsLoadError': 'Unable to load admin reservations.',
    'admin.home.dashboardLoadError': 'Unable to load the admin dashboard.',
    'admin.trajets.title': 'Trip management',
    'admin.trajets.subtitle': 'Review, details, and conversion to public',
    'admin.voyageurs.title': 'Passenger management',
    'admin.voyageurs.subtitle': 'Create, edit, and delete network passengers',
    'admin.voyageurs.matricule': 'Passenger ID',
    'admin.voyageurs.matriculeHint': 'VIP: V+4 chars · LOCAL: L+5 chars · LIBRE: F+5 chars',
    'admin.voyageurs.type': 'Passenger type',
    'admin.voyageurs.typeGlobal': 'VIP - All agencies',
    'admin.voyageurs.typeAgence': 'Local - Dedicated agency',
    'admin.voyageurs.typeLibre': 'Libre - Free access',
    'admin.voyageurs.nom': 'Last name',
    'admin.voyageurs.prenom': 'First name',
    'admin.voyageurs.email': 'Email',
    'admin.voyageurs.agence': 'Agency',
    'admin.voyageurs.agenceNone': 'No agency (VIP / LIBRE)',
    'admin.voyageurs.searchPlaceholder': 'Search by passenger ID, last name, or first name...',
    'admin.voyageurs.filterAll': 'All',
    'admin.voyageurs.filterGlobal': 'VIP',
    'admin.voyageurs.filterAgence': 'Local',
    'admin.voyageurs.filterLibre': 'Free access',
    'admin.voyageurs.empty': 'No passenger found',
    'admin.voyageurs.emptySub': 'Create your first passenger with the form above.',
    'admin.voyageurs.restriction': 'Active restriction',
    'admin.voyageurs.emailMissing': 'Not set',
    'admin.voyageurs.agenceAll': 'All agencies',
    'admin.voyageurs.balance': 'Balance',
    'admin.voyageurs.edit': 'Edit',
    'admin.voyageurs.delete': 'Delete',
    'admin.voyageurs.count': 'passenger(s)',
    'admin.agences.title': 'Agency management',
    'admin.agences.subtitle': 'Create, edit, and delete agencies',
    'admin.vehicules.title': 'Vehicle management',
    'admin.vehicules.subtitle': 'Create and manage vehicles by agency',
    'admin.indisponibilites.title': 'Indisponibilite days',
    'admin.indisponibilites.subtitle': 'Global and agency-based indisponibilite management',
    'voyageur.home.title': 'Passenger area',
    'voyageur.home.subtitle': 'Sign in to manage your journeys and reservations',
    'voyageur.home.identification': 'Identification',
    'voyageur.home.loginHint': 'Enter your passenger ID and password to join the vehicle',
    'voyageur.home.password': 'Password',
    'voyageur.home.cta': 'Open my area',
    'voyageur.home.invalid': 'Invalid credentials. Check the passenger ID and password.',
    'voyageur.home.global': 'VIP',
    'voyageur.home.agence': 'Local',
    'voyageur.home.livre': 'Free access',
    'voyageur.home.tousAgences': 'All agencies',
    'voyageur.create.title': 'Create a trip',
    'voyageur.create.subtitle': 'Public or private, according to backend rules.',
    'voyageur.create.rappel': 'Reminder',
    'voyageur.create.public': 'PUBLIC',
    'voyageur.create.private': 'PRIVATE',
    'voyageur.create.back': 'Back to profile',
    'voyageur.create.submit': 'Create trip',
    'voyageur.create.mode': 'Creation mode',
    'voyageur.create.agence': 'Agency',
    'voyageur.create.vehicule': 'Vehicle',
    'voyageur.create.date': 'Date',
    'voyageur.create.dateHint': 'Between',
    'voyageur.create.time': 'Start time',
    'voyageur.create.trajetType': 'Trip type',
    'voyageur.create.privatePassagers': 'Passengers to invite now (optional)',
    'voyageur.create.passager2': 'Client ID 1',
    'voyageur.create.passager3': 'Client ID 2',
    'voyageur.create.passager4': 'Client ID 3',
    'voyageur.create.noAgence': 'No agency available to create a trip.',
    'voyageur.create.noVehicule': 'No vehicle available for the selected agency.',
    'voyageur.create.selectedAgence': 'Selected agency',
    'voyageur.create.bookingLimit': 'Latest allowed date according to your profile:',
    'voyageur.create.ruleGlobal': 'VIP: up to 3 weeks ahead',
    'voyageur.create.ruleAgence': 'LOCAL: up to 2 weeks ahead on your agency',
    'voyageur.create.ruleLibre': 'LIBRE: up to 5 days ahead',
    'voyageur.create.noAgenceLoaded': 'No agency loaded.',
    'voyageur.create.noVehiculeLoaded': 'No vehicle loaded.',
    'voyageur.create.success':
      'Trip created: the driver is assigned and four passenger seats are offered.',
    'voyageur.create.successPrivate':
      'Private trip created. Invitations are pending passenger payments.',
    'voyageur.create.partialSuccess': 'Trip created, but some passengers could not be added.',
    'voyageur.public.title': 'Find your next ride',
    'voyageur.public.subtitle': 'Compare departures and book your place easily.',
    'voyageur.public.filter': 'Filter by agency',
    'voyageur.public.search': 'Search',
    'voyageur.public.create': 'Create a trip',
    'voyageur.public.join': 'Join',
    'voyageur.public.modify': 'Edit',
    'voyageur.public.delete': 'Delete',
    'voyageur.public.myProfile': 'My profile',
    'voyageur.public.myTrajet': 'My trip',
    'voyageur.public.available': 'Available',
    'voyageur.public.completion': 'Full',
    'voyageur.public.none': 'Not joinable',
    'voyageur.public.organizer': 'Organizer',
    'voyageur.public.status': 'Status',
    'voyageur.public.type': 'Type',
    'voyageur.public.passagers': 'Passengers',
    'voyageur.public.price': 'Price per passenger',
    'voyageur.public.registering': 'Booking...',
    'voyageur.public.editTitle': 'Edit trip',
    'voyageur.public.editHint':
      'Editing unavailable: a trip cannot be edited less than 24 hours before start.',
    'voyageur.public.noTrajets': 'No public trip trips the filters.',
    'voyageur.public.searchPlaceholder': 'Vehicle, agency, organizer',
    'voyageur.public.noTrajetPrompt': "Can't find a trip?",
    'voyageur.public.createBody': 'Public or private, with agency and vehicle choice.',
    'voyageur.public.date': 'Date',
    'voyageur.public.time': 'Start time',
    'voyageur.public.typeLabel': 'Type',
    'voyageur.reservations.title': 'My reservations',
    'voyageur.reservations.subtitle': 'Payment and cancellation of your registrations.',
    'voyageur.reservations.createPublic': 'Create PUBLIC trip',
    'voyageur.reservations.createPrivate': 'Create PRIVATE trip',
    'voyageur.reservations.trajets': 'Public trips',
    'voyageur.reservations.select': 'Select an organized trip',
    'voyageur.reservations.addPassager': 'Add passenger',
    'voyageur.reservations.noOrganized':
      'No organized trip yet. Create a PUBLIC or PRIVATE trip using the buttons above.',
    'voyageur.reservations.edit': 'Edit trip',
    'voyageur.reservations.confirmation': 'Confirmation required',
    'voyageur.reservations.passagers': 'Registered passengers:',
    'voyageur.reservations.empty': 'No reservation found.',
    'voyageur.reservations.passager': 'Passenger',
    'voyageur.reservations.trajet': 'Trip',
    'voyageur.reservations.reservation': 'Reservation',
    'voyageur.reservations.payment': 'Payment',
    'voyageur.reservations.amount': 'Amount',
    'voyageur.reservations.pay': 'Pay',
    'voyageur.reservations.cancel': 'Cancel',
    'voyageur.reservations.organizedTitle': 'My organized trips',
    'voyageur.reservations.organizedSub': 'Edit, delete, or manage passengers for your trips.',
    'voyageur.reservations.chooseTrajet': '-- Choose a trip --',
    'voyageur.reservations.inviteMatricule': 'Passenger passenger ID to add',
    'voyageur.reservations.noPassager': 'No passenger registered for this trip.',
    'voyageur.reservations.reservationLabel': 'Reservation',
    'voyageur.reservations.paymentLabel': 'Payment',
    'voyageur.reservations.removePassager': 'Remove',
    'voyageur.reservations.emptyBody':
      'Create a trip or join a public trip to see your reservations here.',
    'voyageur.payments.title': 'My payments',
    'voyageur.payments.subtitle': 'Payment and refund history',
    'voyageur.payments.reservations': 'My reservations',
    'voyageur.payments.total': 'Total payments',
    'voyageur.payments.paid': 'Total paid',
    'voyageur.payments.pending': 'Pending',
    'voyageur.payments.refunded': 'Refunded',
    'voyageur.payments.none': 'No payment found',
    'voyageur.payments.notSet': 'Not set yet',
    'voyageur.payments.idLabel': 'Payment',
    'voyageur.payments.noPayments': 'No payment found',
    'voyageur.payments.loadingState': 'Loading payments...',
    'voyageur.payments.errorState': 'Unable to load payments.',
    'voyageur.payments.noVoyageurState': 'No passenger logged in.',
    'voyageur.profile.title': 'My passenger profile',
    'voyageur.profile.subtitle': 'Information loaded from the backend',
    'voyageur.profile.createQuick': 'Create a trip quickly',
    'voyageur.profile.publicTrajet': 'Open trip',
    'voyageur.profile.privateTrajet': 'Invitation trip',
    'voyageur.profile.quickPublic': 'Create a PUBLIC trip',
    'voyageur.profile.quickPrivate': 'Create a PRIVATE trip',
    'voyageur.profile.quickPublicBody':
      'Eligible passengers · EUR 25/client · four client places maximum',
    'voyageur.profile.quickPrivateBody':
      'Invite up to four clients · Public release at H-24 only with your consent',
    'voyageur.profile.explore': 'Explore',
    'voyageur.profile.followup': 'Follow-up',
    'voyageur.profile.finance': 'Finance',
    'voyageur.profile.refresh': 'Refresh',
    'voyageur.profile.logout': 'Logout passenger',
    'voyageur.profile.nom': 'Last name',
    'voyageur.profile.prenom': 'First name',
    'voyageur.profile.email': 'Email',
    'voyageur.profile.matricule': 'Passenger ID',
    'voyageur.profile.type': 'Type',
    'voyageur.profile.agence': 'Agency',
    'voyageur.profile.solde': 'Balance',
    'voyageur.profile.restrictionActive': 'Active restriction',
    'voyageur.profile.balancePending': 'Pending balance',
    'voyageur.profile.yes': 'Yes',
    'voyageur.profile.no': 'No',
    'voyageur.profile.exploreBody': 'Join an available trip quickly.',
    'voyageur.profile.followupBody': 'Pay, cancel, or track your registrations.',
    'voyageur.profile.financeBody': 'Payment history.',
    'voyageur.payments.payment': 'Payment',
    'voyageur.payments.loading': 'Loading payments...',
    'voyageur.payments.error': 'Unable to load payments.',
    'voyageur.payments.noVoyageur': 'No passenger logged in.',
    'admin.trajets.organizer': 'Organizer',
    'admin.trajets.status': 'Status',
    'admin.trajets.passagers': 'Passengers',
    'admin.trajets.reservations': 'Trip reservations',
    'admin.trajets.noReservations': 'No reservations.',
    'admin.trajets.hideDetails': 'Hide',
    'admin.trajets.showDetails': 'View details',
    'admin.trajets.convert': 'Convert to public',
    'admin.trajets.convertSuccess': 'Trip converted to public.',
    'admin.trajets.loadError': 'Unable to load admin trips.',
    'admin.trajets.reservationsError': 'Unable to load trip reservations.',
    'admin.trajets.convertError': 'Unable to convert trip.',
    'admin.trajets.empty': 'No trip available',
    'admin.indisponibilites.date': 'Date',
    'admin.indisponibilites.reason': 'Reason (optional)',
    'admin.indisponibilites.agence': 'Agency concerned',
    'admin.indisponibilites.noneGlobal': 'None (global)',
    'admin.indisponibilites.global': 'Global indisponibilite (all agencies)',
    'admin.indisponibilites.add': 'Add indisponibilite',
    'admin.indisponibilites.reset': 'Reset',
    'admin.indisponibilites.reasonEmpty': 'No reason specified',
    'admin.indisponibilites.empty': 'No indisponibilite day configured',
    'common.notProvided': 'Not provided',
    'admin.home.allStatuses': 'all statuses',
    'admin.home.trajetCount': 'trips',
    'admin.home.agencesVisible': 'Visible agencies',
    'admin.home.vehiculesVisible': 'Visible vehicles',
    'admin.home.completeTrajets': 'Complete trips',
    'admin.home.pendingReservations': 'Pending reservations',
    'admin.login.globalBadge': '🌐 Global admin',
    'admin.login.agenceBadge': '🏢 Agency admin',
    'admin.login.showPassword': 'Show admin password',
    'admin.login.hidePassword': 'Hide admin password',
    'admin.voyageurs.searchNoResult': 'No result for "{query}"',
    'admin.voyageurs.loadError': 'Unable to load passengers.',
    'admin.voyageurs.saveError': 'Unable to save passenger.',
    'admin.voyageurs.deleteError': 'Unable to delete.',
    'admin.voyageurs.updateSuccess': 'Passenger updated.',
    'admin.voyageurs.createSuccess': 'Passenger created successfully.',
    'admin.voyageurs.deleteSuccess': 'Passenger deleted.',
    'admin.voyageurs.deleteConfirm': 'Confirm deletion of this passenger?',
    'admin.agences.name': 'Name',
    'admin.agences.address': 'Address',
    'admin.agences.opening': 'Opening',
    'admin.agences.closing': 'Closing',
    'admin.agences.trajetDuration': 'Trip duration (min)',
    'admin.agences.breakDuration': 'Break between trips (min)',
    'admin.agences.civilYear': 'Calendar year',
    'admin.agences.hours': 'Hours',
    'admin.agences.duration': 'Trip duration',
    'admin.agences.break': 'Break',
    'admin.agences.year': 'Year',
    'admin.agences.empty': 'No agency configured',
    'admin.agences.agenceAdminEditBlocked': 'A AGENCE admin cannot modify agencies.',
    'admin.agences.agenceAdminDeleteBlocked': 'A AGENCE admin cannot delete agencies.',
    'admin.agences.loadError': 'Unable to load agencies.',
    'admin.agences.saveError': 'Unable to save agency.',
    'admin.agences.deleteError': 'Unable to delete agency.',
    'admin.agences.deleteConfirm': 'Confirm deletion of this agency?',
    'admin.agences.updateSuccess': 'Agency updated.',
    'admin.agences.createSuccess': 'Agency created.',
    'admin.agences.deleteSuccess': 'Agency deleted.',
    'admin.vehicules.name': 'Vehicle name',
    'admin.vehicules.agence': 'Agency',
    'admin.vehicules.empty': 'No vehicle configured',
    'admin.vehicules.loadError': 'Unable to load vehicles.',
    'admin.vehicules.saveError': 'Unable to save vehicle.',
    'admin.vehicules.deleteError': 'Unable to delete vehicle.',
    'admin.vehicules.deleteConfirm': 'Confirm deletion of this vehicle?',
    'admin.vehicules.updateSuccess': 'Vehicle updated.',
    'admin.vehicules.createSuccess': 'Vehicle created.',
    'admin.vehicules.deleteSuccess': 'Vehicle deleted.',
    'admin.indisponibilites.loadError': 'Unable to load indisponibilite days.',
    'admin.indisponibilites.addSuccess': 'Indisponibilite day added.',
    'admin.indisponibilites.addError': 'Unable to add indisponibilite day.',
    'admin.indisponibilites.deleteSuccess': 'Indisponibilite day deleted.',
    'admin.indisponibilites.deleteError': 'Unable to delete indisponibilite day.',
    'voyageur.home.matricule': 'Passenger ID',
    'voyageur.home.matriculePlaceholder': 'Ex: V1234, L12345, F12345',
    'voyageur.home.showPassword': 'Show passenger password',
    'voyageur.home.hidePassword': 'Hide passenger password',
    'voyageur.home.globalHint': 'V + 4 digits · All agencies',
    'voyageur.home.agenceHint': 'L + 5 digits · Dedicated agency',
    'voyageur.home.livreHint': 'F + 5 digits · Open access',
    'voyageur.home.allAgences': 'All agencies',
    'voyageur.profile.loadError': 'Unable to load passenger profile.',
    'voyageur.create.bookingLimitError': 'Latest allowed date: {date}.',
    'voyageur.create.bookingLimitProfileError':
      'The {type} profile cannot book more than {days} days ahead. Deadline: {date}.',
    'voyageur.create.invalidType': 'Invalid trip type.',
    'voyageur.create.createError': 'Unable to create trip.',
    'voyageur.create.outstandingBalance':
      'You have an outstanding balance. Settle your debts before creating a trip.',
    'voyageur.create.activeRestriction':
      'You have an active restriction. Wait before creating a trip.',
    'voyageur.create.slotTaken': 'This slot is already booked. Pick another time.',
    'voyageur.create.agenceClosed': 'The agency is closed on this date.',
    'voyageur.create.outOfHours': "This time is outside the agency's opening hours.",
    'voyageur.create.agenceOnly': 'A LOCAL passenger can only create a trip on their own agency.',
    'voyageur.create.internalError': 'An internal server error occurred while creating the trip.',
    'voyageur.create.addPassagersError': 'Unable to add all passengers.',
    'voyageur.public.loadError': 'Unable to load public trips.',
    'voyageur.public.noVoyageur': 'No passenger logged in.',
    'voyageur.public.joinBlocked': 'This trip cannot be joined (full, canceled, or not allowed).',
    'voyageur.public.joinSuccess': 'Request created. Your spot will be confirmed after payment.',
    'voyageur.public.joinError': 'Unable to reserve this trip.',
    'voyageur.public.modifyTooLate':
      'Modification unavailable less than 24 hours before trip start.',
    'voyageur.public.updateSuccess': 'Trip updated successfully.',
    'voyageur.public.updateError': 'Unable to update trip.',
    'voyageur.public.deleteConfirm': 'Confirm deletion (cancellation) of trip #{id}?',
    'voyageur.public.deleteSuccess': 'Trip canceled successfully.',
    'voyageur.public.deleteError': 'Unable to delete trip.',
    'voyageur.reservations.reservationTitle': 'Reservation',
    'voyageur.reservations.noVoyageur': 'No passenger logged in.',
    'voyageur.reservations.loadError': 'Unable to load reservations.',
    'voyageur.reservations.privateOnly': 'Passenger management is reserved for PRIVATE trips.',
    'voyageur.reservations.passagersLoadError': 'Unable to load trip passengers.',
    'voyageur.reservations.updateSuccess': 'Trip updated successfully.',
    'voyageur.reservations.updateError': 'Unable to update trip.',
    'voyageur.reservations.deleteSuccess': 'Trip canceled successfully.',
    'voyageur.reservations.deleteError': 'Unable to delete trip.',
    'voyageur.reservations.passagerAdded': 'Passenger added successfully.',
    'voyageur.reservations.passagerAddError': 'Unable to add passenger.',
    'voyageur.reservations.passagerRemoved': 'Passenger removed from trip.',
    'voyageur.reservations.passagerRemoveError': 'Unable to remove passenger.',
    'voyageur.reservations.paymentSuccess': 'Payment completed successfully.',
    'voyageur.reservations.paymentError': 'Unable to process payment.',
    'voyageur.reservations.reservationCanceled': 'Reservation canceled.',
    'voyageur.reservations.reservationCancelError': 'Unable to cancel reservation.',
  },
};
