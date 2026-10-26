package es.criosrango.shared

/** Canonical root product scopes shared by Android and iOS. Null category means unscoped catalog. */
enum class StoreRootFilterScope(val categoryQueryId: Int?, val queryKey: String, val title: String) {
    OUTLET(445, "root-filter:outlet:445", "Outlet"),
    CATALOG(null, "root-filter:catalog:unscoped", "Productos")
}
