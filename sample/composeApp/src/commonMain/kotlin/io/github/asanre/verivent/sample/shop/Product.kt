package io.github.asanre.verivent.sample.shop

/** What the shop sells. The SwiftUI sample sells the same, so both send the same analytics. */
data class Product(val id: String, val name: String, val price: Double)

val CATALOG = listOf(
    Product("SN-42", "Desk lamp", 39.9),
    Product("SN-7", "Notebook", 4.5),
    Product("SN-13", "Coffee mug", 12.5),
)

const val CURRENCY = "EUR"
