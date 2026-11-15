package net.digitalbc.leafreader.data.jellyfin

import okhttp3.Dns

/** Checks the same resolved addresses OkHttp will connect to, including fallback routes. */
internal class LocalNetworkDns(private val delegate: Dns) : Dns {
    override fun lookup(hostname: String) = delegate.lookup(hostname).also { addresses ->
        if (addresses.isEmpty() || addresses.any { address ->
                val bytes = address.address
                val uniqueLocalV6 = bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
                address.isAnyLocalAddress || address.isMulticastAddress ||
                    !(address.isLoopbackAddress || address.isLinkLocalAddress ||
                        address.isSiteLocalAddress || uniqueLocalV6)
            }) {
            throw JellyfinException(JellyfinFailureKind.INVALID_CONFIGURATION,
                "Unencrypted Jellyfin HTTP requires only local destination addresses")
        }
    }
}
