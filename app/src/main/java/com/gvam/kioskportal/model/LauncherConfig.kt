package com.gvam.kioskportal.model

enum class CardStyle {
    NONE,
    TRANSPARENT,
    GLASS,
    GLASS_STRONG,
    SOLID_LIGHT,
    SOLID_DARK;

    companion object {
        fun fromStorage(value: String?): CardStyle =
            values().firstOrNull { it.name == value } ?: GLASS
    }
}

enum class BackgroundMode {
    DEFAULT_IMAGE,
    CUSTOM_IMAGE,
    SOLID_COLOR,
    NONE;

    companion object {
        fun fromStorage(value: String?): BackgroundMode =
            values().firstOrNull { it.name == value } ?: DEFAULT_IMAGE
    }
}

enum class BackgroundScale {
    CROP,
    FIT,
    STRETCH;

    companion object {
        fun fromStorage(value: String?): BackgroundScale =
            values().firstOrNull { it.name == value } ?: CROP
    }
}


enum class LayoutMode {
    ADAPTIVE,
    ONE_COLUMN,
    TWO_COLUMNS,
    THREE_COLUMNS;

    companion object {
        fun fromStorage(value: String?): LayoutMode =
            values().firstOrNull { it.name == value } ?: ADAPTIVE
    }
}

enum class IconSizeMode {
    AUTO,
    SMALL,
    MEDIUM,
    LARGE,
    CUSTOM;

    companion object {
        fun fromStorage(value: String?): IconSizeMode =
            values().firstOrNull { it.name == value } ?: AUTO
    }
}

enum class HeaderPosition {
    TOP,
    BOTTOM;

    companion object {
        fun fromStorage(value: String?): HeaderPosition =
            values().firstOrNull { it.name == value } ?: TOP
    }
}

enum class HeaderStyle {
    TRANSPARENT,
    GLASS,
    SOLID,
    CUSTOM_IMAGE;

    companion object {
        fun fromStorage(value: String?): HeaderStyle =
            values().firstOrNull { it.name == value } ?: GLASS
    }
}

enum class HeaderTitleAlignment {
    START,
    CENTER,
    END;

    companion object {
        fun fromStorage(value: String?): HeaderTitleAlignment =
            values().firstOrNull { it.name == value } ?: CENTER
    }
}


enum class HiddenHeaderAccessMode {
    FLOATING_BUTTONS,
    SECRET_CORNER,
    BOTH;

    companion object {
        fun fromStorage(value: String?): HiddenHeaderAccessMode =
            values().firstOrNull { it.name == value } ?: BOTH
    }
}

enum class FloatingControlsPosition {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END;

    companion object {
        fun fromStorage(value: String?): FloatingControlsPosition =
            values().firstOrNull { it.name == value } ?: TOP_END
    }
}

enum class FloatingControlsStyle {
    TRANSPARENT,
    GLASS,
    SOLID;

    companion object {
        fun fromStorage(value: String?): FloatingControlsStyle =
            values().firstOrNull { it.name == value } ?: GLASS
    }
}

data class AppDisplayConfig(
    val packageName: String,
    val customLabel: String? = null,
    val showLabel: Boolean = true,
    val customIconUri: String? = null,
    val iconSize: Int? = null,
    val position: Int = Int.MAX_VALUE,
    val useGlobalStyle: Boolean = true,
    val cardStyle: CardStyle? = null,
    val cardOpacity: Float? = null,
    val cardColor: Long? = null
) {
    fun resolveLabel(originalLabel: String): String =
        customLabel ?: originalLabel
}

data class LauncherAppearance(
    val backgroundMode: BackgroundMode = BackgroundMode.DEFAULT_IMAGE,
    val backgroundUri: String? = null,
    val backgroundColor: Long = 0xFF1B2117L,
    val backgroundScale: BackgroundScale = BackgroundScale.CROP,
    val backgroundOverlay: Float = 0.12f,

    val cardStyle: CardStyle = CardStyle.GLASS,
    val cardOpacity: Float = 0.65f,
    val cardBlurRadius: Float = 18f,
    val cardBrightness: Float = 0.30f,
    val cardBorderOpacity: Float = 0.20f,
    val cardCornerRadius: Float = 24f,
    val cardShadow: Float = 4f,
    val cardSpacing: Float = 12f,
    val cardHeight: Float = 160f,
    val layoutMode: LayoutMode = LayoutMode.ADAPTIVE,

    // Se conserva para migrar configuraciones antiguas.
    val columns: Int = 2,

    val iconSizeMode: IconSizeMode = IconSizeMode.AUTO,
    val iconSize: Int = 72,
    val showLabels: Boolean = true,
    val textColor: Long = 0xFF252327L,
    val textSize: Float = 16f,

    val showTitle: Boolean = true,

    val headerVisible: Boolean = true,
    val headerPosition: HeaderPosition = HeaderPosition.TOP,
    val headerStyle: HeaderStyle = HeaderStyle.GLASS,
    val headerColor: Long = 0xFF344E25L,
    val headerImageUri: String? = null,
    val headerImageScale: BackgroundScale = BackgroundScale.CROP,
    val topBarOpacity: Float = 0.90f,
    val headerHeight: Float = 68f,
    val headerCornerRadius: Float = 26f,
    val headerHorizontalMargin: Float = 10f,
    val headerVerticalMargin: Float = 4f,
    val headerShadow: Float = 3f,
    val headerBorderOpacity: Float = 0.18f,

    val headerTitleColor: Long = 0xFFFFFFFFL,
    val headerTitleSize: Float = 22f,
    val headerTitleAlignment: HeaderTitleAlignment = HeaderTitleAlignment.CENTER,
    val headerTitleBold: Boolean = true,

    val showViewToggle: Boolean = true,
    val showMenuButton: Boolean = true,

    val hiddenHeaderAccessMode: HiddenHeaderAccessMode = HiddenHeaderAccessMode.BOTH,
    val floatingControlsPosition: FloatingControlsPosition = FloatingControlsPosition.TOP_END,
    val floatingControlsStyle: FloatingControlsStyle = FloatingControlsStyle.GLASS,
    val floatingControlsColor: Long = 0xFF344E25L,
    val floatingControlsOpacity: Float = 0.86f,
    val floatingControlsSize: Float = 52f,
    val floatingControlsAutoHide: Boolean = false,
    val floatingControlsAutoHideSeconds: Int = 5
)
