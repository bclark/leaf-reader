package net.digitalbc.leafreader.data.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Keep exactly one launcher entry enabled; the actual activity always stays enabled. */
object LauncherIcons {
    fun apply(context: Context, icon: LauncherIcon) {
        val pm = context.packageManager
        val components = LauncherIcon.entries.map { choice ->
            ComponentName(context.packageName, context.packageName + when (choice) {
                LauncherIcon.LIGHT -> ".LightLauncher"
                LauncherIcon.DARK -> ".DarkLauncher"
            }) to if (choice == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val changes = components.filter { (component, state) ->
            val current = pm.getComponentEnabledSetting(component)
            val effective = if (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                if (component.className.endsWith(".LightLauncher")) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else current
            effective != state
        }
        if (changes.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33) {
            pm.setComponentEnabledSettings(changes.map { (component, state) ->
                PackageManager.ComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            })
        } else {
            // Enable the destination first, so there is never a gap with no launcher entry.
            changes.sortedBy { (_, state) -> state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
                .forEach { (component, state) ->
                    pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                }
        }
    }
}
