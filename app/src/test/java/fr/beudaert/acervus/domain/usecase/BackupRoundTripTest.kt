package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.data.backup.BackupCoverIo
import fr.beudaert.acervus.data.backup.BackupGatewayImpl
import fr.beudaert.acervus.data.backup.BackupZip
import fr.beudaert.acervus.domain.FakeRecordRepository
import fr.beudaert.acervus.domain.FakeSeriesRepository
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.Artist
import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.ItemStatus
import fr.beudaert.acervus.domain.model.MangaExtras
import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.RecordingSpeed
import fr.beudaert.acervus.domain.model.Series
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class BackupRoundTripTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private val fixedInstant: Instant = Instant.parse("2026-05-20T10:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @Test
    fun `export then import restores records and series`() = runTest {
        val recordRepo = FakeRecordRepository()
        val seriesRepo = FakeSeriesRepository()
        val coverIo = FakeBackupCoverIo(tempFolder.root)

        val seriesId = UUID.randomUUID().toString()
        val vinylId = UUID.randomUUID().toString()
        val mangaId = UUID.randomUUID().toString()

        // Pré-existant : une cover vinyle locale + une cover série locale
        val vinylCoverBytes = "FAKE-VINYL-COVER".toByteArray()
        val vinylCoverFile = coverIo.coverFileForRecord(vinylId).apply { writeBytes(vinylCoverBytes) }
        val seriesCoverBytes = "FAKE-SERIES-COVER".toByteArray()
        val seriesCoverFile = coverIo.coverFileForSeries(seriesId).apply { writeBytes(seriesCoverBytes) }

        seriesRepo.upsert(
            Series(
                id = seriesId,
                collectionType = CollectionType.MANGA,
                title = "Berserk",
                originalAuthor = "Kentaro Miura",
                publicationStatus = "ONGOING",
                totalVolumes = 42,
                synopsis = "Guts.",
                coverPath = seriesCoverFile.absolutePath,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )
        )
        recordRepo.upsert(
            Record(
                id = vinylId,
                collectionType = CollectionType.VINYL,
                artists = listOf(Artist(id = "a1", name = "Pink Floyd"), Artist(id = "a2", name = "Roger Waters")),
                title = "The Dark Side of the Moon",
                year = 1973,
                label = "Harvest",
                format = AcervusFormat.LP_33,
                recordingSpeed = RecordingSpeed.RPM_33,
                country = "UK",
                purchasePrice = 25.5,
                rating = 5,
                coverPath = vinylCoverFile.absolutePath,
                barcode = "5099902987910",
                status = ItemStatus.OWNED,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )
        )
        recordRepo.upsert(
            Record(
                id = mangaId,
                collectionType = CollectionType.MANGA,
                seriesId = seriesId,
                volumeNumber = 1,
                artists = listOf(Artist(id = "a3", name = "Kentaro Miura")),
                title = "Berserk vol. 1",
                isbn = "9784253111119",
                status = ItemStatus.OWNED,
                extras = mapOf(MangaExtras.KEY_PUBLISHER_FR to "Glénat", MangaExtras.KEY_PAGE_COUNT to "240"),
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )
        )

        val gateway = BackupGatewayImpl(coverIo)
        val export = ExportBackupUseCase(recordRepo, seriesRepo, gateway, clock)
            .apply { ioDispatcher = UnconfinedTestDispatcher() }
        val import = ImportBackupUseCase(recordRepo, seriesRepo, gateway)
            .apply { ioDispatcher = UnconfinedTestDispatcher() }

        // 1. Export
        val buffer = ByteArrayOutputStream()
        val exportResult = export(buffer, appVersionName = "0.1.0-test")
        assertEquals(2, exportResult.recordCount)
        assertEquals(1, exportResult.seriesCount)
        val backupBytes = buffer.toByteArray()
        assertTrue("backup should not be empty", backupBytes.isNotEmpty())

        // 2. Wipe covers (les repos sont wipés par l'import lui-même)
        coverIo.deleteAllCovers()

        // 3. Import
        val importResult = import(ByteArrayInputStream(backupBytes))
        assertEquals(2, importResult.recordCount)
        assertEquals(1, importResult.seriesCount)
        assertEquals(1, importResult.schemaVersion)

        // 4. Vérifications
        val restoredSeries = seriesRepo.listAll().single()
        assertEquals(seriesId, restoredSeries.id)
        assertEquals("Berserk", restoredSeries.title)
        assertEquals("Kentaro Miura", restoredSeries.originalAuthor)
        assertEquals(42, restoredSeries.totalVolumes)
        assertNotNull("la cover série doit être restaurée", restoredSeries.coverPath)
        assertArrayEquals(seriesCoverBytes, File(restoredSeries.coverPath!!).readBytes())

        val restoredRecords = recordRepo.listAll().sortedBy { it.title }
        assertEquals(2, restoredRecords.size)

        val berserk = restoredRecords.first { it.title == "Berserk vol. 1" }
        assertEquals(mangaId, berserk.id)
        assertEquals(CollectionType.MANGA, berserk.collectionType)
        assertEquals(seriesId, berserk.seriesId)
        assertEquals(1, berserk.volumeNumber)
        assertEquals("9784253111119", berserk.isbn)
        assertEquals(listOf("Kentaro Miura"), berserk.artists.map { it.name })
        assertEquals("Glénat", berserk.extras[MangaExtras.KEY_PUBLISHER_FR])

        val floyd = restoredRecords.first { it.title.startsWith("The Dark") }
        assertEquals(vinylId, floyd.id)
        assertEquals(listOf("Pink Floyd", "Roger Waters"), floyd.artists.map { it.name })
        assertEquals(AcervusFormat.LP_33, floyd.format)
        assertEquals(RecordingSpeed.RPM_33, floyd.recordingSpeed)
        assertEquals(1973, floyd.year)
        assertEquals(25.5, floyd.purchasePrice!!, 0.0001)
        assertEquals(5, floyd.rating)
        assertEquals("5099902987910", floyd.barcode)
        assertNotNull("la cover du vinyle doit être restaurée", floyd.coverPath)
        assertArrayEquals(vinylCoverBytes, File(floyd.coverPath!!).readBytes())
    }

    @Test
    fun `import rejects malicious record id without wiping existing data`() = runTest {
        val recordRepo = FakeRecordRepository()
        val seriesRepo = FakeSeriesRepository()
        val coverIo = FakeBackupCoverIo(tempFolder.root)

        // Donnée existante à protéger : doit survivre à un import malformé.
        val existing = Record(
            id = UUID.randomUUID().toString(),
            collectionType = CollectionType.VINYL,
            artists = listOf(Artist(id = "x", name = "Keep me")),
            title = "Existing — must not be wiped",
            status = ItemStatus.OWNED,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        recordRepo.upsert(existing)

        // Backup malicieux : id contenant un path traversal (`../...`).
        val malicious = Record(
            id = "../../../databases/acervus",
            collectionType = CollectionType.VINYL,
            artists = listOf(Artist(id = "y", name = "Evil")),
            title = "Pwn",
            status = ItemStatus.OWNED,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        val buffer = ByteArrayOutputStream()
        BackupZip.write(
            output = buffer,
            records = listOf(malicious),
            series = emptyList(),
            coverFileResolver = { null },
            exportedAt = fixedInstant,
            appVersionName = "evil"
        )

        val gateway = BackupGatewayImpl(coverIo)
        val import = ImportBackupUseCase(recordRepo, seriesRepo, gateway)
            .apply { ioDispatcher = UnconfinedTestDispatcher() }

        val error = try {
            import(ByteArrayInputStream(buffer.toByteArray()))
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertNotNull("l'import doit refuser un id malicieux", error)

        // Crucial : la donnée pré-existante NE doit PAS avoir été effacée.
        assertEquals(1, recordRepo.listAll().size)
        assertEquals(existing.id, recordRepo.listAll().single().id)
    }

    @Test
    fun `import wipes existing data before restoring`() = runTest {
        val recordRepo = FakeRecordRepository()
        val seriesRepo = FakeSeriesRepository()
        val coverIo = FakeBackupCoverIo(tempFolder.root)

        val gateway = BackupGatewayImpl(coverIo)
        val export = ExportBackupUseCase(recordRepo, seriesRepo, gateway, clock)
            .apply { ioDispatcher = UnconfinedTestDispatcher() }
        val import = ImportBackupUseCase(recordRepo, seriesRepo, gateway)
            .apply { ioDispatcher = UnconfinedTestDispatcher() }

        // Export d'un état VIDE.
        val empty = ByteArrayOutputStream().also { export(it) }.toByteArray()

        // On peuple les repos après coup avec un disque "résiduel".
        val ghost = Record(
            id = UUID.randomUUID().toString(),
            collectionType = CollectionType.VINYL,
            artists = listOf(Artist(id = "x", name = "Ghost")),
            title = "Should be wiped",
            status = ItemStatus.OWNED,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        recordRepo.upsert(ghost)
        assertEquals(1, recordRepo.listAll().size)

        // Import du backup vide → doit effacer le disque résiduel.
        import(ByteArrayInputStream(empty))
        assertTrue("import doit avoir effacé les disques précédents", recordRepo.listAll().isEmpty())
        assertTrue("import doit avoir effacé les séries précédentes", seriesRepo.listAll().isEmpty())
    }
}

private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
    org.junit.Assert.assertArrayEquals(expected, actual)
}

private class FakeBackupCoverIo(private val root: File) : BackupCoverIo {
    private val dir: File = File(root, "covers").apply { mkdirs() }

    override fun resolveExistingCover(absolutePath: String): File? =
        File(absolutePath).takeIf { it.exists() && it.isFile }

    override fun coverFileForRecord(recordId: String): File = File(dir, "$recordId.jpg")
    override fun coverFileForSeries(seriesId: String): File = File(dir, "series-$seriesId.jpg")
    override fun deleteAllCovers() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
