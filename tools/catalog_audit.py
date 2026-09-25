#!/usr/bin/env python3
"""Read-only WooCommerce Store API vs app catalog audit.

WooCommerce view = direct category assignment exposed by the complete Store
API catalog. App view mirrors CategoryCatalogCache.queryCategoryTree(): the
complete global snapshot is deduplicated by product_id and category descendants
are included. No endpoint or database is modified.
"""
from __future__ import annotations

import argparse
import json
import sys
import urllib.parse
import urllib.request
from collections import Counter
from typing import Any

DEFAULT_BASE = "https://criosrango.es/wp-json/wc/store/v1/"
PAGE_SIZE = 100


def fetch_json(base: str, path: str, params: dict[str, Any]) -> tuple[Any, dict[str, str]]:
    url = base.rstrip("/") + "/" + path.lstrip("/")
    url += "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(
        url,
        headers={"Accept": "application/json", "User-Agent": "CriosRango-Catalog-Audit/1.0"},
    )
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.loads(response.read().decode("utf-8")), {k.lower(): v for k, v in response.headers.items()}


def fetch_all(base: str, path: str) -> tuple[list[dict[str, Any]], int, list[int]]:
    rows: list[dict[str, Any]] = []
    pages: list[int] = []
    page = 1
    while True:
        body, headers = fetch_json(base, path, {"per_page": PAGE_SIZE, "page": page})
        if not isinstance(body, list):
            raise RuntimeError(f"Unexpected response for {path} page {page}")
        pages.append(page)
        rows.extend(body)
        total_pages = int(headers["x-wp-totalpages"]) if headers.get("x-wp-totalpages", "").isdigit() else None
        if total_pages is not None:
            if page >= total_pages:
                break
        elif len(body) < PAGE_SIZE:
            break
        page += 1
    return rows, len(pages), pages


def category_descendants(category_id: int, categories: dict[int, dict[str, Any]]) -> set[int]:
    children: dict[int, list[int]] = {}
    for category in categories.values():
        children.setdefault(int(category.get("parent", 0)), []).append(int(category["id"]))
    result: set[int] = set()
    queue = [category_id]
    while queue:
        current = queue.pop(0)
        if current in result:
            continue
        result.add(current)
        queue.extend(children.get(current, []))
    return result


def direct_category_ids(product: dict[str, Any]) -> set[int]:
    return {int(c["id"]) for c in product.get("categories", []) if isinstance(c, dict) and "id" in c}


def outlet_extension(product: dict[str, Any]) -> list[int]:
    extensions = product.get("extensions") or {}
    outlet = extensions.get("criosrango_outlet") if isinstance(extensions, dict) else None
    values = outlet.get("original_category_ids") if isinstance(outlet, dict) else None
    return [int(x) for x in values] if isinstance(values, list) and all(str(x).lstrip("-").isdigit() for x in values) else []


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=DEFAULT_BASE)
    parser.add_argument("--output", default="catalog-audit.json")
    args = parser.parse_args()

    raw_categories, category_pages, _ = fetch_all(args.base_url, "products/categories")
    raw_products, product_pages, product_page_numbers = fetch_all(args.base_url, "products")

    categories: dict[int, dict[str, Any]] = {int(c["id"]): c for c in raw_categories if "id" in c}
    raw_ids = [int(p["id"]) for p in raw_products if "id" in p]
    duplicate_product_ids = sorted(pid for pid, n in Counter(raw_ids).items() if n > 1)
    products_by_id = {int(p["id"]): p for p in raw_products if "id" in p}

    outlet = next(
        (c for c in categories.values() if str(c.get("slug", "")).lower() == "outlet" or str(c.get("name", "")).lower() == "outlet"),
        None,
    )
    outlet_id = int(outlet["id"]) if outlet else None
    outlet_tree = category_descendants(outlet_id, categories) if outlet_id is not None else set()

    visible = [
        c for c in categories.values()
        if int(c.get("count", 0)) > 0 or (outlet_id is not None and int(c["id"]) in outlet_tree)
    ]
    visible.sort(key=lambda c: (int(c.get("parent", 0)), str(c.get("name", "")).lower(), int(c["id"])))

    category_rows = []
    for category in visible:
        cid = int(category["id"])
        subtree = category_descendants(cid, categories)
        woo_ids = sorted(pid for pid, product in products_by_id.items() if cid in direct_category_ids(product))
        app_ids = sorted(pid for pid, product in products_by_id.items() if direct_category_ids(product) & subtree)
        woo_set, app_set = set(woo_ids), set(app_ids)
        category_rows.append({
            "category_id": cid,
            "category_name": category.get("name", ""),
            "parent_id": int(category.get("parent", 0)),
            "woocommerce_count": len(woo_ids),
            "app_count": len(app_ids),
            "woocommerce_product_ids": woo_ids,
            "app_product_ids": app_ids,
            "missing_in_app": sorted(woo_set - app_set),
            "extra_in_app": sorted(app_set - woo_set),
        })

    outlet_products = []
    outlet_ids: set[int] = set()
    with_original = 0
    without_original = 0
    for pid, product in products_by_id.items():
        current_ids = direct_category_ids(product)
        matched = current_ids & outlet_tree
        if not matched:
            continue
        outlet_ids.add(pid)
        original_ids = outlet_extension(product)
        if original_ids:
            with_original += 1
        else:
            without_original += 1
        outlet_names = sorted({str(categories[cid].get("name", "")) for cid in matched if cid in categories and cid != outlet_id})
        outlet_products.append({
            "product_id": pid,
            "name": product.get("name", ""),
            "current_category_ids": sorted(current_ids),
            "outlet_category": outlet_names,
            "original_category_ids": original_ids,
            "has_original_category_ids": bool(original_ids),
        })
    outlet_products.sort(key=lambda x: (str(x["name"]).lower(), x["product_id"]))

    report = {
        "read_only": True,
        "api_base": args.base_url,
        "page_size": PAGE_SIZE,
        "pagination": {
            "categories_pages": category_pages,
            "products_pages": product_pages,
            "product_pages_seen": product_page_numbers,
            "complete": True,
        },
        "rules": {
            "woocommerce": "Complete Store API snapshot, deduplicated by product_id; direct category assignment only.",
            "app": "Complete Store API snapshot, deduplicated by product_id; CategoryCatalogCache.queryCategoryTree semantics, including descendants.",
            "original_category_ids": "read-only extension data; never used to mutate category membership",
        },
        "categories": category_rows,
        "outlet": {
            "outlet_category_id": outlet_id,
            "outlet_distinct_products": len(outlet_ids),
            "with_original_category_ids": with_original,
            "without_original_category_ids": without_original,
            "duplicate_product_ids": duplicate_product_ids,
            "products": outlet_products,
        },
        "summary": {
            "total_categories": len(category_rows),
            "total_distinct_products": len(products_by_id),
            "total_raw_product_rows": len(raw_products),
        },
    }

    with open(args.output, "w", encoding="utf-8") as fh:
        json.dump(report, fh, ensure_ascii=False, indent=2)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"CATALOG_AUDIT_FAILED: {exc}", file=sys.stderr)
        raise
