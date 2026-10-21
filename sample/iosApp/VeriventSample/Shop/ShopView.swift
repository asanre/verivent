import SwiftUI

enum Route: Hashable {
    case product(Product)
    case cart
    case payment
    case confirmation(String)
    case developer
}

/// The shop, a stack of its screens, and the Developer screen that opens Verivent.
struct ShopView: View {
    @State private var path: [Route] = []
    @State private var cart = Cart()

    var body: some View {
        NavigationStack(path: $path) {
            CatalogView()
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .product(let product):
                        ProductView(product: product) {
                            cart = cart.adding(product)
                            path.append(.cart)
                        }
                    case .cart:
                        CartView(cart: cart) { path.append(.payment) }
                    case .payment:
                        PaymentView(cart: cart) { pay() }
                    case .confirmation(let orderId):
                        ConfirmationView(orderId: orderId) { path = [] }
                    case .developer:
                        DeveloperView()
                    }
                }
        }
    }

    private func pay() {
        let orderId = String(format: "T-%06X", Int.random(in: 0..<0x1000000))
        ShopAnalytics.purchase(cart, orderId: orderId)
        SampleNetwork.shared.placeOrder(cart, orderId: orderId)
        cart = Cart()
        path = [.confirmation(orderId)]
    }
}

struct CatalogView: View {
    var body: some View {
        List {
            Section(footer: Text("A shop whose every step sends analytics. Developer opens Verivent.")) {
                ForEach(catalog) { product in
                    NavigationLink(value: Route.product(product)) {
                        Text("\(product.name) · \(price(product.price))")
                    }
                    .accessibilityIdentifier("catalog.item.\(product.id)")
                }
            }
        }
        .navigationTitle("Shop")
        .toolbar {
            NavigationLink("Developer", value: Route.developer).accessibilityIdentifier("catalog.developer")
        }
        .onAppear {
            ShopAnalytics.screen("catalog")
            ShopAnalytics.catalog(catalog)
        }
    }
}

struct ProductView: View {
    let product: Product
    let onAdd: () -> Void

    var body: some View {
        List {
            Text(price(product.price)).font(.title3)
            Button("Add to cart") {
                ShopAnalytics.addToCart(product)
                onAdd()
            }
            .accessibilityIdentifier("product.addToCart")
        }
        .navigationTitle(product.name)
        .onAppear {
            ShopAnalytics.screen("product")
            ShopAnalytics.product(product)
        }
    }
}

struct CartView: View {
    let cart: Cart
    let onCheckout: () -> Void

    var body: some View {
        List {
            ForEach(cart.lines, id: \.product) { Text("\($0.quantity) × \($0.product.name)") }
            Text("Total \(price(cart.total))").font(.title3)
            Button("Checkout") {
                ShopAnalytics.checkout(cart)
                onCheckout()
            }
            .accessibilityIdentifier("cart.checkout")
        }
        .navigationTitle("Cart")
        .onAppear { ShopAnalytics.screen("cart") }
    }
}

struct PaymentView: View {
    let cart: Cart
    let onPay: () -> Void

    var body: some View {
        List {
            Text("Total \(price(cart.total))").font(.title3)
            Button("Pay", action: onPay).accessibilityIdentifier("payment.pay")
        }
        .navigationTitle("Payment")
        .onAppear { ShopAnalytics.screen("payment") }
    }
}

struct ConfirmationView: View {
    let orderId: String
    let onDone: () -> Void

    var body: some View {
        List {
            Section(header: Text("Order")) {
                Text(orderId).font(.title3).accessibilityIdentifier("confirmation.orderId")
            }
            Button("Continue shopping", action: onDone).accessibilityIdentifier("confirmation.continue")
        }
        .navigationTitle("Thank you")
        .navigationBarBackButtonHidden()
        .onAppear { ShopAnalytics.screen("confirmation") }
    }
}
