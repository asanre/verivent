package io.github.asanre.verivent.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import io.github.asanre.verivent.sample.developer.DeveloperScreen
import io.github.asanre.verivent.sample.shop.Cart
import io.github.asanre.verivent.sample.shop.CartScreen
import io.github.asanre.verivent.sample.shop.CatalogScreen
import io.github.asanre.verivent.sample.shop.ConfirmationScreen
import io.github.asanre.verivent.sample.shop.PaymentScreen
import io.github.asanre.verivent.sample.shop.Product
import io.github.asanre.verivent.sample.shop.ProductScreen
import io.github.asanre.verivent.ui.InspectorScreen
import kotlinx.coroutines.launch
import kotlin.random.Random

private sealed interface Screen {
    data object Catalog : Screen
    data class ProductPage(val product: Product) : Screen
    data object CartPage : Screen
    data object Payment : Screen
    data class Confirmation(val orderId: String) : Screen
    data object Developer : Screen
    data object Inspector : Screen
}

/** The shop, a back stack of its screens, and the Developer screen that opens Verivent. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SampleApp() {
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Catalog)) }
    var cart by remember { mutableStateOf(Cart()) }
    val scope = rememberCoroutineScope()
    val go = { screen: Screen -> stack = stack + screen }
    val back = { stack = stack.dropLast(1) }

    BackHandler(enabled = stack.size > 1, onBack = back)
    when (val screen = stack.last()) {
        Screen.Catalog -> CatalogScreen(onOpen = { go(Screen.ProductPage(it)) }, onDeveloper = { go(Screen.Developer) })
        is Screen.ProductPage -> ProductScreen(screen.product, onBack = back) {
            cart = cart.adding(screen.product)
            go(Screen.CartPage)
        }
        Screen.CartPage -> CartScreen(cart, onBack = back) { go(Screen.Payment) }
        Screen.Payment -> PaymentScreen(cart, onBack = back) {
            val orderId = "T-" + Random.nextInt(0x1000000).toString(16).uppercase().padStart(6, '0')
            val paid = cart
            Sample.shop.purchase(paid, orderId)
            scope.launch { runCatching { Sample.placeOrder(paid, orderId) } }
            cart = Cart()
            stack = listOf(Screen.Catalog, Screen.Confirmation(orderId))
        }
        is Screen.Confirmation -> ConfirmationScreen(screen.orderId) { stack = listOf(Screen.Catalog) }
        Screen.Developer -> DeveloperScreen(onBack = back) { go(Screen.Inspector) }
        Screen.Inspector -> InspectorScreen(Modifier.fillMaxSize())
    }
}
