package io.github.asanre.verivent.sample.shop

data class CartLine(val product: Product, val quantity: Int)

/** The products added so far, one line per product. */
data class Cart(val lines: List<CartLine> = emptyList()) {

    val total: Double get() = lines.sumOf { it.product.price * it.quantity }

    val size: Int get() = lines.sumOf { it.quantity }

    fun adding(product: Product): Cart = copy(
        lines = if (lines.any { it.product == product }) {
            lines.map { if (it.product == product) it.copy(quantity = it.quantity + 1) else it }
        } else {
            lines + CartLine(product, 1)
        },
    )
}
