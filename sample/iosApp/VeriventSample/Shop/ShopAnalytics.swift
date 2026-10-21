import Verivent

/// The shop's tracking: one function per event in `sample/shared/tracking-plan.json`.
enum ShopAnalytics {

    static func screen(_ name: String) {
        Verivent.log("screen_view", channel: "firebase", properties: ["screen_name": name])
    }

    static func catalog(_ products: [Product]) {
        Verivent.log("view_item_list", channel: "firebase", properties: [
            "item_list_name": "catalog",
            "items": products.enumerated().map { index, product in
                ["item_id": product.id, "item_name": product.name, "price": product.price, "index": index]
            },
        ])
    }

    static func product(_ product: Product) {
        Verivent.log("view_item", channel: "firebase", properties: [
            "currency": currency,
            "value": product.price,
            "items": [["item_id": product.id, "item_name": product.name, "price": product.price]],
        ])
    }

    static func addToCart(_ product: Product) {
        Verivent.log("add_to_cart", channel: "firebase", properties: order(Cart().adding(product)))
    }

    static func checkout(_ cart: Cart) {
        Verivent.log("begin_checkout", channel: "firebase", properties: order(cart))
        Verivent.log("checkout_started", channel: "gtm", properties: ["cart_size": cart.size])
    }

    static func purchase(_ cart: Cart, orderId: String) {
        Verivent.log("purchase", channel: "firebase", properties: order(cart).merging([
            "transaction_id": orderId,
            "user_email": "demo@verivent.dev",
        ]) { $1 })
    }

    private static func order(_ cart: Cart) -> [String: Any] {
        [
            "currency": currency,
            "value": cart.total,
            "items": cart.lines.map { ["item_id": $0.product.id, "quantity": $0.quantity] },
        ]
    }
}
