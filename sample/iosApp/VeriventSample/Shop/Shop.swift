import Combine

/// The shop's state. Observed only by the screens that show the cart, so adding to it doesn't
/// rebuild, and log again, the product page.
final class Shop: ObservableObject {
    @Published var cart = Cart()
}
