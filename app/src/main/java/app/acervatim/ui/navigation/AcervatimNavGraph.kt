package app.acervatim.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.acervatim.domain.model.CollectionType
import app.acervatim.ui.detail.RecordDetailScreen
import app.acervatim.ui.edit.AddEditRecordScreen
import app.acervatim.ui.list.CollectionScreen
import app.acervatim.ui.manga.AddEditMangaScreen
import app.acervatim.ui.account.AccountScreen
import app.acervatim.ui.scan.BarcodeScanScreen
import app.acervatim.ui.series.AddEditSeriesScreen
import app.acervatim.ui.series.SeriesDetailScreen
import app.acervatim.ui.settings.ManageCollectionTypesScreen
import app.acervatim.ui.settings.SettingsScreen

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
                onScanRecord = { type -> navController.navigate(AcervatimDestinations.scan(type.name)) },
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
                onOpenSettings = { navController.navigate(AcervatimDestinations.SETTINGS_ROUTE) },
                onOpenAccount = { navController.navigate(AcervatimDestinations.ACCOUNT_ROUTE) }
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
                onOpenAccount = { navController.navigate(AcervatimDestinations.ACCOUNT_ROUTE) },
                onManageCollectionTypes = {
                    navController.navigate(AcervatimDestinations.MANAGE_COLLECTION_TYPES_ROUTE)
                }
            )
        }
        composable(AcervatimDestinations.MANAGE_COLLECTION_TYPES_ROUTE) {
            ManageCollectionTypesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(AcervatimDestinations.ACCOUNT_ROUTE) {
            AccountScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = AcervatimDestinations.SCAN_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.COLLECTION_TYPE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            // Le scan route le code-barres vers le formulaire du type de collection actif :
            // manga (ISBN → AddEditMangaScreen) sinon vinyle (EAN → AddEditRecordScreen).
            val scannedType = backStackEntry.arguments
                ?.getString(AcervatimNavArgs.COLLECTION_TYPE)
                ?.let { runCatching { CollectionType.valueOf(it) }.getOrNull() }
            BarcodeScanScreen(
                onBack = { navController.popBackStack() },
                onExistingRecord = { id ->
                    navController.navigate(AcervatimDestinations.recordDetail(id)) {
                        popUpTo(AcervatimDestinations.SCAN_ROUTE) { inclusive = true }
                    }
                },
                onNewBarcode = { barcode ->
                    val route = when (scannedType) {
                        CollectionType.MANGA -> AcervatimDestinations.addManga(barcode = barcode)
                        else -> AcervatimDestinations.addRecord(barcode)
                    }
                    navController.navigate(route) {
                        popUpTo(AcervatimDestinations.SCAN_ROUTE) { inclusive = true }
                    }
                }
            )
        }
    }
}
