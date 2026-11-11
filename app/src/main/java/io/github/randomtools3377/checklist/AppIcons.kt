package io.github.randomtools3377.checklist

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIcons {
    private fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, "io.github.randomtools3377.checklist.${icon.alias}")

    /** Turns on [icon]'s launcher entry and turns the others off, without closing the app. */
    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        // The new one first, so there is never a moment with no icon at all.
        AppIcon.entries.sortedBy { it != icon }.forEach { each ->
            val wanted = if (each == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val name = component(context, each)
            if (pm.getComponentEnabledSetting(name) != wanted && !(each == AppIcon.GARDEN && each == icon && isDefault(pm, name))) {
                pm.setComponentEnabledSetting(name, wanted, PackageManager.DONT_KILL_APP)
            }
        }
    }

    /** Garden is on from the manifest until something else is chosen. */
    private fun isDefault(pm: PackageManager, name: ComponentName) =
        pm.getComponentEnabledSetting(name) == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

    /** The icon whose launcher entry is on now. */
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull { icon ->
            when (pm.getComponentEnabledSetting(component(context, icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.GARDEN
                else -> false
            }
        } ?: AppIcon.GARDEN
    }

    fun of(name: String?): AppIcon = AppIcon.entries.firstOrNull { it.name == name } ?: AppIcon.GARDEN
}
