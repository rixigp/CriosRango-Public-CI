package es.criosrango.shared
sealed interface StoreLoadState<out T> {
    data object Loading : StoreLoadState<Nothing>
    data class Content<T>(val value: T) : StoreLoadState<T>
    data object Empty : StoreLoadState<Nothing>
    data class Error(val message: String) : StoreLoadState<Nothing>
}