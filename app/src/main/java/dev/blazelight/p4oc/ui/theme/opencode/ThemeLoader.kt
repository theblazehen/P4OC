package dev.blazelight.p4oc.ui.theme.opencode

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Loads and parses OpenCode theme.json files.
 * Handles color reference resolution and dark/light mode selection.
 */
object ThemeLoader {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Load a bundled theme from assets by name.
     */
    fun loadBundledTheme(context: Context, themeName: String, isDark: Boolean): OpenCodeTheme {
        val jsonString = try {
            context.assets.open("themes/$themeName.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            // Fallback to catppuccin if theme not found
            context.assets.open("themes/catppuccin.json").bufferedReader().use { it.readText() }
        }
        return parseTheme(jsonString, themeName, isDark)
    }
    
    /**
     * Parse a theme from JSON string.
     */
    fun parseTheme(jsonString: String, themeName: String, isDark: Boolean): OpenCodeTheme {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val defs = root["defs"]?.jsonObject ?: JsonObject(emptyMap())
        val theme = root["theme"]?.jsonObject ?: error("Theme must have 'theme' object")
        
        fun resolveColor(key: String): Color {
            val value = theme[key] ?: return Color.Magenta // Fallback for missing keys
            
            return when {
                // Direct hex color
                value.jsonPrimitive.isString && value.jsonPrimitive.content.startsWith("#") -> {
                    parseHexColor(value.jsonPrimitive.content)
                }
                // Reference to defs
                value.jsonPrimitive.isString -> {
                    val ref = value.jsonPrimitive.content
                    val resolved = defs[ref]?.jsonPrimitive?.content
                        ?: return Color.Magenta
                    parseHexColor(resolved)
                }
                // Dark/light pair object
                else -> {
                    val pair = value.jsonObject
                    val modeKey = if (isDark) "dark" else "light"
                    val modeValue = pair[modeKey]?.jsonPrimitive?.content
                        ?: return Color.Magenta
                    
                    // Could be a reference or direct hex
                    if (modeValue.startsWith("#")) {
                        parseHexColor(modeValue)
                    } else {
                        val resolved = defs[modeValue]?.jsonPrimitive?.content
                            ?: return Color.Magenta
                        parseHexColor(resolved)
                    }
                }
            }
        }
        
        return OpenCodeTheme(
            name = themeName,
            isDark = isDark,
            
            // Core
            primary = resolveColor("primary"),
            secondary = resolveColor("secondary"),
            accent = resolveColor("accent"),
            text = resolveColor("text"),
            textMuted = resolveColor("textMuted"),
            background = resolveColor("background"),
            
            // Status
            error = resolveColor("error"),
            warning = resolveColor("warning"),
            success = resolveColor("success"),
            info = resolveColor("info"),
            
            // Surfaces
            backgroundPanel = resolveColor("backgroundPanel"),
            backgroundElement = resolveColor("backgroundElement"),
            border = resolveColor("border"),
            borderActive = resolveColor("borderActive"),
            borderSubtle = resolveColor("borderSubtle"),
            
            // Diff
            diffAdded = resolveColor("diffAdded"),
            diffRemoved = resolveColor("diffRemoved"),
            diffContext = resolveColor("diffContext"),
            diffHunkHeader = resolveColor("diffHunkHeader"),
            diffHighlightAdded = resolveColor("diffHighlightAdded"),
            diffHighlightRemoved = resolveColor("diffHighlightRemoved"),
            diffAddedBg = resolveColor("diffAddedBg"),
            diffRemovedBg = resolveColor("diffRemovedBg"),
            diffContextBg = resolveColor("diffContextBg"),
            diffLineNumber = resolveColor("diffLineNumber"),
            diffAddedLineNumberBg = resolveColor("diffAddedLineNumberBg"),
            diffRemovedLineNumberBg = resolveColor("diffRemovedLineNumberBg"),
            
            // Markdown
            markdownText = resolveColor("markdownText"),
            markdownHeading = resolveColor("markdownHeading"),
            markdownLink = resolveColor("markdownLink"),
            markdownLinkText = resolveColor("markdownLinkText"),
            markdownCode = resolveColor("markdownCode"),
            markdownBlockQuote = resolveColor("markdownBlockQuote"),
            markdownEmph = resolveColor("markdownEmph"),
            markdownStrong = resolveColor("markdownStrong"),
            markdownHorizontalRule = resolveColor("markdownHorizontalRule"),
            markdownListItem = resolveColor("markdownListItem"),
            markdownListEnumeration = resolveColor("markdownListEnumeration"),
            markdownImage = resolveColor("markdownImage"),
            markdownImageText = resolveColor("markdownImageText"),
            markdownCodeBlock = resolveColor("markdownCodeBlock"),
            
            // Syntax
            syntaxComment = resolveColor("syntaxComment"),
            syntaxKeyword = resolveColor("syntaxKeyword"),
            syntaxFunction = resolveColor("syntaxFunction"),
            syntaxVariable = resolveColor("syntaxVariable"),
            syntaxString = resolveColor("syntaxString"),
            syntaxNumber = resolveColor("syntaxNumber"),
            syntaxType = resolveColor("syntaxType"),
            syntaxOperator = resolveColor("syntaxOperator"),
            syntaxPunctuation = resolveColor("syntaxPunctuation")
        )
    }
    
    /**
     * Parse hex color string to Compose Color.
     * Supports #RGB, #RRGGBB, and #AARRGGBB formats.
     */
    private fun parseHexColor(hex: String): Color {
        val cleaned = hex.removePrefix("#")
        return try {
            when (cleaned.length) {
                3 -> {
                    // #RGB -> #RRGGBB
                    val r = cleaned[0].toString().repeat(2).toInt(16)
                    val g = cleaned[1].toString().repeat(2).toInt(16)
                    val b = cleaned[2].toString().repeat(2).toInt(16)
                    Color(r, g, b)
                }
                6 -> {
                    // #RRGGBB
                    val colorInt = cleaned.toLong(16).toInt() or 0xFF000000.toInt()
                    Color(colorInt)
                }
                8 -> {
                    // #AARRGGBB
                    val colorLong = cleaned.toLong(16)
                    Color(colorLong.toInt())
                }
                else -> Color.Magenta
            }
        } catch (e: Exception) {
            Color.Magenta
        }
    }
    
    /**
     * Get list of available bundled theme names.
     */
    fun getAvailableThemes(context: Context): List<String> {
        return try {
            context.assets.list("themes")
                ?.filter { it.endsWith(".json") }
                ?.map { it.removeSuffix(".json") }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
