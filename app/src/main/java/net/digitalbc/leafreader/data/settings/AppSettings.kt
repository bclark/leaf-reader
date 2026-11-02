package net.digitalbc.leafreader.data.settings

import android.content.Context

/**
 * Lightweight app-wide preferences backed by [android.content.SharedPreferences].
 *
 * [defaultRightToLeft] is the reading direction applied to newly imported comics; each comic then
 * remembers its own direction once opened (stored per-comic in the database). This is the global
 * half of "remembered per comic + a global default", matching the iOS app's ReadingPrefs.
 */
class AppSettings internal constructor(private val prefs: android.content.SharedPreferences) {
    constructor(context: Context) : this(loadPreferences(context))

    var defaultRightToLeft: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_RTL, false)
        set(value) { prefs.edit().putBoolean(KEY_DEFAULT_RTL, value).apply() }

    /** When true, the app uses a true-black (AMOLED) theme instead of the default ink dark. */
    var amoledTheme: Boolean
        get() = prefs.getBoolean(KEY_AMOLED, false)
        set(value) { prefs.edit().putBoolean(KEY_AMOLED, value).apply() }

    /** When true, reading mode is Page Flip (full page slide); when false, Panel by Panel (guided reading). */
    var pageSlideEnabled: Boolean
        get() = prefs.getBoolean(KEY_PAGE_SLIDE, true)
        set(value) { prefs.edit().putBoolean(KEY_PAGE_SLIDE, value).apply() }

    /** Preferred whole-page framing: fit-to-page (contain, default) or fit-to-width. */
    var pageFitMode: net.digitalbc.leafreader.ui.reader.PageFitMode
        get() = when (prefs.getString(KEY_PAGE_FIT_MODE, net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_PAGE.name)) {
            net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_WIDTH.name -> net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_WIDTH
            else -> net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_PAGE
        }
        set(value) { prefs.edit().putString(KEY_PAGE_FIT_MODE, value.name).apply() }

    /** Independent portrait choice; existing installations previously always used single pages here. */
    var portraitSpread: Boolean
        get() = prefs.getBoolean(KEY_PORTRAIT_SPREAD, false)
        set(value) { prefs.edit().putBoolean(KEY_PORTRAIT_SPREAD, value).apply() }

    /** Retain the original key so existing landscape preferences migrate without changing behavior. */
    var landscapeSpread: Boolean
        get() = prefs.getBoolean(KEY_LANDSCAPE_SPREAD, false)
        set(value) { prefs.edit().putBoolean(KEY_LANDSCAPE_SPREAD, value).apply() }

    /** Number of comic cover columns on the library screen. */
    var libraryColumns: Int
        get() = prefs.getInt(KEY_LIBRARY_COLUMNS, 2)
        set(value) { prefs.edit().putInt(KEY_LIBRARY_COLUMNS, value).apply() }

    var smbHost: String
        get() = prefs.getString(KEY_SMB_HOST, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SMB_HOST, value.trim()).apply() }

    var smbShare: String
        get() = prefs.getString(KEY_SMB_SHARE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SMB_SHARE, value.trim().trim('/')).apply() }

    var smbUsername: String
        get() = prefs.getString(KEY_SMB_USER, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SMB_USER, value.trim()).apply() }

    var smbPassword: String
        get() = prefs.getString(KEY_SMB_PASS, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SMB_PASS, value).apply() }

    val hasSmbConfig: Boolean
        get() = smbHost.isNotBlank() && smbShare.isNotBlank()

    private companion object {
        fun loadPreferences(context: Context): android.content.SharedPreferences {
            val leafPrefs = context.getSharedPreferences("leaf.settings", Context.MODE_PRIVATE)
            val chikaPrefs = context.getSharedPreferences("chika.settings", Context.MODE_PRIVATE)
            if (leafPrefs.all.isEmpty() && chikaPrefs.all.isNotEmpty()) {
                val editor = leafPrefs.edit()
                for ((key, value) in chikaPrefs.all) {
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                    }
                }
                editor.apply()
            }
            return leafPrefs
        }

        const val KEY_DEFAULT_RTL = "default_rtl"
        const val KEY_AMOLED = "amoled_theme"
        const val KEY_PAGE_SLIDE = "page_slide_enabled"
        const val KEY_PAGE_FIT_MODE = "page_fit_mode"
        const val KEY_PORTRAIT_SPREAD = "portrait_spread"
        const val KEY_LANDSCAPE_SPREAD = "landscape_spread"
        const val KEY_LIBRARY_COLUMNS = "library_columns"
        const val KEY_SMB_HOST = "smb_host"
        const val KEY_SMB_SHARE = "smb_share"
        const val KEY_SMB_USER = "smb_user"
        const val KEY_SMB_PASS = "smb_pass"
    }
}
