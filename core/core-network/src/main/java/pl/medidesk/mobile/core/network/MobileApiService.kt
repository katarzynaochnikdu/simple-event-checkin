package pl.medidesk.mobile.core.network

import pl.medidesk.mobile.core.network.dto.*
import retrofit2.Response
import retrofit2.http.*

interface MobileApiService {

    // Auth
    @POST("api/mobile/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/mobile/me")
    suspend fun me(): Response<UserDto>

    // Events
    @GET("api/mobile/events")
    suspend fun getEvents(): Response<EventsResponse>

    // Participants
    @GET("api/mobile/events/{eventId}/participants")
    suspend fun getParticipants(
        @Path("eventId") eventId: String,
        @Query("since") since: String? = null
    ): Response<ParticipantsResponse>

    // Ticket classes
    @GET("api/mobile/events/{eventId}/ticket-classes")
    suspend fun getTicketClasses(@Path("eventId") eventId: String): Response<TicketClassesResponse>

    // Check-in
    @POST("api/mobile/checkin")
    suspend fun checkin(@Body request: CheckinRequest): Response<CheckinResponse>

    @POST("api/mobile/checkin/undo")
    suspend fun undoCheckin(@Body request: UndoCheckinRequest): Response<CheckinResponse>

    @POST("api/mobile/checkin/sync")
    suspend fun syncCheckins(@Body request: CheckinSyncRequest): Response<CheckinSyncResponse>

    // Stats & Dashboard
    @GET("api/mobile/events/{eventId}/checkin-stats")
    suspend fun getCheckinStats(@Path("eventId") eventId: String): Response<CheckinStatsResponse>

    @GET("api/mobile/events/{eventId}/dashboard")
    suspend fun getDashboard(@Path("eventId") eventId: String): Response<DashboardResponse>

    // Walk-in
    @POST("api/mobile/walkin")
    suspend fun createWalkin(@Body request: WalkinRequest): Response<WalkinResponse>

    @POST("api/mobile/walkin/batch")
    suspend fun syncWalkins(@Body request: WalkinBatchRequest): Response<WalkinBatchResponse>

    @GET("api/mobile/events/{eventId}/walkins")
    suspend fun getWalkins(@Path("eventId") eventId: String): Response<WalkinsListResponse>

    // InHub
    @GET("api/mobile/events/{eventId}/inhub-config")
    suspend fun getInHubConfig(@Path("eventId") eventId: String): Response<InHubConfigResponse>

    @POST("api/mobile/events/{eventId}/inhub-config")
    suspend fun saveInHubConfig(
        @Path("eventId") eventId: String,
        @Body request: InHubConfigRequest
    ): Response<InHubConfigResponse>

    @POST("api/mobile/events/{eventId}/inhub/verify-pin")
    suspend fun verifyPin(
        @Path("eventId") eventId: String,
        @Body request: VerifyPinRequest
    ): Response<VerifyPinResponse>

    // GUS lookup
    @GET("api/mobile/gus/lookup/{nip}")
    suspend fun gusLookup(@Path("nip") nip: String): Response<GusLookupResponse>

    // Speakers
    @GET("api/mobile/events/{eventId}/speakers")
    suspend fun getSpeakers(@Path("eventId") eventId: String): Response<SpeakersResponse>

    @GET("api/mobile/events/{eventId}/speakers/{speakerId}")
    suspend fun getSpeakerDetail(
        @Path("eventId") eventId: String,
        @Path("speakerId") speakerId: String
    ): Response<SpeakerDto>

    // Sponsors
    @GET("api/mobile/events/{eventId}/sponsors")
    suspend fun getSponsors(@Path("eventId") eventId: String): Response<EventSponsorsResponse>

    @GET("api/mobile/events/{eventId}/sponsors/{eventSponsorId}")
    suspend fun getSponsorDetail(
        @Path("eventId") eventId: String,
        @Path("eventSponsorId") eventSponsorId: Long
    ): Response<SponsorDetailResponse>

    // Companies
    @GET("api/mobile/events/{eventId}/companies")
    suspend fun getCompanies(
        @Path("eventId") eventId: String,
        @Query("role") role: String = "all"
    ): Response<CompaniesResponse>

    // Orders
    @GET("api/mobile/events/{eventId}/orders")
    suspend fun getOrders(
        @Path("eventId") eventId: String
    ): Response<OrdersResponse>

    @POST("api/mobile/orders/{orderId}/status")
    suspend fun updateOrderStatus(
        @Path("orderId") orderId: String,
        @Body request: OrderStatusUpdateRequest
    ): Response<GenericActionResponse>

    @POST("api/mobile/orders/{orderId}/resend-ticket")
    suspend fun resendTickets(
        @Path("orderId") orderId: String
    ): Response<GenericActionResponse>

    @POST("api/mobile/orders/{orderId}/send-reminder")
    suspend fun sendReminder(
        @Path("orderId") orderId: String
    ): Response<GenericActionResponse>

    // Image Upload
    @Multipart
    @POST("api/mobile/upload-image")
    suspend fun uploadImage(
        @Part image: okhttp3.MultipartBody.Part,
        @Part("context") context: okhttp3.RequestBody,
    ): Response<ImageUploadResponse>
}

