#!/usr/bin/env python3
"""Read-only static gate for the shared Outlet architecture.

Reads only checked-out source files. No network, Git history, branch names,
commit messages, or baseline SHA are consulted.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
FILES = {
    "android": "app/src/main/java/es/criosrango/app/CategoriesScreen.kt",
    "ios_discovery": "shared/src/commonMain/kotlin/es/criosrango/shared/IosStoreDiscoveryScreens.kt",
    "ios_root": "shared/src/commonMain/kotlin/es/criosrango/shared/IosStoreRootScreen.kt",
    "ios_catalog": "shared/src/commonMain/kotlin/es/criosrango/shared/IosCatalogScreen.kt",
    "controller": "shared/src/commonMain/kotlin/es/criosrango/shared/StoreOutletController.kt",
    "adapter": "shared/src/commonMain/kotlin/es/criosrango/shared/StoreOutletDataSourceAdapter.kt",
    "filter_api": "shared/src/commonMain/kotlin/es/criosrango/shared/api/StoreFilterApiClient.kt",
    "main_activity": "app/src/main/java/es/criosrango/app/MainActivity.kt",
    "shop_view_model": "app/src/main/java/es/criosrango/app/ShopViewModel.kt",
    "controller_tests": "shared/src/commonTest/kotlin/es/criosrango/shared/StoreOutletControllerTest.kt",
    "filter_api_tests": "shared/src/commonTest/kotlin/es/criosrango/shared/api/StoreFilterApiClientTest.kt",
    "hydration_tests": "shared/src/commonTest/kotlin/es/criosrango/shared/api/StoreApiClientCatalogTest.kt",
}


def read_sources():
    sources, missing = {}, []
    for key, relative in FILES.items():
        try:
            sources[key] = (ROOT / relative).read_text(encoding="utf-8")
        except (OSError, UnicodeError):
            sources[key] = ""
            missing.append(relative)
    return sources, missing


def region(source, start_marker, end_marker, start_at=0):
    start = source.find(start_marker, start_at)
    if start < 0:
        return ""
    end = source.find(end_marker, start + len(start_marker))
    return source[start:end] if end >= 0 else ""


def all_in(source, fragments):
    return all(fragment in source for fragment in fragments)


def run_checks():
    src, missing = read_sources()
    android = region(src["android"], "private fun OutletControlledProductGrid(", "@Composable")
    ios = region(src["ios_discovery"], "internal fun IosOutletScreen(", "internal fun IosPagedProductScreen")
    catalog, controller = src["ios_catalog"], src["controller"]
    adapter, filter_api = src["adapter"], src["filter_api"]

    chooser_filter = ""
    chooser_at = ios.find("if (showOutletChooser) {")
    if chooser_at >= 0:
        filter_start = ios.find("onFilterClick = {", chooser_at)
        if filter_start >= 0:
            filter_end = ios.find("},", filter_start)
            if filter_end >= 0:
                chooser_filter = ios[filter_start:filter_end]

    dialog = ""
    dialog_start = src["android"].find("private fun OutletControllerFilterDialog(")
    if dialog_start >= 0:
        dialog_end = src["android"].find("@Composable", dialog_start + 1)
        if dialog_end >= 0:
            dialog = src["android"][dialog_start:dialog_end]

    checks = {
        "ANDROID_ACTIVE_OUTLET_USES_COMMON_CONTROLLER": (
            "controller: es.criosrango.shared.StoreOutletController" in android
            and "controller.state.collectAsStateWithLifecycle()" in android
        ),
        "IOS_ACTIVE_OUTLET_USES_SAME_CONTROLLER": (
            "controller: StoreOutletController" in ios
            and "controllerState by controller.state.collectAsState()" in ios
        ),
        "OUTLET_PRESENTATION_FULL_SHARED": (
            "var showOutletChooser by remember(outletController)" in catalog
            and "showOutletChooser = showOutletChooser" in catalog
            and "gridState = outletGridState" in catalog
            and "var showOutletChooser by remember(outlet.id)" not in ios
            and "val gridState = rememberLazyGridState()" not in ios
            and "StoreProductListScreen(" in android
            and "StoreProductListScreen(" in ios
            and "storeOutletDataSource(sharedCatalogClient)" in src["main_activity"]
            and "StoreOutletController(viewModelScope, outletDataSource)" in src["shop_view_model"]
            and "StoreOutletController(outletControllerScope, storeOutletDataSource(storeApi))" in src["ios_root"]
        ),
        "OUTLET_STATE_FULL_SHARED": (
            all_in(controller, (
                "draftSizes", "draftColors", "draftBrands", "selectedSizes", "selectedColors", "selectedBrands",
                "fun openFilters()", "fun cancelFilters()", "fun applyFilters()", "fun clearFilters()",
                "facetGenerations", "facetCache", "fun selectFacetDimension", "private fun requestFacet",
                "private fun draftQuery", "private val paginators", "private fun activateCurrentQuery",
                "private fun startInitial", "private fun requestNext", "retainedSelections",
                "paginators = linkedMapOf", "facetGenerations[dimension] == generation",
                "facetKey(dimension, currentQuery) == key",
            ))
            and "CatalogPaginatorStore" not in android
            and "loadNextCategoryPage" not in android
            and "CatalogPaginatorStore<StoreProduct>" not in ios
            and "paginator.loadNext" not in ios
            and "var filtersOpen" not in android and "fun matches(" not in android
            and "var filtersOpen" not in ios and "var selectedSizes" not in ios and "fun matches(" not in ios
            and "fun matches(" not in controller and "productBelongsToOutletBubble" not in controller
            and ".sortedBy" not in controller and ".sortedByDescending" not in controller
            and "onFilterClick = controller::openFilters" in ios
            and "controllerState.filterDialogVisible" in ios
            and "selectedSizes = controllerState.draftSizes" in ios
            and "controller.openFilters()" in chooser_filter
            and "onShowOutletChooserChange(false)" not in chooser_filter
            and ios.count("if (controllerState.filterDialogVisible)") == 1
            and "onDismissRequest = controller::cancelFilters" in ios
            and "onDismiss = controller::cancelFilters" in ios
            and "onCancel = controller::cancelFilters" in ios
            and "controller::cancelFilters" in ios
            and "LaunchedEffect(controllerState.activeCategoryId, controllerState.activeFilterCount, outlet.id)" in ios
            and "onShowOutletChooserChange(isRootCategory && controllerState.activeFilterCount == 0)" in ios
            and "onClearFilters = controller::clearFilters" in ios
            and "onFilterClick = {" in src["android"]
            and "outletController.selectCategory(current.id)" in src["android"]
            and "outletController.openFilters()" in src["android"]
            and src["android"].count("OutletControllerFilterDialog(controller, outletControllerState)") == 1
            and all_in(dialog, (
                "controller: es.criosrango.shared.StoreOutletController",
                "state: es.criosrango.shared.StoreOutletState", "state.filterDialogVisible",
                "selectedSizes = state.draftSizes", "selectedColors = state.draftColors",
                "selectedBrands = state.draftBrands", "onDismissRequest = controller::cancelFilters",
                "onDismiss = controller::cancelFilters", "onCancel = controller::cancelFilters",
            ))
            and "onApply = { controller.applyFilters() }" in src["android"]
            and "onFiltersApplied" not in src["android"]
            and "showOutletProductsFromFilters" not in src["android"]
            and "StoreRootFilterScope.OUTLET" not in android and "rootFilterOwner" not in android
            and "StoreRootFilterScope.OUTLET" not in ios and "onOpenRootFilter" not in ios
        ),
        "OUTLET_BUSINESS_LOGIC_FULL_SHARED": (
            all_in(controller, (
                "val categoryId: Int", "val originCategoryIds: List<Int>", "val sizeIds: List<Int>",
                "val colorIds: List<Int>", "val brandIds: List<Int>", "val sort: StoreOutletSort",
                "Page is deliberately not part of query identity",
                "originCategoryIds = originCategoryIds.distinct().sorted()",
                "sizeIds = sizeIds.distinct().sorted()", "colorIds = colorIds.distinct().sorted()",
                "brandIds = brandIds.distinct().sorted()", "facetGenerations[dimension] == generation",
                "facetKey(dimension, currentQuery) == key",
            ))
            and all_in(filter_api, (
                'parameter("outlet_category_id", it)', 'parameter("origin_category_ids"',
                "originCategoryIds.distinct().sorted().joinToString",
            ))
            and all_in(adapter, (
                'scope = "outlet"', "outletCategoryId = q.categoryId", "originCategoryIds = q.originCategoryIds",
                "sizeIds = q.sizeIds", "colorIds = q.colorIds", "brandIds = q.brandIds",
                "sort = q.sort.toBackendSort()", "storeApi.productsByIds(response.productIds)",
                "response.productIds.isEmpty()", "CatalogPage(products, response.hasMore, response.total)",
                "price_asc", "price_desc", "title_asc", "newest",
            ))
            and "storeApi.products(" not in adapter
            and all_in(src["controller_tests"], (
                "query_identity_contains_category_bubble_filters_and_sort_but_not_page",
                "draft_changes_do_not_reload_products_until_apply_and_apply_resets_page_one",
                "bubble_selection_becomes_origin_context_and_restarts_query",
                "sort_is_backend_query_identity_and_restarts_page_one",
                "near_end_prefetch_appends_exactly_one_next_page",
                "facet_query_preserves_category_bubble_and_other_draft_dimensions",
                "stale_facet_response_cannot_overwrite_newer_draft",
                "controller_retains_paginator_per_full_query_identity",
                "missing_availability_fails_closed", "unsupported_availability_schema_fails_closed",
                "positive_availability_shows_bubble", "unavailable_bubble_selection_is_ignored",
                "bubble_selection_does_not_change_common_screen_title",
            ))
            and all_in(src["filter_api_tests"], (
                "query_preserves_scope_context_canonical_ids_pagination_sort_and_facet",
                "null_category_and_empty_origins_are_omitted",
            ))
            and all_in(src["hydration_tests"], (
                "outletDataSourceUsesFilterApiThenSingleOrderedHydrationBatch",
                "outletDataSourceSkipsStoreHydrationWhenFilterApiReturnsNoIds",
                "outletDataSourceFailsClosedWhenStoreHydrationOmitsBackendId",
            ))
        ),
    }

    names = (
        "ANDROID_ACTIVE_OUTLET_USES_COMMON_CONTROLLER",
        "IOS_ACTIVE_OUTLET_USES_SAME_CONTROLLER",
        "OUTLET_PRESENTATION_FULL_SHARED",
        "OUTLET_STATE_FULL_SHARED",
        "OUTLET_BUSINESS_LOGIC_FULL_SHARED",
    )
    for name in names:
        print(f"{name}={'PASS' if checks[name] else 'FAIL'}")
    overall = all(checks.values()) and not missing
    if missing:
        print("MISSING_REQUIRED_FILES=" + ",".join(sorted(missing)))
    print(f"OUTLET_SHARED_ARCHITECTURE={'PASS' if overall else 'FAIL'}")
    return 0 if overall else 1


if __name__ == "__main__":
    sys.exit(run_checks())
