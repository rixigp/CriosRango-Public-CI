# CriosRango Android

Aplicación Android nativa de CriosRango para explorar la tienda infantil.

- UI nativa con Kotlin, Jetpack Compose y arquitectura MVVM.
- Catálogo, categorías, búsqueda, detalle de producto y variaciones mediante la WooCommerce Store API pública de `criosrango.es`.
- Carrito local persistente; el checkout requiere completarse en tienda y no se envían credenciales desde la aplicación.
- GitHub Actions genera un APK debug en cada push a `main`/`master` y manualmente desde Actions.

## APK
En GitHub: **Actions → Build Android APK → último run → Artifacts → CriosRango-debug-apk**.

La aplicación usa Retrofit/OkHttp para red y Coil para imágenes. Las descripciones HTML se convierten a texto nativo y la interfaz muestra estados de carga, errores y productos sin incrustar la web dentro de la app.

## Direcciones
El checkout usa entrada manual únicamente. Cada campo se valida de forma individual y el usuario debe completar los datos obligatorios para continuar.

<!-- temporary brands runner trigger -->
<!-- temporary brands runner trigger 2 -->
