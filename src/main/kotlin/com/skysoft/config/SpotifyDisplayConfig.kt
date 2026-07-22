package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import com.skysoft.features.spotify.SpotifyAuthentication
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import org.lwjgl.glfw.GLFW

class SpotifyDisplayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show your current Spotify playback in-game.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Connect Spotify and configure playback controls.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val settings = SpotifyDisplaySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the Spotify display appearance.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val details = SpotifyDisplayDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(-8, 8, centerY = false).rememberDefault()
}

class SpotifyDisplaySettingsConfig {
    @JvmField
    @field:ConfigOption(
        name = "1. Create Spotify App",
        desc = "Open Spotify's dashboard, create an app, and select Web API when asked which APIs you plan to use.",
    )
    @field:ConfigEditorButton(buttonText = "Open Dashboard")
    val openDashboard = Runnable { SpotifyAuthentication.openDashboard() }

    @JvmField
    @field:ConfigOption(
        name = "2. Redirect URI",
        desc = "Copy this and add it to the app's Redirect URIs before saving the app.",
    )
    @field:ConfigEditorButton(buttonText = "Copy")
    val copyRedirectUri = Runnable { SpotifyAuthentication.copyRedirectUri() }

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "3. Client ID", desc = "Copy the Client ID from your Spotify app and paste it here.")
    @field:ConfigEditorText
    var clientId = ""

    @JvmField
    @field:ConfigOption(name = "4. Connect", desc = "Open Spotify and approve the connection.")
    @field:ConfigEditorButton(buttonText = "Connect")
    val connect = Runnable { SpotifyAuthentication.connect() }

    @JvmField
    @field:ConfigOption(name = "Disconnect", desc = "Disconnect the Spotify account from Skysoft.")
    @field:ConfigEditorButton(buttonText = "Disconnect")
    val disconnect = Runnable { SpotifyAuthentication.disconnect() }

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Playback Controls", desc = "Use the configured keys to control Spotify.")
    @field:ConfigEditorBoolean
    var playbackControls = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Play / Pause Key", desc = "Play or pause Spotify.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    @field:ConfigVisibleIf("playbackControls")
    var playPauseKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Previous Key", desc = "Return to the previous Spotify track.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    @field:ConfigVisibleIf("playbackControls")
    var previousKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Next Key", desc = "Skip to the next Spotify track.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    @field:ConfigVisibleIf("playbackControls")
    var nextKey = GLFW.GLFW_KEY_UNKNOWN
}

class SpotifyDisplayDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Album Artwork", desc = "Show the current album artwork.")
    @field:ConfigEditorBoolean
    var albumArtwork = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Synced Lyrics", desc = "Show synced lyrics when available.")
    @field:ConfigEditorBoolean
    var syncedLyrics = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Rounded Corners", desc = "Round the display corners.")
    @field:ConfigEditorBoolean
    var roundedCorners = true
}
