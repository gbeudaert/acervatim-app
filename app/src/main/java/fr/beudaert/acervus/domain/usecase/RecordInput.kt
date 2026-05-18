package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.RecordStatus
import fr.beudaert.acervus.domain.model.RecordingSpeed

data class RecordInput(
    val artists: List<String>,
    val title: String,
    val genre: String? = null,
    val year: Int? = null,
    val label: String? = null,
    val format: AcervusFormat? = null,
    val recordingSpeed: RecordingSpeed? = null,
    val country: String? = null,
    val purchasePrice: Double? = null,
    val rating: Int? = null,
    val coverPath: String? = null,
    val coverRemoteUrl: String? = null,
    val barcode: String? = null,
    val discogsId: Long? = null,
    val status: RecordStatus = RecordStatus.OWNED
)
