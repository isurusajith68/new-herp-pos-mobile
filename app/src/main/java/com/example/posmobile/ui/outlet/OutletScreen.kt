package com.example.posmobile.ui.outlet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posmobile.data.Container
import com.example.posmobile.data.PosLocation
import com.example.posmobile.data.Property
import com.example.posmobile.ui.Outlet
import com.example.posmobile.ui.SessionViewModel
import com.example.posmobile.ui.theme.BrandBlue
import com.example.posmobile.ui.theme.BrandBlueDark
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutletScreen(
    session: SessionViewModel,
    onOutletChosen: () -> Unit,
    onOpenPrinter: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val pos = Container.pos
    val scope = rememberCoroutineScope()

    var properties by remember { mutableStateOf<List<Property>?>(null) }
    var selectedProperty by remember { mutableStateOf<Property?>(null) }
    var locations by remember { mutableStateOf<List<PosLocation>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadProperties() {
        error = null
        properties = null
        scope.launch {
            try {
                properties = pos.properties().filter { it.hasPos }
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load workspaces. Pull to retry."
                properties = emptyList()
            }
        }
    }

    fun openProperty(p: Property) {
        selectedProperty = p
        locations = null
        error = null
        scope.launch {
            try {
                locations = pos.locations(p.slug)
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load outlets."
                locations = emptyList()
            }
        }
    }

    LaunchedEffect(Unit) { loadProperties() }

    val inLocations = selectedProperty != null

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Hero(
                inLocations = inLocations,
                propertyName = selectedProperty?.name,
                onBack = { selectedProperty = null; locations = null; error = null },
                onProfile = onOpenProfile,
                onPrinter = onOpenPrinter,
                onRefresh = {
                    if (inLocations) openProperty(selectedProperty!!) else loadProperties()
                },
            )

            val loading = if (inLocations) locations == null else properties == null

            AnimatedContent(
                targetState = inLocations,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 3 } + fadeOut())
                    }
                },
                label = "outlet-step",
            ) { showLocations ->
                when {
                    loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                    showLocations -> OutletGrid(
                        error = error,
                        empty = locations.orEmpty().isEmpty() && error == null,
                        emptyText = "No active outlets in this property yet.",
                        onRetry = { openProperty(selectedProperty!!) },
                        items = locations.orEmpty(),
                        title = { it.name },
                        subtitle = { it.description },
                        onClick = { loc ->
                            session.selectOutlet(
                                Outlet(
                                    propertySlug = selectedProperty!!.slug,
                                    propertyName = selectedProperty!!.name,
                                    locationId = loc.id,
                                    locationName = loc.name,
                                ),
                            )
                            onOutletChosen()
                        },
                    )

                    else -> OutletGrid(
                        error = error,
                        empty = properties.orEmpty().isEmpty() && error == null,
                        emptyText = "No POS-enabled properties for this account.",
                        onRetry = { loadProperties() },
                        items = properties.orEmpty(),
                        title = { it.name },
                        subtitle = { "@${it.slug}" },
                        onClick = { openProperty(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Hero(
    inLocations: Boolean,
    propertyName: String?,
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onPrinter: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(BrandBlue, BrandBlueDark)))
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (inLocations) {
                HeroIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Spacer(Modifier.size(4.dp))
            }
            Text(
                if (inLocations) "Outlets" else "Workspaces",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            HeroIcon(Icons.Filled.Refresh, "Refresh", onRefresh)
            HeroIcon(Icons.Filled.Print, "Printer settings", onPrinter)
            HeroIcon(Icons.Filled.AccountCircle, "Profile", onProfile)
        }

        Spacer(Modifier.height(18.dp))
        Text(
            if (inLocations) (propertyName ?: "Select an outlet") else greeting(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (inLocations) "Pick where you're taking orders" else "Choose a property to start taking orders",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun HeroIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = desc, tint = Color.White)
    }
}

@Composable
private fun <T> OutletGrid(
    error: String?,
    empty: Boolean,
    emptyText: String,
    onRetry: () -> Unit,
    items: List<T>,
    title: (T) -> String,
    subtitle: (T) -> String?,
    onClick: (T) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(20.dp),
            )
        }
        if (empty) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { item ->
                OutletCard(
                    title = title(item),
                    subtitle = subtitle(item),
                    onClick = { onClick(item) },
                )
            }
        }
    }
}

@Composable
private fun OutletCard(title: String, subtitle: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(18.dp),
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarTile(title)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AvatarTile(name: String) {
    Box(
        Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val initials = name.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .let { parts ->
                when {
                    parts.size >= 2 -> "${parts.first().first()}${parts[1].first()}"
                    parts.isNotEmpty() -> parts.first().take(2)
                    else -> "?"
                }
            }
            .uppercase()
        Text(
            initials,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Welcome back"
}
