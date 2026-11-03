<?php
/**
 * Plugin Name: Críos & Rango — Order Product Data
 * Description: Enriches the existing authenticated orders-detailed REST response with safe product IDs and thumbnail URLs.
 * Version: 1.0.0
 * Author: Críos & Rango
 * Text Domain: criosrango-order-product-data
 */

defined( 'ABSPATH' ) || exit;

final class CriosRango_Order_Product_Data {
    const VERSION = '1.0.0';
    const ROUTE = '/criosrango/v1/orders-detailed';

    public static function boot() {
        add_filter( 'rest_post_dispatch', array( __CLASS__, 'enrich_response' ), 10, 3 );
    }

    public static function enrich_response( $response, $server, $request ) {
        if ( ! $request instanceof WP_REST_Request || self::ROUTE !== $request->get_route() ) {
            return $response;
        }
        if ( ! $response instanceof WP_REST_Response ) {
            return $response;
        }
        if ( $response->get_status() < 200 || $response->get_status() >= 300 ) {
            return $response;
        }
        if ( ! function_exists( 'wc_get_order' ) ) {
            return $response;
        }

        $data = $response->get_data();
        if ( ! is_array( $data ) || ! isset( $data['orders'] ) || ! is_array( $data['orders'] ) ) {
            return $response;
        }

        foreach ( $data['orders'] as &$order_data ) {
            if ( ! is_array( $order_data ) || empty( $order_data['id'] ) || ! is_numeric( $order_data['id'] ) ) {
                continue;
            }
            if ( ! isset( $order_data['items'] ) || ! is_array( $order_data['items'] ) ) {
                continue;
            }

            $order = wc_get_order( absint( $order_data['id'] ) );
            if ( ! $order || ! is_a( $order, 'WC_Order' ) ) {
                continue;
            }

            $line_items = $order->get_items( 'line_item' );
            if ( ! is_array( $line_items ) || empty( $line_items ) ) {
                continue;
            }

            $unused = array();
            foreach ( $line_items as $line_id => $line_item ) {
                if ( $line_item instanceof WC_Order_Item_Product ) {
                    $unused[(string) $line_id] = $line_item;
                }
            }

            foreach ( $order_data['items'] as &$response_item ) {
                if ( ! is_array( $response_item ) ) {
                    continue;
                }

                $matches = array();
                foreach ( $unused as $line_id => $line_item ) {
                    if ( self::items_match( $response_item, $line_item ) ) {
                        $matches[$line_id] = $line_item;
                    }
                }

                // Only a single exact candidate is safe. Ambiguous and unmatched rows stay untouched.
                if ( 1 !== count( $matches ) ) {
                    continue;
                }

                $matched_id = (string) array_key_first( $matches );
                $line_item = $matches[$matched_id];
                unset( $unused[$matched_id] );

                $product_id = absint( $line_item->get_product_id() );
                if ( $product_id <= 0 ) {
                    continue;
                }

                $response_item['product_id'] = $product_id;
                $image_url = self::thumbnail_url( $line_item, $product_id );
                if ( '' !== $image_url ) {
                    $response_item['image_url'] = $image_url;
                }
            }
            unset( $response_item );
        }
        unset( $order_data );

        $response->set_data( $data );
        return $response;
    }

    private static function normalize_text( $value ) {
        $value = html_entity_decode( wp_strip_all_tags( (string) $value ), ENT_QUOTES | ENT_HTML5, get_bloginfo( 'charset' ) ?: 'UTF-8' );
        $value = trim( preg_replace( '/\s+/u', ' ', $value ) );
        return function_exists( 'mb_strtolower' ) ? mb_strtolower( $value, 'UTF-8' ) : strtolower( $value );
    }

    private static function normalize_attribute_name( $name ) {
        $name = self::normalize_text( $name );
        if ( function_exists( 'wc_attribute_label' ) ) {
            $label = wc_attribute_label( $name );
            if ( is_string( $label ) && '' !== trim( $label ) ) {
                $name = self::normalize_text( $label );
            }
        }
        return $name;
    }

    private static function response_variations( $item ) {
        $attributes = array();
        if ( ! isset( $item['variations'] ) || ! is_array( $item['variations'] ) ) {
            return $attributes;
        }
        foreach ( $item['variations'] as $variation ) {
            if ( ! is_array( $variation ) || ! array_key_exists( 'name', $variation ) || ! array_key_exists( 'value', $variation ) ) {
                return null;
            }
            $name = self::normalize_attribute_name( $variation['name'] );
            $value = self::normalize_text( $variation['value'] );
            if ( '' === $name || '' === $value ) {
                return null;
            }
            $attributes[$name] = $value;
        }
        ksort( $attributes );
        return $attributes;
    }

    private static function woo_variations( $line_item ) {
        $attributes = array();
        foreach ( $line_item->get_formatted_meta_data( '' ) as $meta ) {
            if ( ! isset( $meta->display_key, $meta->display_value ) ) {
                continue;
            }
            $name = self::normalize_attribute_name( $meta->display_key );
            $value = self::normalize_text( $meta->display_value );
            if ( '' !== $name && '' !== $value ) {
                $attributes[$name] = $value;
            }
        }
        ksort( $attributes );
        return $attributes;
    }

    private static function items_match( $response_item, $line_item ) {
        if ( ! array_key_exists( 'name', $response_item ) || ! array_key_exists( 'quantity', $response_item ) ) {
            return false;
        }
        if ( self::normalize_text( $response_item['name'] ) !== self::normalize_text( $line_item->get_name() ) ) {
            return false;
        }
        if ( ! is_numeric( $response_item['quantity'] ) || (int) $response_item['quantity'] !== (int) $line_item->get_quantity() ) {
            return false;
        }
        $response_attributes = self::response_variations( $response_item );
        if ( null === $response_attributes ) {
            return false;
        }
        return $response_attributes === self::woo_variations( $line_item );
    }

    private static function thumbnail_url( $line_item, $parent_product_id ) {
        $variation_id = absint( $line_item->get_variation_id() );
        if ( $variation_id > 0 ) {
            $variation = wc_get_product( $variation_id );
            if ( $variation && is_a( $variation, 'WC_Product' ) ) {
                $url = self::product_thumbnail_url( $variation );
                if ( '' !== $url ) {
                    return $url;
                }
            }
        }

        $product = wc_get_product( $parent_product_id );
        if ( $product && is_a( $product, 'WC_Product' ) ) {
            return self::product_thumbnail_url( $product );
        }
        return '';
    }

    private static function product_thumbnail_url( $product ) {
        $image_id = absint( $product->get_image_id() );
        if ( $image_id <= 0 ) {
            return '';
        }
        $image = wp_get_attachment_image_src( $image_id, 'woocommerce_thumbnail' );
        if ( ! is_array( $image ) || empty( $image[0] ) ) {
            return '';
        }
        return esc_url_raw( $image[0] );
    }
}

CriosRango_Order_Product_Data::boot();
