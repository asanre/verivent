package io.github.asanre.verivent.sample.shop

import io.github.asanre.verivent.analytics.AnalyticsSink

/** The shop's tracking: one function per event in `sample/shared/tracking-plan.json`. */
class ShopAnalytics(private val sink: AnalyticsSink) {

    fun screen(name: String) = sink.log("screen_view", "firebase", mapOf("screen_name" to name))

    fun catalog(products: List<Product>) = sink.log(
        "view_item_list",
        "firebase",
        mapOf(
            "item_list_name" to "catalog",
            "items" to products.mapIndexed { index, product ->
                mapOf("item_id" to product.id, "item_name" to product.name, "price" to product.price, "index" to index)
            },
        ),
    )

    fun product(product: Product) = sink.log(
        "view_item",
        "firebase",
        mapOf(
            "currency" to CURRENCY,
            "value" to product.price,
            "items" to listOf(mapOf("item_id" to product.id, "item_name" to product.name, "price" to product.price)),
        ),
    )

    fun addToCart(product: Product) = sink.log("add_to_cart", "firebase", order(Cart().adding(product)))

    fun checkout(cart: Cart) {
        sink.log("begin_checkout", "firebase", order(cart))
        sink.log("checkout_started", "gtm", mapOf("cart_size" to cart.size))
    }

    fun purchase(cart: Cart, orderId: String) =
        sink.log("purchase", "firebase", order(cart) + mapOf("transaction_id" to orderId, "user_email" to "demo@verivent.dev"))

    private fun order(cart: Cart) = mapOf(
        "currency" to CURRENCY,
        "value" to cart.total,
        "items" to cart.lines.map { mapOf("item_id" to it.product.id, "quantity" to it.quantity) },
    )
}
