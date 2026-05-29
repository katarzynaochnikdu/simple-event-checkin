package pl.medidesk.mobile.core.testing.http

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

/**
 * Placeholder [Dispatcher] for MockWebServer-backed network tests.
 *
 * Faza 0 (WO-MOB-024): returns an empty HTTP 200 for every request — a safe default so a test
 * that forgets to register a path still gets a well-formed response instead of a connection error.
 *
 * Faza 3: replace with a path → response map (e.g. matching on [RecordedRequest.path] /
 * [RecordedRequest.method]) and JSON fixture bodies so the Mobile API contract can be exercised
 * offline.
 */
class MockApiDispatcher : Dispatcher() {

    override fun dispatch(request: RecordedRequest): MockResponse {
        // Faza 3: branch on request.path / request.method and return fixture bodies.
        return MockResponse().setResponseCode(HTTP_OK).setBody("")
    }

    private companion object {
        const val HTTP_OK = 200
    }
}
