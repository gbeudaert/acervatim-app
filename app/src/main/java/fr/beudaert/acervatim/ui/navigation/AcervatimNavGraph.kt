package fr.beudaert.acervatim.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.ui.detail.RecordDetailScreen
import fr.beudaert.acervatim.ui.edit.AddEditRecordScreen
import fr.beudaert.acervatim.ui.list.CollectionScreen
import fr.beudaert.acervatim.ui.manga.AddEditMangaScreen
import fr.beudaert.acervatim.ui.account.AccountScreen
import fr.beudaert.acervatim.ui.scan.BarcodeScanScreen
import fr.beudaert.acervatim.ui.series.AddEditSeriesScreen
import fr.beudaert.acervatim.ui.series.SeriesDetailScreen
import fr.beudaert.acervatim.ui.settings.SettingsScreen

@Composable
fun AcervatimNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AcervatimDestinations.COLLECTION_ROUTE
    ) {
        composable(AcervatimDestinations.COLLECTION_ROUTE) {
            CollectionScreen(
                onAddRecord = { navController.navigate(AcervatimDestinations.addRecord()) },
                onScanRecord = { navController.navigate(AcervatimDestinations.SCAN_ROUTE) },
                onAddManga = { navController.navigate(AcervatimDestinations.addManga()) },
                onAddSeries = { type ->
                    navController.navigate(AcervatimDestinations.addSeries(type.name))
                },
                onOpenRecord = { id ->
                    navController.navigate(AcervatimDestinations.recordDetail(id))
                },
                onOpenSeries = { id ->
                    navController.navigate(AcervatimDestinations.seriesDetail(id))
                },
                onOpenSettings = { navController.navigate(AcervatimDestinations.SETTINGS_ROUTE) }
            )
        }
        composable(
            route = AcervatimDestinations.ADD_RECORD_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.BARCODE) {
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
            route = AcervatimDestinations.RECORD_DETAIL_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.RECORD_ID) { type = NavType.StringType }
            )
        ) {
            RecordDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id, type ->
                    val route = when (type) {
                        CollectionType.MANGA -> AcervatimDestinations.editManga(id)
                        else -> AcervatimDestinations.editRecord(id)
                    }
                    navController.navigate(route)
                }
            )
        }
        composable(
            route = AcervatimDestinations.EDIT_RECORD_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.RECORD_ID) { type = NavType.StringType }
            )
        ) {
            AddEditRecordScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(
            route = AcervatimDestinations.SERIES_DETAIL_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.SERIES_ID) { type = NavType.StringType }
            )
        ) {
            SeriesDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenVolume = { id ->
                    navController.navigate(AcervatimDestinations.recordDetail(id))
                },
                onAddVolume = { seriesId, suggested ->
                    navController.navigate(
                        AcervatimDestinations.addManga(
                            seriesId = seriesId,
                            volumeNumber = suggested
                        )
                    )
                },
                onEditSeries = { id ->
                    navController.navigate(AcervatimDestinations.editSeries(id))
                }
            )
        }
        composable(
            route = AcervatimDestinations.ADD_SERIES_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.COLLECTION_TYPE) {
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
            route = AcervatimDestinations.EDIT_SERIES_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.SERIES_ID) { type = NavType.StringType }
            )
        ) {
            AddEditSeriesScreen(
                onSaved = { _ -> navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(
            route = AcervatimDestinations.ADD_MANGA_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.SERIES_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(AcervatimNavArgs.BARCODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(AcervatimNavArgs.VOLUME_NUMBER) {
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
                    navController.navigate(AcervatimDestinations.addSeries(CollectionType.MANGA.name))
                }
            )
        }
        composable(
            route = AcervatimDestinations.EDIT_MANGA_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.RECORD_ID) { type = NavType.StringType }
            )
        ) {
            AddEditMangaScreen(
                onSaved = { _ -> navController.popBackStack() },
                onCancel = { navController.popBackStack() },
                onAddSeries = {
                    navController.navigate(AcervatimDestinations.addSeries(CollectionType.MANGA.name))
                }
            )
        }
        composable(AcervatimDestinations.SETTINGS_ROUTE) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAccount = { navController.navigate(AcervatimDestinations.ACCOUNT_ROUTE) }
            )
        }
        composable(AcervatimDestinations.ACCOUNT_ROUTE) {
            AccountScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(AcervatimDestinations.SCAN_ROUTE) {
            BarcodeScanScreen(
                onBack = { navController.popBackStack() },
                onExistingRecord = { id ->
                    navController.navigate(AcervatimDestinations.recordDetail(id)) {
                        popUpTo(AcervatimDestinations.SCAN_ROUTE) { inclusive = true }
                    }
                },
                onNewBarcode = { barcode ->
                    // Le scan dépose toujours sur le formulaire vinyle pour Sprint 8.
                    // La bascule du routing selon le CollectionType actif est tracée pour
                    // Sprint 9 (cf. spec §6 — fallback "formulaire pré-rempli" déjà couvert).
                    navController.navigate(AcervatimDestinations.addRecord(barcode)) {
                        popUpTo(AcervatimDestinations.SCAN_ROUTE) { inclusive = true }
                    }
                }
            )
        }
    }
}
