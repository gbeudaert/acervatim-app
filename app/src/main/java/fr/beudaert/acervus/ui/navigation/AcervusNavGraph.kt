package fr.beudaert.acervus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.ui.detail.RecordDetailScreen
import fr.beudaert.acervus.ui.edit.AddEditRecordScreen
import fr.beudaert.acervus.ui.list.CollectionScreen
import fr.beudaert.acervus.ui.manga.AddEditMangaScreen
import fr.beudaert.acervus.ui.scan.BarcodeScanScreen
import fr.beudaert.acervus.ui.series.AddEditSeriesScreen
import fr.beudaert.acervus.ui.series.SeriesDetailScreen
import fr.beudaert.acervus.ui.settings.SettingsScreen

@Composable
fun AcervusNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AcervusDestinations.COLLECTION_ROUTE
    ) {
        composable(AcervusDestinations.COLLECTION_ROUTE) {
            CollectionScreen(
                onAddRecord = { navController.navigate(AcervusDestinations.addRecord()) },
                onScanRecord = { navController.navigate(AcervusDestinations.SCAN_ROUTE) },
                onAddManga = { navController.navigate(AcervusDestinations.addManga()) },
                onAddSeries = { type ->
                    navController.navigate(AcervusDestinations.addSeries(type.name))
                },
                onOpenRecord = { id ->
                    navController.navigate(AcervusDestinations.recordDetail(id))
                },
                onOpenSeries = { id ->
                    navController.navigate(AcervusDestinations.seriesDetail(id))
                },
                onOpenSettings = { navController.navigate(AcervusDestinations.SETTINGS_ROUTE) }
            )
        }
        composable(
            route = AcervusDestinations.ADD_RECORD_ROUTE,
            arguments = listOf(
                navArgument(AcervusNavArgs.BARCODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            AddEditRecordScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(
            route = AcervusDestinations.RECORD_DETAIL_ROUTE,
            arguments = listOf(
                navArgument(AcervusNavArgs.RECORD_ID) { type = NavType.StringType }
            )
        ) {
            RecordDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id, type ->
                    val route = when (type) {
                        CollectionType.MANGA -> AcervusDestinations.editManga(id)
                        else -> AcervusDestinations.editRecord(id)
                    }
                    navController.navigate(route)
                }
            )
        }
        composable(
            route = AcervusDestinations.EDIT_RECORD_ROUTE,
            arguments = listOf(
                navArgument(AcervusNavArgs.RECORD_ID) { type = NavType.StringType }
            )
        ) {
            AddEditRecordScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(
            route = AcervusDestinations.SERIES_DETAIL_ROUTE,
            arguments = listOf(
                navArgument(AcervusNavArgs.SERIES_ID) { type = NavType.StringType }
            )
        ) {
            SeriesDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenVolume = { id ->
                    navController.navigate(AcervusDestinations.recordDetail(id))
                },
                onAddVolume = { seriesId, suggested ->
                    navController.navigate(
                        AcervusDestinations.addManga(
                            seriesId = seriesId,
                            volumeNumber = suggested
                        )
                    )
                },
                onEditSeries = { id ->
                    navController.navigate(AcervusDestinations.editSeries(id))
                }
            )
        }
        composable(
            route = AcervusDestinations.ADD_SERIES_ROUTE,
            arguments = listOf(
                navArgument(AcervusNavArgs.COLLECTION_TYPE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            AddEditSeriesScreen(
                onSaved = { _ -> navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(
            route = AcervusDestinations.EDIT_SERIES_ROUTE,
            arguments = listOf(
                navArgument(AcervusNavArgs.SERIES_ID) { type = NavType.StringType }
            )
        ) {
            AddEditSeriesScreen(
                onSaved = { _ -> navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(
            route = AcervusDestinations.ADD_MANGA_ROUTE,
            arguments = listOf(
                navArgument(AcervusNavArgs.SERIES_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(AcervusNavArgs.BARCODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(AcervusNavArgs.VOLUME_NUMBER) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            AddEditMangaScreen(
                onSaved = { _ -> navController.popBackStack() },
                onCancel = { navController.popBackStack() },
                onAddSeries = {
                    navController.navigate(AcervusDestinations.addSeries(CollectionType.MANGA.name))
                }
            )
        }
        composable(
            route = AcervusDestinations.EDIT_MANGA_ROUTE,
            arguments = listOf(
                navArgument(AcervusNavArgs.RECORD_ID) { type = NavType.StringType }
            )
        ) {
            AddEditMangaScreen(
                onSaved = { _ -> navController.popBackStack() },
                onCancel = { navController.popBackStack() },
                onAddSeries = {
                    navController.navigate(AcervusDestinations.addSeries(CollectionType.MANGA.name))
                }
            )
        }
        composable(AcervusDestinations.SETTINGS_ROUTE) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(AcervusDestinations.SCAN_ROUTE) {
            BarcodeScanScreen(
                onBack = { navController.popBackStack() },
                onExistingRecord = { id ->
                    navController.navigate(AcervusDestinations.recordDetail(id)) {
                        popUpTo(AcervusDestinations.SCAN_ROUTE) { inclusive = true }
                    }
                },
                onNewBarcode = { barcode ->
                    // Le scan dépose toujours sur le formulaire vinyle pour Sprint 8.
                    // La bascule du routing selon le CollectionType actif est tracée pour
                    // Sprint 9 (cf. spec §6 — fallback "formulaire pré-rempli" déjà couvert).
                    navController.navigate(AcervusDestinations.addRecord(barcode)) {
                        popUpTo(AcervusDestinations.SCAN_ROUTE) { inclusive = true }
                    }
                }
            )
        }
    }
}
