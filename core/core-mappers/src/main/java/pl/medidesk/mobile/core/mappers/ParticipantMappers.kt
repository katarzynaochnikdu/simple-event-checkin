package pl.medidesk.mobile.core.mappers

import pl.medidesk.mobile.core.database.entities.ParticipantEntity
import pl.medidesk.mobile.core.model.Participant
import pl.medidesk.mobile.core.network.dto.ParticipantDto

/**
 * WO-MOB-004 (2026-05-19): unified mappers for Participant across layers.
 *
 * Replaces 4 inline mappings previously scattered in:
 * - core/core-sync/SyncWorker.kt:pullParticipants() (DTO→Entity, lines 141-173)
 * - features/feature-participants/ParticipantDetailsViewModel.kt:toParticipant() (Entity→Domain, lines 158-186)
 * - features/feature-participants/ParticipantDetailsViewModel.kt:loadParticipant() (Entity→Domain legacy, lines 195-228)
 * - features/feature-participants/ParticipantsViewModel.kt (Entity→Domain list, lines 56-86)
 *
 * Architecture decision (Option A — new Gradle module `core-mappers`):
 *   Self-contained module depending on core-model + core-network + core-database.
 *   Rationale: clean separation, no need to add cross-layer deps in core-network
 *   (which would mix network with DB concerns) or core-database (mixing DB with DTO).
 *   See ADR in decision_log.md.
 *
 * Subtle bug fix bundled in this refactor:
 *   The two Entity→Domain mappings inside `ParticipantDetailsViewModel` previously
 *   OMITTED the `phone` field while `ParticipantsViewModel` (list) included it.
 *   Result: details screen displayed phone=null even when DB had it.
 *   Unified `toDomain()` includes phone — matches list-view behavior (canonical).
 *
 * Note on `tags`:
 *   - DTO → Entity: comma-join (List<String>? → String?)
 *   - Entity → Domain: comma-split with blank filter (String? → List<String>)
 */

fun ParticipantDto.toEntity(eventId: String): ParticipantEntity = ParticipantEntity(
    id = id,
    ticketId = ticketId,
    ticketNumber = ticketNumber,
    backstageTicketId = backstageTicketId,
    firstName = firstName,
    lastName = lastName,
    email = email,
    phone = phone,
    company = company,
    ticketClassId = ticketClassId,
    ticketName = ticketName,
    status = status,
    attendanceStatus = attendanceStatus,
    eventOrderId = eventOrderId,
    eventId = eventId,
    checkedInAt = checkedInAt,
    orderStatus = orderStatus,
    isWalkin = isWalkin,
    tags = tags?.joinToString(","),
    buyerName = buyerName,
    buyerEmail = buyerEmail,
    paymentMethod = paymentMethod,
    purchaserNip = purchaserNip,
    purchaserCompany = purchaserCompany,
    orderParticipantsTotal = orderParticipantsTotal,
    orderParticipantsCheckedIn = orderParticipantsCheckedIn,
    rsvpSent = rsvpSent,
    rsvpResponse = rsvpResponse,
    rsvpRespondedAt = rsvpRespondedAt
)

fun ParticipantEntity.toDomain(): Participant = Participant(
    id = id,
    ticketId = ticketId,
    backstageTicketId = backstageTicketId,
    firstName = firstName,
    lastName = lastName,
    email = email,
    phone = phone,
    company = company,
    ticketClassId = ticketClassId,
    ticketName = ticketName,
    status = status,
    attendanceStatus = attendanceStatus,
    eventOrderId = eventOrderId,
    eventId = eventId,
    checkedInAt = checkedInAt,
    orderStatus = orderStatus,
    isWalkin = isWalkin,
    tags = tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    buyerName = buyerName,
    buyerEmail = buyerEmail,
    paymentMethod = paymentMethod,
    purchaserNip = purchaserNip,
    purchaserCompany = purchaserCompany,
    orderParticipantsTotal = orderParticipantsTotal,
    orderParticipantsCheckedIn = orderParticipantsCheckedIn,
    rsvpSent = rsvpSent,
    rsvpResponse = rsvpResponse,
    rsvpRespondedAt = rsvpRespondedAt
)
