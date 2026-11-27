package com.metrolist.shared.remote

import android.security.NetworkSecurityPolicy

// the app's network security config, which refuses cleartext
actual fun remotePlainHttpAllowed(host: String): Boolean = NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host)
