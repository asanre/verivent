package io.github.asanre.verivent.sample.shop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.asanre.verivent.sample.Sample
import io.github.asanre.verivent.sample.SampleScreen
import io.github.asanre.verivent.sample.Wide

@Composable
fun CatalogScreen(onOpen: (Product) -> Unit, onDeveloper: () -> Unit) {
    LaunchedEffect(Unit) {
        Sample.shop.screen("catalog")
        Sample.shop.catalog(CATALOG)
    }
    SampleScreen(
        title = "Shop",
        action = { TextButton(onClick = onDeveloper, modifier = Modifier.testTag("catalog.developer")) { Text("Developer") } },
    ) {
        Text("A shop whose every step sends analytics. Developer opens Verivent.", style = MaterialTheme.typography.bodyMedium)
        CATALOG.forEach { product ->
            Wide("${product.name} · ${price(product.price)}", tag = "catalog.item.${product.id}") { onOpen(product) }
        }
    }
}

@Composable
fun ProductScreen(product: Product, onBack: () -> Unit, onAdd: () -> Unit) {
    LaunchedEffect(product) {
        Sample.shop.screen("product")
        Sample.shop.product(product)
    }
    SampleScreen(title = product.name, onBack = onBack) {
        Text(price(product.price), style = MaterialTheme.typography.titleMedium)
        Wide("Add to cart", tag = "product.addToCart") {
            Sample.shop.addToCart(product)
            onAdd()
        }
    }
}

@Composable
fun CartScreen(cart: Cart, onBack: () -> Unit, onCheckout: () -> Unit) {
    LaunchedEffect(Unit) { Sample.shop.screen("cart") }
    SampleScreen(title = "Cart", onBack = onBack) {
        cart.lines.forEach { Text("${it.quantity} × ${it.product.name}") }
        Text("Total ${price(cart.total)}", style = MaterialTheme.typography.titleMedium)
        Wide("Checkout", tag = "cart.checkout") {
            Sample.shop.checkout(cart)
            onCheckout()
        }
    }
}

@Composable
fun PaymentScreen(cart: Cart, onBack: () -> Unit, onPay: () -> Unit) {
    LaunchedEffect(Unit) { Sample.shop.screen("payment") }
    SampleScreen(title = "Payment", onBack = onBack) {
        Text("Total ${price(cart.total)}", style = MaterialTheme.typography.titleMedium)
        Wide("Pay", tag = "payment.pay", onClick = onPay)
    }
}

@Composable
fun ConfirmationScreen(orderId: String, onDone: () -> Unit) {
    LaunchedEffect(orderId) { Sample.shop.screen("confirmation") }
    SampleScreen(title = "Thank you") {
        Text("Order", style = MaterialTheme.typography.labelMedium)
        Text(orderId, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("confirmation.orderId"))
        Wide("Continue shopping", tag = "confirmation.continue", onClick = onDone)
    }
}

private fun price(value: Double): String {
    val cents = kotlin.math.round(value * 100).toLong()
    return "€${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
