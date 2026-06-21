package app.acervatim.domain.usecase

import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.RecordingSpeed

data class RecordInput(
    val artists: List<String>,
    val title: String,
    val collectionType: CollectionType = CollectionType.VINYL,
    val seriesId: String? = null,
    val volumeNumber: Int? = null,
    val genre: String? = null,
    val year: Int? = null,
    val label: String? = null,
    val format: AcervatimFormat? = null,
    val recordingSpeed: RecordingSpeed? = null,
    val country: String? = null,
    val purchasePrice: Double? = null,
    val rating: Int? = null,
    val coverPath: String? = null,
    val coverRemoteUrl: String? = null,
    val barcode: String? = null,
    val isbn: String? = null,
    val discogsId: Long? = null,
    val status: ItemStatus = ItemStatus.OWNED,
    val extras: Map<String, String> = emptyMap()
)
