struct CartLine: Hashable {
    let product: Product
    let quantity: Int
}

/// The products added so far, one line per product.
struct Cart: Hashable {
    var lines: [CartLine] = []

    var total: Double { lines.reduce(0) { $0 + $1.product.price * Double($1.quantity) } }

    var size: Int { lines.reduce(0) { $0 + $1.quantity } }

    func adding(_ product: Product) -> Cart {
        guard lines.contains(where: { $0.product == product }) else {
            return Cart(lines: lines + [CartLine(product: product, quantity: 1)])
        }
        return Cart(lines: lines.map { $0.product == product ? CartLine(product: product, quantity: $0.quantity + 1) : $0 })
    }
}
