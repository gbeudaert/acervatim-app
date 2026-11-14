package app.acervatim.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

@Immutable
data class Series(
    val id: String,
    val collectionType: CollectionType,
    val title: String,
    val originalAuthor: String? = null,
    val publicationStatus: String? = null,
    val totalVolumes: Int? = null,
    val synopsis: String? = null,
    val coverPath: String? = null,
    /**
     * Jaquette distante (URL provider), pendant de [Record.coverRemoteUrl].
     *
     * Champ **domaine seulement** : Room ne le stocke pas, il reste donc `null` pour les séries
     * locales. Il sert aux séries reconstruites depuis le réseau — une collection reçue en partage,
     * qui n'a par définition aucun fichier local.
     */
    val coverRemoteUrl: String? = null,
    /**
     * Mention d'édition (205$a BnF, ex « Éd. colossale »), `null` pour l'édition standard. Avec
     * [publisherFr], elle fait partie de l'**identité** de la série : deux éditions d'une même œuvre
     * coexistent comme deux séries (cf. `spec-macro-import-serie-manga.md`, arbitrage du 2026-08-26).
     */
    val edition: String? = null,
    /** Éditeur français de l'édition (ex « Pika »). `null` pour une série saisie à la main. */
    val publisherFr: String? = null,
    /**
     * Id MangaDex de l'œuvre, quand la série vient d'une recherche ou d'un scan identifié : il
     * joint directement les jaquettes par tome côté serveur, sans rapprochement par titre.
     */
    val mangaId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Vrai si [Series.publicationStatus] dit la série **terminée**. Sert à ne pas chercher de nouveaux
 * tomes d'une série close. Même règle que la synchro, par construction : cf. [isFinishedStatus].
 */
val Series.isPublicationFinished: Boolean
    get() = isFinishedStatus(publicationStatus)

/**
 * Le statut de parution libre [raw] dit-il « terminé » ? Seule règle de l'app, partagée par le
 * rattrapage ([isPublicationFinished]) **et** l'encodage de synchro (`FacadeMapper.toMangaStatusCode`) :
 * deux listes séparées avaient divergé (« Terminé » reconnu ici, pas là). Le champ est un texte
 * libre — saisi à la main, écrit par un import (« Terminée »), ou relu du serveur (`finished`) —
 * d'où le pliage casse + accents ([normalizeArtistName]) : « Terminé », « TERMINÉE », « Terminee ».
 */
fun isFinishedStatus(raw: String?): Boolean =
    raw != null && normalizeArtistName(raw) in FINISHED_STATUSES

/** Formes pliées (minuscules, sans accents) d'un statut « terminé ». */
private val FINISHED_STATUSES = setOf("termine", "terminee", "finished", "completed")
