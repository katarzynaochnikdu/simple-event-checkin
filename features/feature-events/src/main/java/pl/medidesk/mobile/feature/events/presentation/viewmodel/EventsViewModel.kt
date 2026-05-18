package pl.medidesk.mobile.feature.events.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.model.EventItem
import pl.medidesk.mobile.feature.events.domain.repository.EventsRepository
import pl.medidesk.mobile.feature.events.presentation.screen.parseToDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

enum class EventTab { UPCOMING, PAST, SANDBOX }

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
    val selectedTab: EventTab = EventTab.UPCOMING
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventsRepository: EventsRepository
) : ViewModel() {

    private val _rawEvents = MutableStateFlow<List<EventItem>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTab = MutableStateFlow(EventTab.UPCOMING)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<EventsUiState> = combine(
        _rawEvents, _searchQuery, _selectedTab, _isLoading, _error
    ) { raw, query, tab, loading, err ->
        
        val now = LocalDateTime.now()

        // 1. Filtracja po wyszukiwaniu
        var filtered = raw.filter { event ->
            query.isBlank() || 
            event.eventName.contains(query, ignoreCase = true) || 
            event.venue.contains(query, ignoreCase = true)
        }

        // 2. Podział na grupy (Nadchodzące, Przeszłe, Sandbox)
        filtered = when (tab) {
            EventTab.SANDBOX -> {
                filtered.filter { isSandbox(it) }
            }
            EventTab.UPCOMING -> {
                filtered.filter { !isSandbox(it) && !isPast(it, now) }
            }
            EventTab.PAST -> {
                filtered.filter { !isSandbox(it) && isPast(it, now) }
            }
        }

        // 3. Sortowanie: Najbliższe wydarzenia na górze (ascending dla Upcoming/Sandbox, descending dla Past)
        filtered = if (tab == EventTab.PAST) {
            filtered.sortedByDescending { parseToDateTime(it.startDate) }
        } else {
            filtered.sortedBy { parseToDateTime(it.startDate) }
        }

        // 4. Grupowanie po miesiącach (zachowując sortowanie)
        // Używamy mianownika ręcznie — Locale("pl") + MMMM zwraca dopełniacz (maja, czerwca).
        val monthNominative = listOf("", "Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
            "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień")
        val grouped = filtered.groupBy { event ->
            try {
                val date = parseToDateTime(event.startDate)
                "${monthNominative[date.monthValue]} ${date.year}"
            } catch (e: Exception) { "Inne" }
        }.map { (month, events) -> UiEventGroup(month, events) }

        EventsUiState(loading, grouped, filtered.size, err, query, tab)
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

    private fun isPast(event: EventItem, now: LocalDateTime): Boolean {
        val endDate = parseToDateTime(event.endDate ?: event.startDate)
        return endDate.isBefore(now)
    }
}
