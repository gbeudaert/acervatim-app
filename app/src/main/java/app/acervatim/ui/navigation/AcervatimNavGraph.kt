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
import app.acervatim.ui.manga.MangaDetailScreen
import app.acervatim.ui.account.AccountScreen
import app.acervatim.ui.scan.BarcodeScanScreen
import app.acervatim.ui.search.ExternalSearchScreen
import app.acervatim.ui.series.AddEditSeriesScreen
import app.acervatim.ui.series.SeriesDetailScreen
import app.acervatim.ui.series.SeriesImportScreen
import app.acervatim.ui.sharing.SharingScreen
import app.acervatim.ui.sharing.received.ReceivedSharesScreen
import app.acervatim.ui.sharing.received.SharedCollectionScreen
import app.acervatim.ui.sharing.received.SharedItemDetailScreen
import app.acervatim.ui.sharing.received.SharedSeriesDetailScreen
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
                onSearchExternal = { type ->
                    navController.navigate(AcervatimDestinations.externalSearch(type.name))
                },
                onScanSeries = { type ->
                    navController.navigate(AcervatimDestinations.scan(type.name, seriesImport = true))
                },
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
                onOpenAccount = { navController.navigate(AcervatimDestinations.ACCOUNT_ROUTE) },
                onOpenSharing = {
                    navController.navigate(AcervatimDestinations.SHARING_ROUTE)
                },
                onOpenReceivedShares = {
                    navController.navigate(AcervatimDestinations.RECEIVED_SHARES_ROUTE)
                }
            )
        }
        composable(AcervatimDestinations.SHARING_ROUTE) {
            SharingScreen(onBack = { navController.popBackStack() })
        }
        composable(AcervatimDestinations.RECEIVED_SHARES_ROUTE) {
            ReceivedSharesScreen(
                onBack = { navController.popBackStack() },
                onOpenSharedCollection = { collectionId ->
                    navController.navigate(AcervatimDestinations.sharedCollection(collectionId))
                }
            )
        }
        composable(
            route = AcervatimDestinations.SHARED_COLLECTION_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.COLLECTION_ID) { type = NavType.StringType }
            )
        ) {
            val collectionId = it.arguments
                ?.getString(AcervatimNavArgs.COLLECTION_ID)
                .orEmpty()
            SharedCollectionScreen(
                onBack = { navController.popBackStack() },
                onOpenItem = { itemId ->
                    navController.navigate(AcervatimDestinations.sharedItem(itemId))
                },
                onOpenSeries = { seriesId ->
                    navController.navigate(
                        AcervatimDestinations.sharedSeries(collectionId, seriesId)
                    )
                }
            )
        }
        composable(
            route = AcervatimDestinations.SHARED_SERIES_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.COLLECTION_ID) { type = NavType.StringType },
                navArgument(AcervatimNavArgs.SERIES_ID) { type = NavType.StringType }
            )
        ) {
            SharedSeriesDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenVolume = { itemId ->
                    navController.navigate(AcervatimDestinations.sharedItem(itemId))
                }
            )
        }
        composable(
            route = AcervatimDestinations.SHARED_ITEM_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.ITEM_ID) { type = NavType.StringType }
            )
        ) {
            // Aucune callback d'édition : une vue partagée n'en propose aucune (le backend la
            // refuserait au guard, l'UI ne la construit pas).
            SharedItemDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = AcervatimDestinations.ADD_RECORD_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.BARCODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(AcervatimNavArgs.PREFILL_KEY) {
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
                    // Un tome de série est toujours un manga → détail manga dédié.
                    navController.navigate(AcervatimDestinations.mangaDetail(id))
                },
                onAddVolume = { seriesId, suggested ->
                    navController.navigate(
                        AcervatimDestinations.addManga(
                            seriesId = seriesId,
                            volumeNumber = suggested
                        )
                    )
                },
                onScanVolume = { seriesId ->
                    navController.navigate(
                        AcervatimDestinations.scan(
                            collectionType = CollectionType.MANGA.name,
                            seriesId = seriesId
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
        composable(
            route = AcervatimDestinations.MANGA_DETAIL_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.RECORD_ID) { type = NavType.StringType }
            )
        ) {
            MangaDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(AcervatimDestinations.editManga(id)) }
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
            route = AcervatimDestinations.EXTERNAL_SEARCH_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.COLLECTION_TYPE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            ExternalSearchScreen(
                onBack = { navController.popBackStack() },
                // Le refus de jeton se lève en reliant la source à son type de collection —
                // c'est là que vit ce parcours, pas sur l'écran de compte.
                onConnectSource = {
                    navController.navigate(AcervatimDestinations.MANAGE_COLLECTION_TYPES_ROUTE)
                },
                // Le pressage retenu rejoint le formulaire par le chemin du scan : même passe-plat,
                // même écran d'arrivée. Deux parcours d'ajout parallèles divergeraient.
                onPressingSelected = { prefillKey ->
                    navController.navigate(AcervatimDestinations.addRecord(prefillKey = prefillKey)) {
                        popUpTo(AcervatimDestinations.EXTERNAL_SEARCH_ROUTE) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = AcervatimDestinations.SCAN_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.COLLECTION_TYPE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(AcervatimNavArgs.SERIES_IMPORT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(AcervatimNavArgs.SERIES_ID) {
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
            // Scan lancé depuis la vue séries : le code-barres résolu ouvre l'écran d'import de série.
            val seriesImport = backStackEntry.arguments
                ?.getString(AcervatimNavArgs.SERIES_IMPORT) == "true"
            // Scan lancé depuis le détail d'une série : on ajoute le tome scanné à CETTE série.
            val targetSeriesId = backStackEntry.arguments?.getString(AcervatimNavArgs.SERIES_ID)
            BarcodeScanScreen(
                onBack = { navController.popBackStack() },
                onExistingRecord = { id ->
                    // Scan en contexte manga (type actif manga, ou ajout à une série) → détail manga.
                    val isManga = scannedType == CollectionType.MANGA || targetSeriesId != null
                    val dest =
                        if (isManga) AcervatimDestinations.mangaDetail(id)
                        else AcervatimDestinations.recordDetail(id)
                    navController.navigate(dest) {
                        popUpTo(AcervatimDestinations.SCAN_ROUTE) { inclusive = true }
                    }
                },
                onNewBarcode = { barcode ->
                    val route = when {
                        targetSeriesId != null ->
                            AcervatimDestinations.addManga(seriesId = targetSeriesId, barcode = barcode)
                        seriesImport -> AcervatimDestinations.seriesImport(barcode)
                        scannedType == CollectionType.MANGA -> AcervatimDestinations.addManga(barcode = barcode)
                        else -> AcervatimDestinations.addRecord(barcode)
                    }
                    navController.navigate(route) {
                        popUpTo(AcervatimDestinations.SCAN_ROUTE) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = AcervatimDestinations.SERIES_IMPORT_ROUTE,
            arguments = listOf(
                navArgument(AcervatimNavArgs.BARCODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            SeriesImportScreen(
                onBack = { navController.popBackStack() },
                onOpenSeries = { seriesId ->
                    navController.navigate(AcervatimDestinations.seriesDetail(seriesId)) {
                        popUpTo(AcervatimDestinations.SERIES_IMPORT_ROUTE) { inclusive = true }
                    }
                },
                onManual = { barcode ->
                    navController.navigate(AcervatimDestinations.addManga(barcode = barcode)) {
                        popUpTo(AcervatimDestinations.SERIES_IMPORT_ROUTE) { inclusive = true }
                    }
                }
            )
        }
    }
}
