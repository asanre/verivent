@file:Suppress("unused", "UnusedPrivateMember")

package io.github.asanre.verivent.paritycheck

import io.github.asanre.verivent.ui.InspectorLauncher as LauncherEntryPoints

/*
 * The iOS half of the parity enforcement (see ../src, which is the Android one). Everything else
 * Verivent exposes is common, so the Android compilation already covers it; `io.github.asanre.verivent.ui.InspectorLauncher` is
 * the one surface that exists only on iOS, and until this file existed it could drift from its
 * no-op mirror without `./gradlew check` noticing.
 */

private fun iosEntryPoints() {
    LauncherEntryPoints.open()
    LauncherEntryPoints.showNotification()
    LauncherEntryPoints.dismissNotification()
}
