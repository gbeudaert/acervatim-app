---
title: Fonctionnalités à venir
layout: default
nav_order: 4
---

# Fonctionnalités à venir

Acervatim est en pré-alpha : l'application n'est pas encore publiée sur le Play Store. Cette page donne la direction générale — les priorités peuvent évoluer.

## Disponible aujourd'hui

- Collections **vinyle** (vue à plat) et **manga** (vue par séries, trois états par tome), avec bascule entre collections via le tiroir de navigation.
- Recherche instantanée, filtres, wishlist, note, dernière écoute.
- Ajout manuel, **scan de code-barres hors-ligne**, enrichissement Discogs (jeton personnel).
- Multi-artistes, vitesse de gravure, déduplication par code-barres et par ISBN.
- Import/export JSON et CSV, **sauvegarde complète ZIP** avec jaquettes.
- Thèmes Material You (marque, système, expressif), clair et sombre.
- Côté serveur : authentification Google, synchronisation des collections, recherche externe unifiée (Discogs, MyAnimeList, TMDB), premium et invitations.

## Court terme

- **Publication Play Store v1.0** : la version 100 % hors-ligne (vinyle + manga), sans compte ni achat intégré.
- **Activation du mode connecté dans l'application** : connexion Google, synchronisation avec le serveur, recherche externe via l'endpoint unifié. La couche technique est en place côté app et côté serveur ; il reste la configuration de production et la finalisation de la synchronisation manga.
- **Enrichissement manga** : recherche par titre et par ISBN dans les bases externes pour pré-remplir séries et tomes, comme Discogs le fait pour le vinyle.

## Moyen terme

- **Premium** (via Google Play Billing) : synchronisation multi-appareils, sources d'enrichissement supplémentaires, exports avancés. Le socle gratuit reste complet pour un usage personnel.
- **Nouvelles collections** : BD (vue à plat ou par séries au choix), LEGO, jeux de société, livres, films — chaque type avec sa source de métadonnées de référence (Rebrickable, BoardGameGeek, Google Books, TMDB…), routée par le serveur.
- **Confort** : recherche dans la vue séries, transitions animées liste → détail, scan adapté automatiquement à la collection active, création de collections personnalisées depuis le tiroir.

## Plus tard

- Alerte « nouveau tome disponible » et vue des tomes manquants pour les séries suivies.
- Statistiques de collection (valeur, répartition par genre, habitudes d'écoute).
- Montée en charge du serveur (migration cloud) si l'usage le justifie — le schéma de données est prêt pour ce scénario.

## Hors périmètre

Volontairement exclus du projet, aujourd'hui comme demain :

- partage social, communauté, marketplace ;
- lecture audio ;
- publicité et revente de données — le modèle est un palier gratuit complet + premium optionnel, rien d'autre.
