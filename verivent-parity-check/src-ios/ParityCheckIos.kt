@file:Suppress("unused", "UnusedPrivateMember")

package com.snoop.paritycheck

import com.snoop.ui.Snoop as SnoopEntryPoints

/*
 * The iOS half of the parity enforcement (see ../src, which is the Android one). Everything else
 * Snoop exposes is common, so the Android compilation already covers it; `com.snoop.ui.Snoop` is
 * the one surface that exists only on iOS, and until this file existed it could drift from its
 * no-op mirror without `./gradlew check` noticing.
 */

private fun iosEntryPoints() {
    SnoopEntryPoints.open()
    SnoopEntryPoints.showNotification()
    SnoopEntryPoints.dismissNotification()
}
