package dev.huecoder.md3agent.feature.settings

import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.net.UrlPolicy
import dev.huecoder.md3agent.ui.text.UiText
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Field error for an endpoint URL: unparseable, or plain http to a public host while it would carry a key or headers. */
internal fun endpointUrlError(url: String, carriesSecrets: Boolean, invalid: Int): UiText? {
    if (url.isBlank()) return null
    val parsed = url.trim().toHttpUrlOrNull() ?: return UiText.Resource(invalid)
    return if (carriesSecrets && !UrlPolicy.allowsCredentials(parsed)) UiText.Resource(R.string.endpoint_https_required) else null
}
