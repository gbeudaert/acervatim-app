---
title: Accueil
layout: default
nav_order: 1
---

# Acervatim

> *acervatim* (latin) — « en monceaux, par accumulation ». De *acervus*, « tas, amas, collection ».

**Acervatim** est une application Android libre (Apache 2.0) pour cataloguer vos collections. Elle démarre avec les **disques vinyles** et les **mangas**, et son architecture est pensée pour s'étendre à d'autres types de collections (BD, LEGO, jeux de société, livres, films…).

![Statut](https://img.shields.io/badge/statut-pre--alpha-orange)
![Plateforme](https://img.shields.io/badge/platform-Android%207%2B-blue)
![Licence](https://img.shields.io/badge/license-Apache%202.0-green)

---

## Les principes

- **100 % hors-ligne par défaut.** Toutes vos données restent sur votre appareil : pas de compte obligatoire, pas de télémétrie, pas de connexion requise pour utiliser l'application.
- **Multi-collections.** Une seule application pour tous vos objets de collection, avec une expérience adaptée à chaque type : liste à plat pour les vinyles, vue par séries avec progression pour les mangas.
- **Vos données vous appartiennent.** Import, export et sauvegarde complète (ZIP) à tout moment, dans des formats ouverts (JSON, CSV).
- **Material You.** Thème généré depuis la couleur de marque (vert d'eau) ou depuis votre fond d'écran, en clair comme en sombre.

## Deux modes de fonctionnement

| | Mode hors-ligne (défaut) | Mode connecté (opt-in) |
|---|---|---|
| Compte | Aucun | Google Sign-In |
| Saisie | Manuelle + scan code-barres | Enrichie par les bases de données externes |
| Données | Uniquement sur l'appareil | Synchronisées avec le serveur Acervatim |
| Connexion | Jamais requise | Requise pour la recherche externe et la synchro |

Le mode connecté s'appuie sur le **serveur Acervatim**, un backend dédié qui sert de point d'accès unique aux bases de données externes (Discogs, MyAnimeList, TMDB…) et de service de synchronisation — voir [Le serveur Acervatim](backend.md).

Même en mode connecté, l'application reste *offline-first* : la consultation, l'édition et la recherche locales fonctionnent toujours sans réseau. Le serveur n'est sollicité que pour des actions explicites (recherche externe, synchronisation).

## Pour commencer

1. Installez l'application (distribution Play Store à venir — voir [Fonctionnalités à venir](roadmap.md)).
2. Ouvrez le tiroir de navigation (☰) et choisissez votre collection : **Vinyle** ou **Manga**.
3. Ajoutez votre premier disque ou votre première série avec le bouton **+**.

Le détail de toutes les fonctionnalités est dans le [guide d'utilisation](application.md).

## Sommaire de la documentation

- [Guide d'utilisation de l'application](application.md) — collections, ajout, scan, wishlist, séries, import/export, réglages.
- [Le serveur Acervatim](backend.md) — rôle, architecture, confidentialité et API du backend.
- [Fonctionnalités à venir](roadmap.md) — ce qui est prévu côté application et côté serveur.
