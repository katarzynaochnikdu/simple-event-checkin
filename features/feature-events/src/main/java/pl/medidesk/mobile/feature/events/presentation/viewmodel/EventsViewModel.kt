package pl.medidesk.mobile.feature.events.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.model.EventItem
import pl.medidesk.mobile.feature.events.domain.repository.EventsRepository
import pl.medidesk.mobile.feature.events.presentation.screen.parseToDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

enum class EventTab { ONGOING, UPCOMING, PAST, SANDBOX }

data class UiEventGroup(
    val monthYear: String,
    val events: List<EventItem>
)

data class EventsUiState(
    val isLoading: Boolean = false,
    val groupedEvents: List<UiEventGroup> = emptyList(),
    val totalActiveEvents: Int = 0,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedTab: EventTab = EventTab.UPCOMING,
    val visibleTabs: List<EventTab> = listOf(EventTab.UPCOMING, EventTab.PAST)
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventsRepository: EventsRepository
) : ViewModel() {

    private val _rawEvents = MutableStateFlow<List<EventItem>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    // null = user nie wybrał ręcznie żadnej zakładki → podążaj za domyślną sterowaną danymi
    private val _selectedTab = MutableStateFlow<EventTab?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<EventsUiState> = combine(
        _rawEvents, _searchQuery, _selectedTab, _isLoading, _error
    ) { raw, query, tab, loading, err ->

        val today = LocalDate.now()

        // 1. Filtracja po wyszukiwaniu (BEZ ZMIAN — nazwa/miejsce zawiera frazę)
        var filtered = raw.filter { event ->
            query.isBlank() ||
            event.eventName.contains(query, ignoreCase = true) ||
            event.venue.contains(query, ignoreCase = true)
        }

        // 2. Widoczne zakładki liczone z RAW (nie z listy po search) — zakładki
        //    nie migoczą podczas wpisywania frazy w wyszukiwarce.
        val anyOngoing = raw.any { !isSandbox(it) && isOngoing(it, today) }
        val isDev = pl.medidesk.mobile.feature.events.BuildConfig.DEBUG
        val visibleTabs = buildList {
            if (anyOngoing) add(EventTab.ONGOING)
            add(EventTab.UPCOMING)
            add(EventTab.PAST)
            if (isDev) add(EventTab.SANDBOX)
        }
        val defaultTab = if (anyOngoing) EventTab.ONGOING else EventTab.UPCOMING
        val effectiveTab = tab?.takeIf { it in visibleTabs } ?: defaultTab

        // 3. Podział na grupy (Trwające, Nadchodzące, Przeszłe, Sandbox)
        filtered = when (effectiveTab) {
            EventTab.SANDBOX  -> filtered.filter { isSandbox(it) }
            EventTab.ONGOING  -> filtered.filter { !isSandbox(it) && isOngoing(it, today) }
            EventTab.UPCOMING -> filtered.filter { !isSandbox(it) && isUpcoming(it, today) }
            EventTab.PAST     -> filtered.filter { !isSandbox(it) && isPast(it, today) }
        }

        // 4. Sortowanie: Najbliższe wydarzenia na górze (ascending dla Trwające/Upcoming/Sandbox, descending dla Past)
        filtered = if (effectiveTab == EventTab.PAST) {
            filtered.sortedByDescending { parseToDateTime(it.startDate) }
        } else {
            filtered.sortedBy { parseToDateTime(it.startDate) }
        }

        // 5. Grupowanie po miesiącach (zachowując sortowanie)
        // Używamy mianownika ręcznie — Locale("pl") + MMMM zwraca dopełniacz (maja, czerwca).
        val monthNominative = listOf("", "Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
            "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień")
        val grouped = filtered.groupBy { event ->
            try {
                val date = parseToDateTime(event.startDate)
                "${monthNominative[date.monthValue]} ${date.year}"
            } catch (e: Exception) { "Inne" }
        }.map { (month, events) -> UiEventGroup(month, events) }

        EventsUiState(loading, grouped, filtered.size, err, query, effectiveTab, visibleTabs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EventsUiState(isLoading = true))

    init { loadEvents() }

    fun loadEvents() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = eventsRepository.getEvents()
            if (result.isSuccess) {
                _rawEvents.value = result.getOrThrow()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Błąd pobierania wydarzeń"
            }
            _isLoading.value = false
        }
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun onTabSelected(tab: EventTab) { _selectedTab.value = tab }

    private fun isSandbox(event: EventItem): Boolean {
        val name = event.eventName.lowercase()
        return name.contains("sandbox") || name.contains("test") || event.status?.lowercase() == "draft"
    }

    // Klasyfikacja data-granularna (ignoruje godziny) — czyste, deterministyczne funkcje
    // przyjmujące `today: LocalDate`, gotowe pod test jednostkowy.
    private fun startDay(e: EventItem): LocalDate = parseToDateTime(e.startDate).toLocalDate()
    private fun endDay(e: EventItem): LocalDate = parseToDateTime(e.endDate.ifBlank { e.startDate }).toLocalDate()
    internal fun isOngoing(e: EventItem, today: LocalDate): Boolean = !today.isBefore(startDay(e)) && !today.isAfter(endDay(e))
    internal fun isUpcoming(e: EventItem, today: LocalDate): Boolean = today.isBefore(startDay(e))
    internal fun isPast(e: EventItem, today: LocalDate): Boolean = today.isAfter(endDay(e))
}
