package pl.medidesk.mobile.core.model

/**
 * WO-MOB-037: jeden aktywny bilet osoby (klasa + nazwa).
 * Apka pokazuje listę po skanie; jeden QR nadal należy do osoby, nie do biletu.
 */
data class TicketEntitlement(
    val ticketName: String,
    val isPrimary: Boolean = false,
    val checkedIn: Boolean = false
)

fun entitlementDisplayNames(
    tickets: List<TicketEntitlement>,
    fallbackTicketName: String?
): List<String> {
    val names = tickets.map { it.ticketName.trim() }.filter { it.isNotBlank() }.distinct()
    if (names.isNotEmpty()) return names
    val fallback = fallbackTicketName?.trim().orEmpty()
    return if (fallback.isNotBlank()) listOf(fallback) else emptyList()
}

/** Stan biletu, za który dopłata jeszcze nie dotarła (kontrakt backendu). */
const val TICKET_PAYMENT_AWAITING = "awaiting_payment"

/**
 * Nazwy biletów czekających na opłacenie — do POKAZANIA, nigdy do wpuszczenia.
 *
 * Bierzemy tylko pozycje o stanie `awaiting_payment`; nieznany stan pomijamy,
 * żeby przyszły stan płatności nie trafił na ekran jako „czeka na opłacenie".
 */
fun pendingTicketDisplayNames(names: List<Pair<String?, String?>>): List<String> =
    names.mapNotNull { (ticketName, paymentState) ->
        val name = ticketName?.trim().orEmpty()
        val state = paymentState?.trim().orEmpty()
        if (name.isBlank()) null
        else if (!state.equals(TICKET_PAYMENT_AWAITING, ignoreCase = true)) null
        else name
    }.distinct()

/**
 * Czy osoba należy do wybranej puli biletowej?
 *
 * Filtr po puli porównywał wyłącznie bilet GŁÓWNY (`ticketClassId`), więc osoba,
 * która dokupiła drugi bilet, znikała z listy przy filtrze po tej drugiej puli.
 * `TicketEntitlement` nie niesie identyfikatora puli (kontrakt sieciowy zwraca
 * tylko nazwę), dlatego dodatkowe bilety dopasowujemy po NAZWIE puli — tej samej,
 * którą operator widzi na chipsie filtra.
 *
 * Zachowanie bez zmian, gdy: brak filtra, osoba ma jeden bilet, lista biletów jest
 * pusta (wtedy liczy się sam bilet główny) albo nie znamy nazwy wybranej puli.
 */
fun participantMatchesTicketPool(
    primaryTicketClassId: String?,
    ticketNames: List<String>,
    selectedTicketClassId: String?,
    selectedTicketClassName: String?
): Boolean {
    if (selectedTicketClassId == null) return true
    if (primaryTicketClassId == selectedTicketClassId) return true
    val poolName = selectedTicketClassName?.trim().orEmpty()
    if (poolName.isBlank()) return false
    return ticketNames.any { it.trim().equals(poolName, ignoreCase = true) }
}

/**
 * Minimalny JSON tablicy obiektów — bez org.json, da się testować na JVM.
 * Pola kontrolowane przez nas (nazwa biletu z katalogu).
 */
fun encodeTicketEntitlements(tickets: List<TicketEntitlement>): String? {
    if (tickets.isEmpty()) return null
    return tickets.joinToString(prefix = "[", postfix = "]") { item ->
        val name = escapeJsonString(item.ticketName)
        """{"ticket_name":"$name","is_primary":${item.isPrimary},"checked_in":${item.checkedIn}}"""
    }
}

fun decodeTicketEntitlements(raw: String?): List<TicketEntitlement> {
    if (raw.isNullOrBlank()) return emptyList()
    val body = raw.trim()
    if (!body.startsWith("[") || !body.endsWith("]")) return emptyList()
    val inner = body.substring(1, body.length - 1).trim()
    if (inner.isEmpty()) return emptyList()
    return splitJsonObjects(inner).mapNotNull { obj ->
        val name = readJsonStringField(obj, "ticket_name")?.trim().orEmpty()
        if (name.isBlank()) return@mapNotNull null
        TicketEntitlement(
            ticketName = name,
            isPrimary = readJsonBooleanField(obj, "is_primary"),
            checkedIn = readJsonBooleanField(obj, "checked_in")
        )
    }
}

private fun escapeJsonString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

private fun splitJsonObjects(inner: String): List<String> {
    val out = mutableListOf<String>()
    var depth = 0
    var start = -1
    inner.forEachIndexed { index, ch ->
        if (ch == '{') {
            if (depth == 0) start = index
            depth++
        } else if (ch == '}') {
            depth--
            if (depth == 0 && start >= 0) {
                out.add(inner.substring(start, index + 1))
                start = -1
            }
        }
    }
    return out
}

private fun readJsonStringField(obj: String, key: String): String? {
    val needle = "\"$key\""
    val keyAt = obj.indexOf(needle)
    if (keyAt < 0) return null
    val colon = obj.indexOf(':', keyAt + needle.length)
    if (colon < 0) return null
    val firstQuote = obj.indexOf('"', colon + 1)
    if (firstQuote < 0) return null
    val builder = StringBuilder()
    var i = firstQuote + 1
    while (i < obj.length) {
        val ch = obj[i]
        if (ch == '\\' && i + 1 < obj.length) {
            builder.append(obj[i + 1])
            i += 2
            continue
        }
        if (ch == '"') return builder.toString()
        builder.append(ch)
        i++
    }
    return null
}

private fun readJsonBooleanField(obj: String, key: String): Boolean {
    val needle = "\"$key\""
    val keyAt = obj.indexOf(needle)
    if (keyAt < 0) return false
    val colon = obj.indexOf(':', keyAt + needle.length)
    if (colon < 0) return false
    var i = colon + 1
    while (i < obj.length && obj[i].isWhitespace()) i++
    return obj.startsWith("true", i)
}
