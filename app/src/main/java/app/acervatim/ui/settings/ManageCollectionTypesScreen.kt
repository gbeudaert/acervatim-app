package app.acervatim.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.CollectionType
import app.acervatim.ui.theme.AcervatimTheme

@Composable
fun ManageCollectionTypesScreen(
    onBack: () -> Unit,
    viewModel: ManageCollectionTypesViewModel = hiltViewModel()
) {
    val items by viewModel.uiState.collectAsStateWithLifecycle()
    ManageCollectionTypesContent(
        items = items,
        onVisibilityChange = viewModel::onVisibilityChange,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageCollectionTypesContent(
    items: List<CollectionTypeVisibilityUiState>,
    onVisibilityChange: (CollectionType, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_types_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.manage_types_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            items.forEach { item ->
                CollectionTypeRow(
                    item = item,
                    onVisibilityChange = { visible -> onVisibilityChange(item.type, visible) }
                )
            }
        }
    }
}

@Composable
private fun CollectionTypeRow(
    item: CollectionTypeVisibilityUiState,
    onVisibilityChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = item.type.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(item.type.labelRes()),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.manage_types_item_count, item.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = item.visible,
            onCheckedChange = onVisibilityChange,
            enabled = item.toggleEnabled
        )
    }
}

private fun CollectionType.labelRes(): Int = when (this) {
    CollectionType.VINYL -> R.string.collection_type_vinyl
    CollectionType.MANGA -> R.string.collection_type_manga
}

private fun CollectionType.icon(): ImageVector = when (this) {
    CollectionType.VINYL -> Icons.Filled.Album
    CollectionType.MANGA -> Icons.Filled.Book
}

@Preview(showBackground = true)
@Composable
private fun ManageCollectionTypesPreview() {
    AcervatimTheme {
        ManageCollectionTypesContent(
            items = listOf(
                CollectionTypeVisibilityUiState(CollectionType.VINYL, visible = true, itemCount = 128, toggleEnabled = true),
                CollectionTypeVisibilityUiState(CollectionType.MANGA, visible = false, itemCount = 0, toggleEnabled = true)
            ),
            onVisibilityChange = { _, _ -> },
            onBack = {}
        )
    }
}
