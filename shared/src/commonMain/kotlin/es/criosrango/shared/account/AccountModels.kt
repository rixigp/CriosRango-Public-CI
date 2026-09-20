package es.criosrango.shared.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountLoginRequest(
    val login: String,
    val password: String
)

@Serializable
data class AccountRegisterRequest(
    val email: String,
    val password: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val phone: String = ""
)

@Serializable
data class AccountForgotPasswordRequest(
    val login: String
)

@Serializable
data class AccountUser(
    val id: Int,
    val email: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = ""
)

@Serializable
data class AccountLoginResponse(
    val token: String,
    val user: AccountUser
)

@Serializable
data class AccountMessageResponse(
    val success: Boolean = false,
    val message: String = ""
)

@Serializable
data class AccountMeResponse(
    val user: AccountUser
)
