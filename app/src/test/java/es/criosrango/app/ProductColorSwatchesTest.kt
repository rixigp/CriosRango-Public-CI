package es.criosrango.app

import kotlin.test.Test
import kotlin.test.assertNotNull

class ProductColorSwatchesTest {
    @Test fun realCatalogMappingsAreRepresentableAndHexRemainsSupported() {
        assertNotNull(productColorSwatch("Berenjena"), "productColorSwatch devolvió null para 'Berenjena'")
        assertNotNull(productColorSwatch("Caldero"), "productColorSwatch devolvió null para 'Caldero'")
        assertNotNull(productColorSwatch("Chicle"), "productColorSwatch devolvió null para 'Chicle'")
        assertNotNull(productColorSwatch("Coral"), "productColorSwatch devolvió null para 'Coral'")
        assertNotNull(productColorSwatch("Kaki"), "productColorSwatch devolvió null para 'Kaki'")
        assertNotNull(productColorSwatch("Oro"), "productColorSwatch devolvió null para 'Oro'")
        assertNotNull(productColorSwatch("Pistacho"), "productColorSwatch devolvió null para 'Pistacho'")
        assertNotNull(productColorSwatch("salmón"), "productColorSwatch devolvió null para 'salmón'")
        assertNotNull(productColorSwatch("#FFFFFF"), "productColorSwatch devolvió null para '#FFFFFF'")
        assertNotNull(productColorSwatch("#ffffff"), "productColorSwatch devolvió null para '#ffffff'")
    }
}
