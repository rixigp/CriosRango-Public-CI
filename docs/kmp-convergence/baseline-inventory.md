# KMP convergence — Batch 0 baseline inventory

Repository `rixigp/CriosRango-Public-CI`; baseline `main` at `610aa645633a2895d7a992bc8848bcb9022afca0`. Documentation-only inventory.

## Gate report
- TOTAL_AREAS=30
- UNCLASSIFIED_AREAS=0 (each row has explicit current/target ownership; source inventory, not runtime parity proof)
- CATEGORY_MAPPING_COUNT=11
- CATEGORY_UNIQUE_ASSET_COUNT=11
- SUBCATEGORY_MAPPING_COUNT=40
- SUBCATEGORY_UNIQUE_ASSET_COUNT=40
- MAPPING_WITHOUT_FILE=0
- OUTLET_MAPPING_COUNT=8 (separate from subcategory count)
- BRAND_PRIMARY_SOURCE=WORDPRESS
- ANDROID_BASELINE_CODE_CHANGED=NO
- IOS_CODE_CHANGED=NO
- PRODUCTION_CODE_CHANGED=NO

Counts extracted from actual `categoryImageRes()` in `HomeScreen.kt` and all `subcat_*` branches in `subcategoryImageRes()` in `CategoriesScreen.kt`. The latter has 8 separate `outlet_*` branches. Every category/subcategory resource is unique by name and exists in the baseline Git tree.

## Source inspected
- `app/src/main/java/es/criosrango/app/HomeScreen.kt`: category mapping, home sections, outlet/Novedades.
- `app/src/main/java/es/criosrango/app/CategoriesScreen.kt`: subcategory/outlet mapping, category pages, filters, grids, skeletons.
- `app/src/main/java/es/criosrango/app/MainActivity.kt`: `AppTab`, host navigation, `StoreTopBar`, startup and Android OS event boundaries.
- `app/src/main/java/es/criosrango/app/SharedUi.kt`: Android remote images use Coil Compose; don't change the image dependency in Foundation/Components.
- `shared/src/commonMain/kotlin/es/criosrango/shared/IosStoreRootScreen.kt`, `IosCatalogScreen.kt`, `IosAccountScreen.kt`, `IosCheckoutScreen.kt`, `IosStoreDiscoveryScreens.kt`: iOS-branded commonMain UI files that still duplicate Android UI.
- `shared/src/commonMain/kotlin/es/criosrango/shared/StoreCartStore.kt`, `StoreCheckoutStore.kt`, `StoreHttpClient.kt` and platform actuals: shared state/API and legitimate OkHttp/Darwin engine split.
- `shared/build.gradle.kts`: Compose Resources, Foundation, Material3 and UI already available in commonMain.

## categoryImageRes() inventory

| ENTRY_KEY | RESOURCE_NAME | FILE_PATH | FILE_EXISTS | DIMENSIONS | FORMAT |
|---|---|---|---|---|---|
| `contains(bautizo)` | `category_bautizo` | `app/src/main/res/drawable-nodpi/category_bautizo.png` | YES | 1254x1254 | PNG |
| `contains(bebe)&contains(nina)` | `category_bebe_nina` | `app/src/main/res/drawable-nodpi/category_bebe_nina.png` | YES | 1254x1254 | PNG |
| `contains(bebe)&contains(nino)` | `category_bebe_nino` | `app/src/main/res/drawable-nodpi/category_bebe_nino.png` | YES | 1254x1254 | PNG |
| `contains(calzado)` | `category_calzado` | `app/src/main/res/drawable-nodpi/category_calzado.png` | YES | 512x512 | PNG |
| `contains(comunion)&contains(nina)` | `category_comunion_nina` | `app/src/main/res/drawable-nodpi/category_comunion_nina.png` | YES | 1254x1254 | PNG |
| `contains(comunion)&contains(nino)` | `category_comunion_nino` | `app/src/main/res/drawable-nodpi/category_comunion_nino.png` | YES | 1254x1254 | PNG |
| `contains(recien)` | `category_recien_nacido` | `app/src/main/res/drawable-nodpi/category_recien_nacido.png` | YES | 1254x1254 | PNG |
| `trim==hombre` | `category_hombre` | `app/src/main/res/drawable-nodpi/category_hombre.png` | YES | 1254x1254 | PNG |
| `trim==mujer` | `category_mujer` | `app/src/main/res/drawable-nodpi/category_mujer.png` | YES | 1254x1254 | PNG |
| `trim==nina` | `category_nina` | `app/src/main/res/drawable-nodpi/category_nina.png` | YES | 1254x1254 | PNG |
| `trim==nino` | `category_nino` | `app/src/main/res/drawable-nodpi/category_nino.png` | YES | 1254x1254 | PNG |

## subcategoryImageRes() inventory

| PARENT_KEY | ENTRY_KEY | RESOURCE_NAME | FILE_PATH | FILE_EXISTS | DIMENSIONS | FORMAT |
|---|---|---|---|---|---|---|
| UNKNOWN | `na_abrigos_cazadoras` | `subcat_bebe_nina_abrigos_cazadoras` | `app/src/main/res/drawable-nodpi/subcat_bebe_nina_abrigos_cazadoras.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `na_ropa_bano` | `subcat_bebe_nina_ropa_bano` | `app/src/main/res/drawable-nodpi/subcat_bebe_nina_ropa_bano.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `na_ropa_sport` | `subcat_bebe_nina_ropa_sport` | `app/src/main/res/drawable-nodpi/subcat_bebe_nina_ropa_sport.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `na_ropa_vestir` | `subcat_bebe_nina_ropa_vestir` | `app/src/main/res/drawable-nodpi/subcat_bebe_nina_ropa_vestir.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `no_abrigos_cazadoras` | `subcat_bebe_nino_abrigos_cazadoras` | `app/src/main/res/drawable-nodpi/subcat_bebe_nino_abrigos_cazadoras.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `no_ropa_bano` | `subcat_bebe_nino_ropa_bano` | `app/src/main/res/drawable-nodpi/subcat_bebe_nino_ropa_bano.png` | YES | NOT_VERIFIED (binary endpoint returned empty content) | PNG |
| UNKNOWN | `no_ropa_sport` | `subcat_bebe_nino_ropa_sport` | `app/src/main/res/drawable-nodpi/subcat_bebe_nino_ropa_sport.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `no_ropa_vestir` | `subcat_bebe_nino_ropa_vestir` | `app/src/main/res/drawable-nodpi/subcat_bebe_nino_ropa_vestir.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `rigos_cazadoras` | `subcat_nina_abrigos_cazadoras` | `app/src/main/res/drawable-nodpi/subcat_nina_abrigos_cazadoras.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `lzado` | `subcat_nina_calzado` | `app/src/main/res/drawable-nodpi/subcat_nina_calzado.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `pa_bano` | `subcat_nina_ropa_bano` | `app/src/main/res/drawable-nodpi/subcat_nina_ropa_bano.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `pa_sport` | `subcat_nina_ropa_sport` | `app/src/main/res/drawable-nodpi/subcat_nina_ropa_sport.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `pa_vestir` | `subcat_nina_ropa_vestir` | `app/src/main/res/drawable-nodpi/subcat_nina_ropa_vestir.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `rigos_cazadoras` | `subcat_nino_abrigos_cazadoras` | `app/src/main/res/drawable-nodpi/subcat_nino_abrigos_cazadoras.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `pa_bano` | `subcat_nino_ropa_bano` | `app/src/main/res/drawable-nodpi/subcat_nino_ropa_bano.png` | YES | 1024x1024 | PNG |
| UNKNOWN | `pa_sport` | `subcat_nino_ropa_sport` | `app/src/main/res/drawable-nodpi/subcat_nino_ropa_sport.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `pa_vestir` | `subcat_nino_ropa_vestir` | `app/src/main/res/drawable-nodpi/subcat_nino_ropa_vestir.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `nacido_abrigos_cazadoras` | `subcat_recien_nacido_abrigos_cazadoras` | `app/src/main/res/drawable-nodpi/subcat_recien_nacido_abrigos_cazadoras.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `nacido_bodys` | `subcat_recien_nacido_bodys` | `app/src/main/res/drawable-nodpi/subcat_recien_nacido_bodys.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `nacido_complementos` | `subcat_recien_nacido_complementos` | `app/src/main/res/drawable-nodpi/subcat_recien_nacido_complementos.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `nacido_jesusitos_vestidos` | `subcat_recien_nacido_jesusitos_vestidos` | `app/src/main/res/drawable-nodpi/subcat_recien_nacido_jesusitos_vestidos.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `nacido_peleles_conjuntos` | `subcat_recien_nacido_peleles_conjuntos` | `app/src/main/res/drawable-nodpi/subcat_recien_nacido_peleles_conjuntos.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `abrigos_cazadoras` | `subcat_hombre_abrigos_cazadoras` | `app/src/main/res/drawable-nodpi/subcat_hombre_abrigos_cazadoras.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `americanas_trajes` | `subcat_hombre_americanas_trajes` | `app/src/main/res/drawable-nodpi/subcat_hombre_americanas_trajes.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `camisas` | `subcat_hombre_camisas` | `app/src/main/res/drawable-nodpi/subcat_hombre_camisas.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `camisetas_polos` | `subcat_hombre_camisetas_polos` | `app/src/main/res/drawable-nodpi/subcat_hombre_camisetas_polos.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `complementos_bano` | `subcat_hombre_complementos_bano` | `app/src/main/res/drawable-nodpi/subcat_hombre_complementos_bano.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `jerseis_chaquetas` | `subcat_hombre_jerseis_chaquetas` | `app/src/main/res/drawable-nodpi/subcat_hombre_jerseis_chaquetas.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `pantalones_bermudas` | `subcat_hombre_pantalones_bermudas` | `app/src/main/res/drawable-nodpi/subcat_hombre_pantalones_bermudas.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `sudaderas` | `subcat_hombre_sudaderas` | `app/src/main/res/drawable-nodpi/subcat_hombre_sudaderas.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `brigos_cazadoras` | `subcat_mujer_abrigos_cazadoras` | `app/src/main/res/drawable-nodpi/subcat_mujer_abrigos_cazadoras.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `amisas_camisetas` | `subcat_mujer_camisas_camisetas` | `app/src/main/res/drawable-nodpi/subcat_mujer_camisas_camisetas.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `haquetas_chalecos` | `subcat_mujer_chaquetas_chalecos` | `app/src/main/res/drawable-nodpi/subcat_mujer_chaquetas_chalecos.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `omplementos` | `subcat_mujer_complementos` | `app/src/main/res/drawable-nodpi/subcat_mujer_complementos.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `erseis` | `subcat_mujer_jerseis` | `app/src/main/res/drawable-nodpi/subcat_mujer_jerseis.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `antalones_faldas` | `subcat_mujer_pantalones_faldas` | `app/src/main/res/drawable-nodpi/subcat_mujer_pantalones_faldas.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `opa_fiesta` | `subcat_mujer_ropa_fiesta` | `app/src/main/res/drawable-nodpi/subcat_mujer_ropa_fiesta.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `estidos_conjuntos_monos` | `subcat_mujer_vestidos_conjuntos_monos` | `app/src/main/res/drawable-nodpi/subcat_mujer_vestidos_conjuntos_monos.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `iesta_conjuntos_monos` | `subcat_mujer_fiesta_conjuntos_monos` | `app/src/main/res/drawable-nodpi/subcat_mujer_fiesta_conjuntos_monos.png` | YES | 1254x1254 | PNG |
| UNKNOWN | `iesta_vestidos` | `subcat_mujer_fiesta_vestidos` | `app/src/main/res/drawable-nodpi/subcat_mujer_fiesta_vestidos.png` | YES | 1254x1254 | PNG |

**Dimensions caveat:** GitHub's binary-content endpoint returned an empty body for `subcat_bebe_nino_ropa_bano.png`; path/existence are verified, dimensions are not guessed. The required mapping/file gate is satisfied. Outlet/splash dimensions were not individually decoded.

## Other static app assets

The manifest also records the Android legacy `brand_*.png` set, Android adaptive-launcher images/XML, notification and brand vector drawables, and iOS `AppIcon.appiconset` PNGs. The 20 bundled `brand_*.png` files are classified as legacy local assets with callers to audit; they are **not** the primary brand-image source and must not override WordPress/WooCommerce imagery. They remain untouched until Batch 8 and only retire after proving no references remain.

## Asset policy
- Canonical local target: `shared/src/commonMain/composeResources/drawable/`, loaded with Compose Resources.
- Keep existing Android assets intact through Batch 1 and until Batch 8 retirement gates.
- Reuse existing `shared/src/commonMain/composeResources/drawable/home_logo.png`; do not create a duplicate home logo.
- Brand primary imagery stays remote from WordPress/WooCommerce; do not package brand logos.
- Remote product image pipeline is a later evidence-based batch. No Kamel, Coil Multiplatform or new image library in Foundation/Components.
- `asset-manifest.csv` contains category/subcategory/outlet/logo/brand-legacy/platform-icon and remote asset entries; uncertain callers are explicitly marked for verification.
- `screen-parity-matrix.csv` records 30 areas with entry points, current/target owner, migration/retirement candidates, navigation, loading/success/empty/error, adapters and acceptance criteria.

## Workflow trigger caveat
Current `.github/workflows/kmp-migration.yml` has `paths-ignore: .github/workflows/**`, not a docs-only ignore. A docs-only push to `main` can trigger an app workflow. Workflows were not changed.
