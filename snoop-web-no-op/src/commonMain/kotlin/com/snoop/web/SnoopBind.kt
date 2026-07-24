package com.snoop.web

/** No-op mirror of `snoop-web`'s bind modes. */
sealed interface SnoopBind {
    data object Local : SnoopBind
    data object Lan : SnoopBind
    data class Host(val address: String) : SnoopBind
}
