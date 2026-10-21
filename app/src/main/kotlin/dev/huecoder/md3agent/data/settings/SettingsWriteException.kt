package dev.huecoder.md3agent.data.settings

/** A settings write was refused because the stored value could not be read back; nothing was written. */
class SettingsWriteException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
