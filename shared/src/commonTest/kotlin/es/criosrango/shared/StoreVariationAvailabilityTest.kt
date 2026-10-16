package es.criosrango.shared
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
class StoreVariationAvailabilityTest {
 @Test fun availability_requires_both_flags_true() {
  assertTrue(isVariationAvailableForPurchase(true, true))
  assertFalse(isVariationAvailableForPurchase(true, null)); assertFalse(isVariationAvailableForPurchase(null, true))
  assertFalse(isVariationAvailableForPurchase(null, null)); assertFalse(isVariationAvailableForPurchase(false, true))
  assertFalse(isVariationAvailableForPurchase(true, false)); assertFalse(isVariationAvailableForPurchase(false, false))
 }
 @Test fun attribute_matching_is_normalized_literal_and_empty_is_not_wildcard() {
  assertTrue(variationAttributeValuesMatch(" Azul ", "azul")); assertTrue(variationAttributeValuesMatch("XXL", "xxl"))
  assertFalse(variationAttributeValuesMatch("", "XXL")); assertFalse(variationAttributeValuesMatch(null, "XXL"))
  assertFalse(variationAttributeValuesMatch("XXL", "")); assertFalse(variationAttributeValuesMatch("XL", "XXL"))
 }
}
