// Ported from Sora (github.com/shizenworks/sora)'s Internet page. Licensed under GPL-3.0.

// DNS over HTTPS and the proxy, read from the settings on every connection, so changing them takes effect for every
// client built from the shared one without rebuilding any of them.

package com.metrolist.shared.network

import com.metrolist.shared.settings.Settings
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/** The DNS-over-HTTPS providers the Internet page offers, with the addresses they're reached at first. */
enum class DnsOverHttpsProvider(
    val url: String,
    val bootstrap: List<String>,
) {
    CLOUDFLARE("https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    QUAD9("https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
    ADGUARD("https://dns.adguard-dns.com/dns-query", listOf("94.140.14.14", "94.140.15.15")),
}

internal object SettingsDns : Dns {
    // the resolver for the last provider asked for, rebuilt only when the setting changes
    @Volatile
    private var cached: Pair<String, Dns>? = null
    private val bootstrapClient by lazy { OkHttpClient() }

    override fun lookup(hostname: String): List<InetAddress> {
        val resolver = resolver() ?: return Dns.SYSTEM.lookup(hostname)
        // a provider that can't answer leaves the phone's own DNS to it, rather than every request failing
        return try {
            resolver.lookup(hostname)
        } catch (_: IOException) {
            Dns.SYSTEM.lookup(hostname)
        }
    }

    private fun resolver(): Dns? {
        val choice = Settings.shared.dnsOverHttpsProvider
        val key = if (choice == "CUSTOM") "CUSTOM:" + Settings.shared.dnsOverHttpsCustomUrl else choice
        cached?.let { (cachedKey, dns) -> if (cachedKey == key) return dns }
        val built = build(choice) ?: return null
        cached = key to built
        return built
    }

    private fun build(choice: String): Dns? {
        val provider = DnsOverHttpsProvider.entries.firstOrNull { it.name == choice }
        val url = (provider?.url ?: Settings.shared.dnsOverHttpsCustomUrl.takeIf { choice == "CUSTOM" })?.toHttpUrlOrNull() ?: return null
        return DnsOverHttps
            .Builder()
            .client(bootstrapClient)
            .url(url)
            .apply {
                val hosts = provider?.bootstrap.orEmpty().mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
                if (hosts.isNotEmpty()) bootstrapDnsHosts(hosts)
            }.build()
    }
}

internal object SettingsProxySelector : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        val settings = Settings.shared
        val type =
            when (settings.proxyType) {
                "HTTP" -> Proxy.Type.HTTP
                "SOCKS" -> Proxy.Type.SOCKS
                else -> return listOf(Proxy.NO_PROXY)
            }
        if (settings.proxyHost.isBlank() || settings.proxyPort !in 1..65535) return listOf(Proxy.NO_PROXY)
        return listOf(Proxy(type, InetSocketAddress.createUnresolved(settings.proxyHost, settings.proxyPort)))
    }

    override fun connectFailed(
        uri: URI?,
        sa: SocketAddress?,
        ioe: IOException?,
    ) = Unit
}
