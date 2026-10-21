/// What the shop sells. The Compose sample sells the same, so both send the same analytics.
struct Product: Hashable, Identifiable {
    let id: String
    let name: String
    let price: Double
}

let catalog = [
    Product(id: "SN-42", name: "Desk lamp", price: 39.9),
    Product(id: "SN-7", name: "Notebook", price: 4.5),
    Product(id: "SN-13", name: "Coffee mug", price: 12.5),
]

let currency = "EUR"

func price(_ value: Double) -> String { String(format: "€%.2f", value) }
