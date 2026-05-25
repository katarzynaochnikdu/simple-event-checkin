package pl.medidesk.mobile.core.sync

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.ParticipantTagDefinitionDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WO-MOB-016: in-memory cache kanonicznych definicji tagow uczestnikow.
 *
 * Source of truth: backend /api/mobile/participant-tags (mirror tabeli
 * participant_tag_definitions, ten sam endpoint co desktop "Tagi uczestnikow").
 *
 * Refresh: raz po loginie (wolanie z MainActivity / AppNavHost). Brak polling,
 * brak pull-to-refresh - tagi zmieniaja sie rzadko. Zmiana w desktop propaguje
 * przy nastepnym app start / re-login.
 *
 * Fallback: jesli network error / pierwszy raz - uzywa DEFAULT_TAGS (mirror seedu
 * migracji 0034). UI nigdy nie pokazuje pustego chipa.
 *
 * Data-only: ZERO Compose imports. Color mapping zywie w core-ui (ParticipantTagChip).
 */
@Singleton
class ParticipantTagsRepository @Inject constructor(
    private val api: MobileApiService
) {

    private val _tags = MutableStateFlow<Map<String, ParticipantTagDefinitionDto>>(
        DEFAULT_TAGS.associateBy { it.key }
    )
    val tags: StateFlow<Map<String, ParticipantTagDefinitionDto>> = _tags.asStateFlow()

    /** Fetch z backendu. Fail-soft: error -> zostawia poprzednio scachowany stan. */
    suspend fun refresh() {
        try {
            val response = api.getParticipantTags()
            val body = response.body()
            if (response.isSuccessful && body != null && body.success && body.tags.isNotEmpty()) {
                _tags.value = body.tags.associateBy { it.key }
                Log.d(TAG, "Loaded ${body.tags.size} tag definitions from backend")
            } else {
                Log.w(TAG, "Refresh failed: code=${response.code()}, success=${body?.success} - keeping cached/defaults")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Refresh exception: ${e.message} - keeping cached/defaults")
        }
    }

    /** Definicja taga lub null. UI wybiera fallback strategy. */
    fun definitionFor(key: String): ParticipantTagDefinitionDto? = _tags.value[key]

    /** Zwraca polski label dla danego klucza taga lub humanizowany fallback. */
    fun labelFor(key: String): String {
        val def = _tags.value[key]
        if (def != null) return def.labelPl
        // Fallback: snake_case -> Title Case
        return key.split('_')
            .joinToString(" ") { word ->
                if (word.isEmpty()) word
                else word.replaceFirstChar { it.titlecase() }
            }
    }

    companion object {
        private const val TAG = "ParticipantTagsRepo"

        /**
         * Mirror seedu z database/migrations/0034_participant_tag_definitions.sql
         * + frontend/src/hooks/useParticipantTags.ts:61-72.
         * SYNC: zmiana seedu -> aktualizuj 3 miejsca (DB migration + frontend defaults + tu).
         */
        val DEFAULT_TAGS: List<ParticipantTagDefinitionDto> = listOf(
            ParticipantTagDefinitionDto("uczestnik", "Uczestnik", "bg-blue-500/10", "text-blue-700", 10, true),
            ParticipantTagDefinitionDto("prelegent", "Prelegent", "bg-purple-500/10", "text-purple-700", 20, true),
            ParticipantTagDefinitionDto("przedstawiciel_partnera", "Przedstawiciel partnera", "bg-orange-500/10", "text-orange-700", 30, true),
            ParticipantTagDefinitionDto("organizator", "Organizator", "bg-indigo-500/10", "text-indigo-700", 40, true),
            ParticipantTagDefinitionDto("vip", "VIP", "bg-amber-500/10", "text-amber-700", 50, true),
            ParticipantTagDefinitionDto("wystawca", "Wystawca", "bg-cyan-500/10", "text-cyan-700", 60, true),
            ParticipantTagDefinitionDto("prasa", "Prasa / Media", "bg-rose-500/10", "text-rose-700", 70, true),
            ParticipantTagDefinitionDto("partner", "Partner", "bg-emerald-500/10", "text-emerald-700", 80, true),
            ParticipantTagDefinitionDto("osoba_towarzyszaca", "Osoba towarzysząca", "bg-slate-500/10", "text-slate-700", 90, true),
            ParticipantTagDefinitionDto("inne", "Inne", "bg-gray-500/10", "text-gray-600", 100, true),
        )
    }
}
