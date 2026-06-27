---
title: Politique de confidentialité
layout: default
nav_order: 90
---

# Politique de confidentialité — Acervatim

*Dernière mise à jour : 21 juin 2026*

**Acervatim** est une application Android de gestion de collections. Elle fonctionne
**100 % hors-ligne par défaut** : toutes les données que vous saisissez (collections,
fiches, jaquettes, notes) restent sur votre appareil et sont supprimées lors de la
désinstallation de l'application.

Un **compte et une synchronisation en ligne sont entièrement optionnels** : tant que
vous ne vous connectez pas, aucune donnée ne quitte votre appareil.

---

## 1. Données traitées localement (sans connexion)

Stockées uniquement sur votre appareil, jamais transmises tant que vous ne connectez
pas de compte :

- Les éléments de vos collections (titres, artistes, séries, métadonnées, notes).
- Les images de jaquettes que vous photographiez ou importez.
- Vos préférences d'affichage et réglages de l'application.

---

## 2. Données traitées lorsque vous activez un compte (optionnel)

Si — et seulement si — vous vous connectez avec **Google Sign-In** pour activer la
synchronisation :

| Donnée | Finalité | Transmission |
|---|---|---|
| Identifiant Google et adresse e-mail | Authentifier votre compte | Vers le backend Acervatim (`api.acervatim.fr`) |
| Contenu de vos collections | Sauvegarder et synchroniser entre vos appareils | Vers le backend Acervatim |
| Jeton d'authentification (JWT) | Maintenir la session | Stocké **chiffré** sur l'appareil |

- Les échanges réseau sont **chiffrés en transit (HTTPS/TLS)**.
- Le jeton d'authentification est stocké de façon **chiffrée** sur l'appareil.
- Nous ne vendons ni ne partageons vos données avec des tiers à des fins publicitaires.
- L'application **ne contient ni publicité ni pisteur tiers**.

---

## 3. Permissions

- **Appareil photo (`CAMERA`)** : pour photographier les jaquettes et scanner les
  codes-barres. La reconnaissance de code-barres s'effectue **hors ligne, sur
  l'appareil** (modèle ML Kit embarqué). Les images restent locales sauf si vous avez
  activé la synchronisation de compte.
- **Internet (`INTERNET`)** : utilisé uniquement pour l'authentification et la
  synchronisation lorsque vous avez activé un compte.

---

## 4. Conservation et suppression de vos données

- **Données locales** : supprimées en désinstallant l'application ou en effaçant ses
  données dans les réglages Android.
- **Données du compte** (si synchronisation activée) : vous pouvez demander la
  suppression de votre compte et des données associées en nous contactant à l'adresse
  ci-dessous. Les données sont alors effacées de nos serveurs.

---

## 5. Enfants

L'application n'est pas destinée aux enfants de moins de 13 ans et ne collecte pas
sciemment leurs données.

---

## 6. Modifications

Cette politique peut être mise à jour ; la date de dernière mise à jour figure en tête
de page. Les changements significatifs seront signalés via les notes de version sur
Google Play.

---

## 7. Contact

Pour toute question relative à la confidentialité ou pour demander la suppression de
vos données : **guillaume.beudaert@gmail.com**
