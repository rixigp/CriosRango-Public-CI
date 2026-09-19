package es.criosrango.app

/**
 * Keeps checkout compatibility with the account address editor while both
 * features continue to use their existing address models.
 */
fun AccountViewModel.saveAddress(address: CustomerAddress) {
    saveAddress(
        AccountCustomerAddress(
            firstName = address.firstName,
            lastName = address.lastName,
            email = address.email,
            phone = address.phone,
            address1 = address.address1,
            postcode = address.postcode,
            city = address.city,
            state = address.state,
            country = address.country
        )
    )
}
