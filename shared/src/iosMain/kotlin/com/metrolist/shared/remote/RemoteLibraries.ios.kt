package com.metrolist.shared.remote

actual val remoteLibrariesSupported: Boolean = false

actual fun remotePlainHttpAllowed(host: String): Boolean = false

actual suspend fun listRemoteFolder(
    server: RemoteServer,
    url: String,
): Result<List<RemoteEntry>> = Result.failure(UnsupportedOperationException("Remote libraries aren't available on iOS"))
