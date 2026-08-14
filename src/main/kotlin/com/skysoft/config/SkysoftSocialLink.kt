package com.skysoft.config

import com.skysoft.utils.BrowserUtilities
import com.skysoft.utils.SoundUtilities
import io.github.notenoughupdates.moulconfig.Social
import io.github.notenoughupdates.moulconfig.common.MyResourceLocation
import io.github.notenoughupdates.moulconfig.common.text.StructuredText

internal class SkysoftSocialLink private constructor(
    tooltip: String,
    val url: String,
    iconName: String,
) : Social() {
    private val icon = icon(iconName)
    private val hoveredIcon = icon("${iconName}_hover")
    private val tooltipLines = listOf(StructuredText.of(tooltip))
    private var wasHovered = false

    override fun onClick() {
        SoundUtilities.playClickSound()
        BrowserUtilities.tryOpen(url)
    }

    override fun getTooltip(): List<StructuredText> {
        wasHovered = true
        return tooltipLines
    }

    override fun getIcon(): MyResourceLocation =
        (if (wasHovered) hoveredIcon else icon).also { wasHovered = false }

    companion object {
        val discord = SkysoftSocialLink("Official Discord", "https://discord.gg/akin", "discord")
        val modrinth = SkysoftSocialLink("Modrinth Page", "https://modrinth.com/mod/skysoft", "modrinth")
        val github = SkysoftSocialLink("Source Code", "https://github.com/akinsoft/Skysoft", "github")
        val koFi = SkysoftSocialLink("sup dawg? ko-fi?", "https://ko-fi.com/akinsoft", "kofi")

        val headerLinks: List<Social> = listOf(github, modrinth, discord, koFi)

        private fun icon(name: String) =
            MyResourceLocation("skysoft", "textures/gui/sprites/social/$name.png")
    }
}
