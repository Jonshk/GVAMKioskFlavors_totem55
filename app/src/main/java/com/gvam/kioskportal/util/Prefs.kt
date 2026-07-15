package com.gvam.kioskportal.util

import android.content.Context
import com.gvam.kioskportal.model.AppDisplayConfig
import com.gvam.kioskportal.model.BackgroundMode
import com.gvam.kioskportal.model.BackgroundScale
import com.gvam.kioskportal.model.CardStyle
import com.gvam.kioskportal.model.FloatingControlsPosition
import com.gvam.kioskportal.model.FloatingControlsStyle
import com.gvam.kioskportal.model.HiddenHeaderAccessMode
import com.gvam.kioskportal.model.HeaderPosition
import com.gvam.kioskportal.model.IconSizeMode
import com.gvam.kioskportal.model.LayoutMode
import com.gvam.kioskportal.model.HeaderStyle
import com.gvam.kioskportal.model.HeaderTitleAlignment
import com.gvam.kioskportal.model.LauncherAppearance
import org.json.JSONArray
import org.json.JSONObject

object Prefs {

    private const val FILE = "portal_prefs"

    private const val KEY_PKGS = "selected_packages"
    private const val KEY_PKGS_ORDER = "selected_packages_order"
    private const val KEY_APP_CONFIGS = "app_display_configs"
    private const val KEY_APPEARANCE = "launcher_appearance"

    private const val KEY_PIN_HASH = "admin_pin_hash"
    private const val KEY_TITLE = "portal_title"
    private const val KEY_SHOW_TITLE = "show_portal_title"
    private const val KEY_DEVICE_NUMBER = "device_number"
    private const val KEY_HIDE_SETTINGS = "hide_settings"
    private const val KEY_BG_URI = "bg_uri"
    private const val KEY_LAYOUT_HORIZONTAL = "layout_horizontal"
    private const val KEY_MAINTENANCE = "kiosk_maintenance"
    private const val KEY_ALLOWLIST = "kiosk_allowlist"
    private const val KEY_KIOSK_STRICT = "kiosk_strict"

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun putBool(ctx: Context, key: String, value: Boolean) {
        sp(ctx).edit().putBoolean(key, value).apply()
    }

    fun getBool(ctx: Context, key: String, def: Boolean = false): Boolean =
        sp(ctx).getBoolean(key, def)

    fun saveSelectedPackages(ctx: Context, packages: Set<String>) {
        saveSelectedPackagesOrdered(ctx, packages.toList())
    }

    fun saveSelectedPackagesOrdered(ctx: Context, packages: List<String>) {
        val clean = packages.filter { it.isNotBlank() }.distinct()
        val array = JSONArray()
        clean.forEach(array::put)

        sp(ctx).edit()
            .putStringSet(KEY_PKGS, clean.toSet())
            .putString(KEY_PKGS_ORDER, array.toString())
            .apply()
    }

    fun loadSelectedPackages(ctx: Context): Set<String> =
        loadSelectedPackagesOrdered(ctx).toSet()

    fun loadSelectedPackagesOrdered(ctx: Context): List<String> {
        val stored = sp(ctx).getString(KEY_PKGS_ORDER, null)

        if (!stored.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(stored)
                return buildList {
                    for (index in 0 until array.length()) {
                        val packageName = array.optString(index).trim()
                        if (packageName.isNotBlank() && packageName !in this) {
                            add(packageName)
                        }
                    }
                }
            }
        }

        return sp(ctx)
            .getStringSet(KEY_PKGS, emptySet())
            .orEmpty()
            .filter { it.isNotBlank() }
            .sorted()
    }

    fun saveAppDisplayConfig(ctx: Context, config: AppDisplayConfig) {
        val all = loadAppDisplayConfigs(ctx).toMutableMap()
        all[config.packageName] = config
        saveAppDisplayConfigs(ctx, all)
    }

    fun getAppDisplayConfig(ctx: Context, packageName: String): AppDisplayConfig? =
        loadAppDisplayConfigs(ctx)[packageName]

    fun loadAppDisplayConfigs(ctx: Context): Map<String, AppDisplayConfig> {
        val raw = sp(ctx).getString(KEY_APP_CONFIGS, null) ?: return emptyMap()

        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val packageName = keys.next()
                    val json = root.optJSONObject(packageName) ?: continue
                    put(packageName, appConfigFromJson(packageName, json))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveAppDisplayConfigs(
        ctx: Context,
        configs: Map<String, AppDisplayConfig>
    ) {
        val root = JSONObject()
        configs.forEach { (packageName, config) ->
            root.put(packageName, appConfigToJson(config))
        }
        sp(ctx).edit().putString(KEY_APP_CONFIGS, root.toString()).apply()
    }

    fun removeAppDisplayConfig(ctx: Context, packageName: String) {
        val all = loadAppDisplayConfigs(ctx).toMutableMap()
        all.remove(packageName)
        saveAppDisplayConfigs(ctx, all)
    }

    fun resetAppDisplayConfig(ctx: Context, packageName: String) {
        removeAppDisplayConfig(ctx, packageName)
    }

    fun saveAppearance(ctx: Context, appearance: LauncherAppearance) {
        sp(ctx).edit()
            .putString(KEY_APPEARANCE, appearanceToJson(appearance).toString())
            .putBoolean(KEY_SHOW_TITLE, appearance.showTitle)
            .apply()
    }

    fun loadAppearance(ctx: Context): LauncherAppearance {
        val raw = sp(ctx).getString(KEY_APPEARANCE, null)

        if (!raw.isNullOrBlank()) {
            return runCatching {
                appearanceFromJson(JSONObject(raw))
            }.getOrElse {
                LauncherAppearance(
                    backgroundMode = if (getBackgroundUri(ctx).isNullOrBlank()) {
                        BackgroundMode.DEFAULT_IMAGE
                    } else {
                        BackgroundMode.CUSTOM_IMAGE
                    },
                    backgroundUri = getBackgroundUri(ctx),
                    showTitle = isTitleVisible(ctx)
                )
            }
        }

        val oldBackground = getBackgroundUri(ctx)
        return LauncherAppearance(
            backgroundMode = if (oldBackground.isNullOrBlank()) {
                BackgroundMode.DEFAULT_IMAGE
            } else {
                BackgroundMode.CUSTOM_IMAGE
            },
            backgroundUri = oldBackground,
            showTitle = isTitleVisible(ctx)
        )
    }

    fun resetAppearance(ctx: Context) {
        saveAppearance(ctx, LauncherAppearance())
    }

    fun hasPin(ctx: Context): Boolean =
        sp(ctx).contains(KEY_PIN_HASH)

    fun savePinHash(ctx: Context, hash: String) {
        sp(ctx).edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun getPinHash(ctx: Context): String? =
        sp(ctx).getString(KEY_PIN_HASH, null)

    fun clearPin(ctx: Context) {
        sp(ctx).edit().remove(KEY_PIN_HASH).apply()
    }

    fun saveTitle(ctx: Context, title: String?) {
        val editor = sp(ctx).edit()
        if (title == null) editor.remove(KEY_TITLE)
        else editor.putString(KEY_TITLE, title)
        editor.apply()
    }

    fun loadTitle(ctx: Context): String? =
        sp(ctx).getString(KEY_TITLE, null)

    fun clearTitle(ctx: Context) {
        saveTitle(ctx, null)
    }

    fun setTitleVisible(ctx: Context, visible: Boolean) {
        sp(ctx).edit().putBoolean(KEY_SHOW_TITLE, visible).apply()
        saveAppearance(ctx, loadAppearance(ctx).copy(showTitle = visible))
    }

    fun isTitleVisible(ctx: Context): Boolean =
        sp(ctx).getBoolean(
            KEY_SHOW_TITLE,
            loadAppearanceWithoutTitleMigration(ctx)?.showTitle ?: true
        )

    private fun loadAppearanceWithoutTitleMigration(ctx: Context): LauncherAppearance? {
        val raw = sp(ctx).getString(KEY_APPEARANCE, null) ?: return null
        return runCatching { appearanceFromJson(JSONObject(raw)) }.getOrNull()
    }

    fun saveDeviceNumber(ctx: Context, value: String) {
        sp(ctx).edit().putString(KEY_DEVICE_NUMBER, value).apply()
    }

    fun loadDeviceNumber(ctx: Context): String? =
        sp(ctx).getString(KEY_DEVICE_NUMBER, null)

    fun setHideSettings(ctx: Context, hide: Boolean) {
        sp(ctx).edit().putBoolean(KEY_HIDE_SETTINGS, hide).apply()
    }

    fun isSettingsHidden(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_HIDE_SETTINGS, false)

    fun setBackgroundUri(ctx: Context, uri: String?) {
        val editor = sp(ctx).edit()
        if (uri.isNullOrBlank()) editor.remove(KEY_BG_URI)
        else editor.putString(KEY_BG_URI, uri)
        editor.apply()
    }

    fun saveBackgroundUri(ctx: Context, uri: String?) =
        setBackgroundUri(ctx, uri)

    fun getBackgroundUri(ctx: Context): String? =
        sp(ctx).getString(KEY_BG_URI, null)

    fun loadBackgroundUri(ctx: Context): String? =
        getBackgroundUri(ctx)

    fun setLayoutHorizontal(ctx: Context, horizontal: Boolean) {
        sp(ctx).edit().putBoolean(KEY_LAYOUT_HORIZONTAL, horizontal).apply()
    }

    fun isLayoutHorizontal(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_LAYOUT_HORIZONTAL, false)


    fun setHorizontal(ctx: Context, horizontal: Boolean) =
        setLayoutHorizontal(ctx, horizontal)

    fun isHorizontal(ctx: Context): Boolean =
        isLayoutHorizontal(ctx)

    fun setMaintenance(ctx: Context, enabled: Boolean) {
        sp(ctx).edit().putBoolean(KEY_MAINTENANCE, enabled).apply()
    }

    fun isMaintenance(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_MAINTENANCE, false)

    fun saveAllowlist(ctx: Context, packages: Set<String>) {
        sp(ctx).edit().putStringSet(KEY_ALLOWLIST, packages).apply()
    }

    fun loadAllowlist(ctx: Context): Set<String> =
        sp(ctx).getStringSet(KEY_ALLOWLIST, emptySet()).orEmpty()


    fun getAllowlist(ctx: Context): Set<String> =
        loadAllowlist(ctx)

    fun addToAllowlist(ctx: Context, packageName: String) {
        val values = loadAllowlist(ctx).toMutableSet()
        values += packageName
        saveAllowlist(ctx, values)
    }

    fun removeFromAllowlist(ctx: Context, packageName: String) {
        val values = loadAllowlist(ctx).toMutableSet()
        values -= packageName
        saveAllowlist(ctx, values)
    }

    fun clearAllowlist(ctx: Context) {
        sp(ctx).edit().remove(KEY_ALLOWLIST).apply()
    }

    fun setKioskStrict(ctx: Context, strict: Boolean) {
        sp(ctx).edit().putBoolean(KEY_KIOSK_STRICT, strict).apply()
    }

    fun isKioskStrict(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_KIOSK_STRICT, true)

    private fun appConfigToJson(config: AppDisplayConfig) =
        JSONObject().apply {
            put("customLabel", config.customLabel ?: JSONObject.NULL)
            put("showLabel", config.showLabel)
            put("customIconUri", config.customIconUri ?: JSONObject.NULL)
            put("iconSize", config.iconSize ?: JSONObject.NULL)
            put("position", config.position)
            put("useGlobalStyle", config.useGlobalStyle)
            put("cardStyle", config.cardStyle?.name ?: JSONObject.NULL)
            put("cardOpacity", config.cardOpacity ?: JSONObject.NULL)
            put("cardColor", config.cardColor ?: JSONObject.NULL)
        }

    private fun appConfigFromJson(
        packageName: String,
        json: JSONObject
    ) = AppDisplayConfig(
        packageName = packageName,
        customLabel = json.optNullableString("customLabel"),
        showLabel = json.optBoolean("showLabel", true),
        customIconUri = json.optNullableString("customIconUri"),
        iconSize = json.optNullableInt("iconSize"),
        position = json.optInt("position", Int.MAX_VALUE),
        useGlobalStyle = json.optBoolean("useGlobalStyle", true),
        cardStyle = json.optNullableString("cardStyle")?.let { CardStyle.fromStorage(it) },
        cardOpacity = json.optNullableFloat("cardOpacity"),
        cardColor = json.optNullableLong("cardColor")
    )

    private fun appearanceToJson(value: LauncherAppearance) =
        JSONObject().apply {
            put("backgroundMode", value.backgroundMode.name)
            put("backgroundUri", value.backgroundUri ?: JSONObject.NULL)
            put("backgroundColor", value.backgroundColor)
            put("backgroundScale", value.backgroundScale.name)
            put("backgroundOverlay", value.backgroundOverlay)

            put("cardStyle", value.cardStyle.name)
            put("cardOpacity", value.cardOpacity)
            put("cardBlurRadius", value.cardBlurRadius)
            put("cardBrightness", value.cardBrightness)
            put("cardBorderOpacity", value.cardBorderOpacity)
            put("cardCornerRadius", value.cardCornerRadius)
            put("cardShadow", value.cardShadow)
            put("cardSpacing", value.cardSpacing)
            put("cardHeight", value.cardHeight)
            put("layoutMode", value.layoutMode.name)
            put("columns", value.columns)

            put("iconSizeMode", value.iconSizeMode.name)
            put("iconSize", value.iconSize)
            put("showLabels", value.showLabels)
            put("textColor", value.textColor)
            put("textSize", value.textSize)
            put("showTitle", value.showTitle)

            put("headerVisible", value.headerVisible)
            put("headerPosition", value.headerPosition.name)
            put("headerStyle", value.headerStyle.name)
            put("headerColor", value.headerColor)
            put("headerImageUri", value.headerImageUri ?: JSONObject.NULL)
            put("headerImageScale", value.headerImageScale.name)
            put("topBarOpacity", value.topBarOpacity)
            put("headerHeight", value.headerHeight)
            put("headerCornerRadius", value.headerCornerRadius)
            put("headerHorizontalMargin", value.headerHorizontalMargin)
            put("headerVerticalMargin", value.headerVerticalMargin)
            put("headerShadow", value.headerShadow)
            put("headerBorderOpacity", value.headerBorderOpacity)

            put("headerTitleColor", value.headerTitleColor)
            put("headerTitleSize", value.headerTitleSize)
            put("headerTitleAlignment", value.headerTitleAlignment.name)
            put("headerTitleBold", value.headerTitleBold)

            put("showViewToggle", value.showViewToggle)
            put("showMenuButton", value.showMenuButton)

            put("hiddenHeaderAccessMode", value.hiddenHeaderAccessMode.name)
            put("floatingControlsPosition", value.floatingControlsPosition.name)
            put("floatingControlsStyle", value.floatingControlsStyle.name)
            put("floatingControlsColor", value.floatingControlsColor)
            put("floatingControlsOpacity", value.floatingControlsOpacity)
            put("floatingControlsSize", value.floatingControlsSize)
            put("floatingControlsAutoHide", value.floatingControlsAutoHide)
            put("floatingControlsAutoHideSeconds", value.floatingControlsAutoHideSeconds)
        }

    private fun appearanceFromJson(json: JSONObject) =
        LauncherAppearance(
            backgroundMode = BackgroundMode.fromStorage(
                json.optString("backgroundMode", BackgroundMode.DEFAULT_IMAGE.name)
            ),
            backgroundUri = json.optNullableString("backgroundUri"),
            backgroundColor = json.optLong("backgroundColor", 0xFF1B2117L),
            backgroundScale = BackgroundScale.fromStorage(
                json.optString("backgroundScale", BackgroundScale.CROP.name)
            ),
            backgroundOverlay = json.optDouble("backgroundOverlay", 0.12).toFloat(),

            cardStyle = CardStyle.fromStorage(
                json.optString("cardStyle", CardStyle.GLASS.name)
            ),
            cardOpacity = json.optDouble("cardOpacity", 0.65).toFloat(),
            cardBlurRadius = json.optDouble("cardBlurRadius", 18.0).toFloat(),
            cardBrightness = json.optDouble("cardBrightness", 0.30).toFloat(),
            cardBorderOpacity = json.optDouble("cardBorderOpacity", 0.20).toFloat(),
            cardCornerRadius = json.optDouble("cardCornerRadius", 24.0).toFloat(),
            cardShadow = json.optDouble("cardShadow", 4.0).toFloat(),
            cardSpacing = json.optDouble("cardSpacing", 12.0).toFloat(),
            cardHeight = json.optDouble("cardHeight", 160.0).toFloat(),
            layoutMode = LayoutMode.fromStorage(
                json.optString("layoutMode", LayoutMode.ADAPTIVE.name)
            ),
            columns = json.optInt("columns", 2),

            iconSizeMode = IconSizeMode.fromStorage(
                json.optString("iconSizeMode", IconSizeMode.AUTO.name)
            ),
            iconSize = json.optInt("iconSize", 72),
            showLabels = json.optBoolean("showLabels", true),
            textColor = json.optLong("textColor", 0xFF252327L),
            textSize = json.optDouble("textSize", 16.0).toFloat(),
            showTitle = json.optBoolean("showTitle", true),

            headerVisible = json.optBoolean("headerVisible", true),
            headerPosition = HeaderPosition.fromStorage(
                json.optString("headerPosition", HeaderPosition.TOP.name)
            ),
            headerStyle = HeaderStyle.fromStorage(
                json.optString("headerStyle", HeaderStyle.GLASS.name)
            ),
            headerColor = json.optLong("headerColor", 0xFF344E25L),
            headerImageUri = json.optNullableString("headerImageUri"),
            headerImageScale = BackgroundScale.fromStorage(
                json.optString("headerImageScale", BackgroundScale.CROP.name)
            ),
            topBarOpacity = json.optDouble("topBarOpacity", 0.90).toFloat(),
            headerHeight = json.optDouble("headerHeight", 68.0).toFloat(),
            headerCornerRadius = json.optDouble("headerCornerRadius", 26.0).toFloat(),
            headerHorizontalMargin = json.optDouble("headerHorizontalMargin", 10.0).toFloat(),
            headerVerticalMargin = json.optDouble("headerVerticalMargin", 4.0).toFloat(),
            headerShadow = json.optDouble("headerShadow", 3.0).toFloat(),
            headerBorderOpacity = json.optDouble("headerBorderOpacity", 0.18).toFloat(),

            headerTitleColor = json.optLong("headerTitleColor", 0xFFFFFFFFL),
            headerTitleSize = json.optDouble("headerTitleSize", 22.0).toFloat(),
            headerTitleAlignment = HeaderTitleAlignment.fromStorage(
                json.optString(
                    "headerTitleAlignment",
                    HeaderTitleAlignment.CENTER.name
                )
            ),
            headerTitleBold = json.optBoolean("headerTitleBold", true),

            showViewToggle = json.optBoolean("showViewToggle", true),
            showMenuButton = json.optBoolean("showMenuButton", true),

            hiddenHeaderAccessMode = HiddenHeaderAccessMode.fromStorage(
                json.optString(
                    "hiddenHeaderAccessMode",
                    HiddenHeaderAccessMode.BOTH.name
                )
            ),
            floatingControlsPosition = FloatingControlsPosition.fromStorage(
                json.optString(
                    "floatingControlsPosition",
                    FloatingControlsPosition.TOP_END.name
                )
            ),
            floatingControlsStyle = FloatingControlsStyle.fromStorage(
                json.optString(
                    "floatingControlsStyle",
                    FloatingControlsStyle.GLASS.name
                )
            ),
            floatingControlsColor = json.optLong(
                "floatingControlsColor",
                0xFF344E25L
            ),
            floatingControlsOpacity = json.optDouble(
                "floatingControlsOpacity",
                0.86
            ).toFloat(),
            floatingControlsSize = json.optDouble(
                "floatingControlsSize",
                52.0
            ).toFloat(),
            floatingControlsAutoHide = json.optBoolean(
                "floatingControlsAutoHide",
                false
            ),
            floatingControlsAutoHideSeconds = json.optInt(
                "floatingControlsAutoHideSeconds",
                5
            ).coerceIn(3, 15)
        )

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeUnless { it == "null" }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key)
    }

    private fun JSONObject.optNullableFloat(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).toFloat()
    }
}
