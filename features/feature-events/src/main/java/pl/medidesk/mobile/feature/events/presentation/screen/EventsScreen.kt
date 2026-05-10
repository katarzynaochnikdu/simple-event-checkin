package pl.medidesk.mobile.feature.events.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.medidesk.mobile.core.ui.components.MdAsyncImage
import pl.medidesk.mobile.core.model.EventItem
import pl.medidesk.mobile.core.ui.components.ErrorScreen
import pl.medidesk.mobile.core.ui.components.LoadingScreen
import pl.medidesk.mobile.feature.events.presentation.viewmodel.EventTab
import pl.medidesk.mobile.feature.events.presentation.viewmodel.EventsViewModel
import pl.medidesk.mobile.feature.events.presentation.viewmodel.UiEventGroup
import java.time.LocalDateTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onEventSelected: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: EventsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color(0xFF152C5B))) {
                TopAppBar(
                    title = { Text("Wydarzenia", color = Color.White, fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Ustawienia", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                
                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = { Text("Szukaj po nazwie lub miejscu...", color = Color.White.copy(alpha = 0.7f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        focusedContainerColor = Color.White.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Tabs
                TabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab.ordinal]),
                            color = Color.White
                        )
                    }
                ) {
                    EventTab.entries.forEach { tab ->
                        val label = when(tab) {
                            EventTab.UPCOMING -> "Nadchodzące"
                            EventTab.PAST -> "Przeszłe"
                            EventTab.SANDBOX -> "Sandbox"
                        }
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.onTabSelected(tab) },
                            text = { Text(label, fontSize = 12.sp, fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (uiState.isLoading) {
                LoadingScreen("Pobieranie...")
            } else if (uiState.error != null) {
                ErrorScreen(uiState.error!!, onRetry = viewModel::loadEvents)
            } else if (uiState.groupedEvents.isEmpty()) {
                EmptyState(uiState.searchQuery.isNotEmpty())
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                    uiState.groupedEvents.forEach { group ->
                        item { MonthHeader(group) }

                        items(group.events) { event ->
                            EventCompactCard(event, { onEventSelected(event.eventId) })
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(group: UiEventGroup) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(group.monthYear, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/**
 * Kompaktowa kostka — logo wydarzenia (mała miniaturka po lewej) + dane po prawej.
 * Wysokość ~80dp (połowa starej kostki) — żeby na ekranie zmieściło się więcej eventów.
 * Akcent koloru wydarzenia (primary_color) jako lewy pasek.
 */
@Composable
private fun EventCompactCard(event: EventItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accentColor = parseHexColor(event.primaryColor) ?: MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Lewy kolorowy pasek — akcent koloru wydarzenia
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            // Logo (mała miniaturka)
            // W dark mode: preferuj logoWhiteUrl (logotyp w wersji białej) → fallback do kolorowego
            // Gdy w dark mode brak logoWhiteUrl, dodajemy jasne tło pod kolorowe logo (czytelność)
            val isDark = isSystemInDarkTheme()
            val logoModel = if (isDark) {
                (event.logoWhiteUrl ?: event.logoUrl ?: event.imageUrl)
            } else {
                (event.logoUrl ?: event.imageUrl)
            }
            val needsLightBgFallback = isDark && event.logoWhiteUrl.isNullOrBlank() && !event.logoUrl.isNullOrBlank()
            MdAsyncImage(
                model = logoModel,
                contentDescription = event.eventName,
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (needsLightBgFallback)
                            Modifier.background(Color.White.copy(alpha = 0.95f))
                        else Modifier
                    ),
                contentScale = ContentScale.Fit,
                initials = event.eventName.take(1),
                placeholderColor = Color.Transparent
            )
            // Dane wydarzenia
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = event.eventName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        formatDateLabel(event.startDate),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (event.venue.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            event.venue,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventFullWidthCard(event: EventItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick, 
        modifier = modifier.fillMaxWidth(), 
        shape = RoundedCornerShape(16.dp), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), 
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            MdAsyncImage(
                model = event.imageUrl,
                contentDescription = event.eventName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Crop,
                initials = event.eventName.take(1),
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = event.eventName, 
                    fontWeight = FontWeight.Bold, 
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text(formatDateLabel(event.startDate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (event.venue.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text(event.venue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (event.status != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = event.status.uppercase(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(isSearching: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (isSearching) Icons.Default.SearchOff else Icons.Default.EventBusy,
            null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (isSearching) "Nie znaleziono wydarzeń pasujących do zapytania" else "Brak wydarzeń w tej kategorii",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

fun parseToDateTime(raw: String?): LocalDateTime {
    if (raw.isNullOrBlank()) return LocalDateTime.now()
    val digits = Regex("\\d+").findAll(raw).map { it.value }.toList()
    return try {
        if (digits.size >= 3) {
            val yearIdx = digits.indexOfFirst { it.length == 4 }
            val y = if (yearIdx != -1) digits[yearIdx].toInt() else 2026
            val other = digits.filterIndexed { index, _ -> index != yearIdx }
            
            val (d, m) = if (yearIdx == 0) {
                (other.getOrNull(1)?.toInt() ?: 1) to (other.getOrNull(0)?.toInt() ?: 1)
            } else {
                (other.getOrNull(0)?.toInt() ?: 1) to (other.getOrNull(1)?.toInt() ?: 1)
            }
            
            val h = if (other.size >= 3) other[2].toInt() else 0
            val min = if (other.size >= 4) other[3].toInt() else 0
            LocalDateTime.of(y, m.coerceIn(1, 12), d.coerceIn(1, 31), h.coerceIn(0, 23), min.coerceIn(0, 59))
        } else LocalDateTime.now()
    } catch (e: Exception) { LocalDateTime.now() }
}

/**
 * Bezpieczny parser hex koloru — zwraca null gdy format nieprawidłowy.
 * Akceptuje formaty: "#RRGGBB", "RRGGBB", "#AARRGGBB".
 */
fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.trim().removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> Color(android.graphics.Color.parseColor("#$cleaned"))
            8 -> Color(android.graphics.Color.parseColor("#$cleaned"))
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

fun formatDateLabel(raw: String?): String {
    if (raw.isNullOrBlank()) return "Brak daty"
    val dt = parseToDateTime(raw)
    val months = listOf("", "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia")
    return try {
        val m = months[dt.monthValue]
        val time = String.format(Locale.getDefault(), "%02d:%02d", dt.hour, dt.minute)
        "${dt.dayOfMonth} $m ${dt.year}, $time"
    } catch (e: Exception) { raw ?: "Błąd daty" }
}
