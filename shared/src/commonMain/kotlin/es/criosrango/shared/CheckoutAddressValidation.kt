package es.criosrango.shared

import es.criosrango.shared.model.CustomerAddress

data class CheckoutProvince(val code: String, val name: String)
val CHECKOUT_SPANISH_PROVINCES = listOf(
    CheckoutProvince("C","A Coruña"),CheckoutProvince("VI","Álava"),CheckoutProvince("AB","Albacete"),CheckoutProvince("A","Alicante"),CheckoutProvince("AL","Almería"),CheckoutProvince("O","Asturias"),CheckoutProvince("AV","Ávila"),CheckoutProvince("BA","Badajoz"),CheckoutProvince("B","Barcelona"),CheckoutProvince("BI","Bizkaia"),CheckoutProvince("BU","Burgos"),CheckoutProvince("CC","Cáceres"),CheckoutProvince("CA","Cádiz"),CheckoutProvince("S","Cantabria"),CheckoutProvince("CS","Castellón"),CheckoutProvince("CE","Ceuta"),CheckoutProvince("CR","Ciudad Real"),CheckoutProvince("CO","Córdoba"),CheckoutProvince("CU","Cuenca"),CheckoutProvince("GI","Girona"),CheckoutProvince("GR","Granada"),CheckoutProvince("GU","Guadalajara"),CheckoutProvince("SS","Gipuzkoa"),CheckoutProvince("H","Huelva"),CheckoutProvince("HU","Huesca"),CheckoutProvince("J","Jaén"),CheckoutProvince("LE","León"),CheckoutProvince("L","Lleida"),CheckoutProvince("LO","La Rioja"),CheckoutProvince("LU","Lugo"),CheckoutProvince("M","Madrid"),CheckoutProvince("MA","Málaga"),CheckoutProvince("ML","Melilla"),CheckoutProvince("MU","Murcia"),CheckoutProvince("NA","Navarra"),CheckoutProvince("OR","Ourense"),CheckoutProvince("P","Palencia"),CheckoutProvince("GC","Las Palmas"),CheckoutProvince("PO","Pontevedra"),CheckoutProvince("SA","Salamanca"),CheckoutProvince("TF","Santa Cruz de Tenerife"),CheckoutProvince("SG","Segovia"),CheckoutProvince("SE","Sevilla"),CheckoutProvince("SO","Soria"),CheckoutProvince("T","Tarragona"),CheckoutProvince("TE","Teruel"),CheckoutProvince("TO","Toledo"),CheckoutProvince("V","Valencia"),CheckoutProvince("VA","Valladolid"),CheckoutProvince("ZA","Zamora"),CheckoutProvince("Z","Zaragoza")
)
private val POSTAL_PREFIXES = mapOf("M" to "28","CU" to "16","TO" to "45","B" to "08","V" to "46","A" to "03","AL" to "04","CA" to "11","S" to "39","CS" to "12","CR" to "13","CO" to "14","GR" to "18","GU" to "19","H" to "21","HU" to "22","J" to "23","LE" to "24","L" to "25","LO" to "26","LU" to "27","MA" to "29","MU" to "30","NA" to "31","OR" to "32","P" to "34","GC" to "35","PO" to "36","SA" to "37","TF" to "38","SG" to "40","SE" to "41","SO" to "42","T" to "43","TE" to "44","VA" to "47","ZA" to "49","Z" to "50","GI" to "17")
private val KNOWN_CITIES = mapOf("madrid" to "M","arganda del rey" to "M","alcalá de henares" to "M","getafe" to "M","leganés" to "M","rivas-vaciamadrid" to "M","móstoles" to "M","tarancón" to "CU","cuenca" to "CU","villanueva de la jara" to "CU","san clemente" to "CU","toledo" to "TO","talavera de la reina" to "TO","illescas" to "TO","mora" to "TO","barcelona" to "B","badalona" to "B","girona" to "B","lleida" to "B","valencia" to "V","torrent" to "V","paterna" to "V","alicante" to "A","elche" to "A","benidorm" to "A")
fun isCheckoutCityCompatibleWithProvince(provinceCode: String?, city: String): Boolean {
    if (city.isBlank()) return false
    val knownProvince = KNOWN_CITIES[city.trim().lowercase()]
    return knownProvince == null || knownProvince == provinceCode
}
fun isCheckoutPostcodeCompatibleWithProvince(provinceCode: String?, postcode: String): Boolean {
    if (postcode.length != 5 || postcode.any { !it.isDigit() }) return false
    return POSTAL_PREFIXES[provinceCode]?.let { postcode.startsWith(it) } ?: true
}
fun validateCheckoutAddress(address: CustomerAddress): String? = when {
    address.firstName.isBlank() -> "Indica tu nombre."
    address.lastName.isBlank() -> "Indica tus apellidos."
    !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(address.email.trim()) -> "Introduce un email válido."
    address.phone.isBlank() -> "Indica un teléfono."
    address.address1.isBlank() -> "Indica una dirección."
    CHECKOUT_SPANISH_PROVINCES.none { it.code == address.state } -> "Selecciona una provincia."
    address.city.isBlank() -> "Indica una localidad."
    !isCheckoutCityCompatibleWithProvince(address.state, address.city) -> "La localidad no corresponde a la provincia seleccionada."
    address.postcode.length != 5 || address.postcode.any { !it.isDigit() } -> "Introduce un código postal de 5 cifras."
    !isCheckoutPostcodeCompatibleWithProvince(address.state, address.postcode) -> "El código postal no corresponde a la provincia seleccionada."
    address.country != "ES" -> "Selecciona España."
    else -> null
}
