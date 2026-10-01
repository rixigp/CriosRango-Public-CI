<?php
declare(strict_types=1);

final class WP_REST_Server {
    public const CREATABLE = 'POST';
    public const DELETABLE = 'DELETE';
}

final class WP_Error {
    public function __construct(
        public string $code,
        public string $message,
        public array $data = []
    ) {}
}

function add_action(...$args): void {}
function register_activation_hook(...$args): void {}
function dbDelta(...$args): void {}
function is_wp_error(mixed $value): bool { return $value instanceof WP_Error; }
function rest_ensure_response(mixed $value): mixed { return $value; }
function sanitize_key($value): string { return preg_replace('/[^a-z0-9_\\-]/', '', strtolower((string)$value)); }
function get_post_meta($id, $key, $single = false): string { return ''; }
function update_post_meta($id, $key, $value): void {}
function wp_date($format): string { return '2026-10-01'; }
function wp_list_pluck(array $items, string $field): array { return array_map(fn($item) => $item->{$field}, $items); }
function wp_remote_post(...$args): array { return ['response' => ['code' => 200], 'body' => '{}']; }
function get_option($key): mixed { return null; }
function get_current_user_id(): int { return $GLOBALS['push_test_user_id'] ?? 0; }
function wc_get_order($id): object { return $GLOBALS['push_test_order']; }

final class FakeRequest {
    public function __construct(private array $params = [], private array $headers = []) {}
    public function get_param($key): mixed { return $this->params[$key] ?? null; }
    public function get_header($key): string { return $this->headers[strtolower($key)] ?? ''; }
}

final class FakeOrder {
    public function __construct(private int $customerId, private string $number = '123') {}
    public function get_customer_id(): int { return $this->customerId; }
    public function get_order_number(): string { return $this->number; }
}

final class FakeWpdb {
    public string $prefix = 'wp_';
    public int $insert_id = 0;
    public ?int $existingDeviceId = null;
    public ?object $digestRow = null;
    public ?object $eventRow = null;
    public array $productEvents = [];
    public array $devices = [];
    public array $deliveries = [];
    public array $queries = [];
    public array $updates = [];
    public array $insertedDeliveries = [];
    public array $lastInsertData = [];

    public function prepare(string $query, ...$args): string {
        foreach ($args as $arg) {
            $replacement = is_numeric($arg) ? (string)$arg : "'" . addslashes((string)$arg) . "'";
            $query = preg_replace('/%[sd]/', $replacement, $query, 1);
        }
        return $query;
    }

    public function get_var(string $query): mixed {
        if (str_contains($query, 'FROM wp_criosrango_push_devices') && str_contains($query, 'token_hash')) {
            return $this->existingDeviceId;
        }
        if (str_contains($query, 'FROM wp_criosrango_push_deliveries')) {
            preg_match('/event_id=([0-9]+)/', $query, $event);
            preg_match('/device_id=([0-9]+)/', $query, $device);
            return $this->deliveries[((int)($event[1] ?? 0)) . ':' . ((int)($device[1] ?? 0))] ?? null;
        }
        return null;
    }

    public function get_row(string $query): ?object {
        if (str_contains($query, 'idempotency_key') && str_contains($query, 'digest:')) return $this->digestRow;
        if (str_contains($query, 'FROM wp_criosrango_push_events WHERE id=')) return $this->eventRow;
        return null;
    }

    public function get_results(string $query): array {
        if (str_contains($query, "type='product_published'")) return $this->productEvents;
        if (str_contains($query, 'FROM wp_criosrango_push_devices')) {
            $isOrder = str_contains($query, 'order_updates=1');
            $isDigest = str_contains($query, 'new_products=1');
            if ($isOrder) {
                preg_match('/user_id=([0-9]+)/', $query, $m);
                $user = (int)($m[1] ?? 0);
                return array_values(array_filter($this->devices, fn($d) => (int)$d->user_id === $user && (int)$d->active === 1 && (int)$d->order_updates === 1));
            }
            if ($isDigest) return array_values(array_filter($this->devices, fn($d) => (int)$d->active === 1 && (int)$d->new_products === 1));
        }
        return [];
    }

    public function query(string $query): int {
        $this->queries[] = $query;
        if (str_starts_with(trim($query), 'INSERT IGNORE')) $this->insert_id++;
        return 1;
    }

    public function update(string $table, array $data, array $where): int {
        $this->updates[] = [$table, $data, $where];
        return 1;
    }

    public function insert(string $table, array $data): int {
        $this->lastInsertData = $data;
        $this->insert_id++;
        return 1;
    }
}

$GLOBALS['wpdb'] = new FakeWpdb();
define('ABSPATH', '/tmp/');

require_once dirname(__DIR__) . '/criosrango-push.php';

function push_assert(bool $condition, string $message): void {
    if (!$condition) throw new RuntimeException($message);
}

function push_assert_same(mixed $expected, mixed $actual, string $message): void {
    if ($expected !== $actual) {
        throw new RuntimeException($message . ' expected=' . var_export($expected, true) . ' actual=' . var_export($actual, true));
    }
}

function reset_push_db(): FakeWpdb {
    $db = new FakeWpdb();
    $GLOBALS['wpdb'] = $db;
    return $db;
}

function test_device_auth_and_dedup(): void {
    $db = reset_push_db();
    $GLOBALS['push_test_user_id'] = 42;
    $response = CriosRango_Push::register(new FakeRequest(
        ['platform' => 'android', 'token' => 'token-A'],
        ['authorization' => 'Bearer valid']
    ));
    push_assert(!is_wp_error($response), 'valid bearer must register');
    push_assert_same(42, $db->lastInsertData['user_id'], 'valid bearer must associate the real current user');

    $db->existingDeviceId = 9;
    CriosRango_Push::register(new FakeRequest(
        ['platform' => 'android', 'token' => 'token-A', 'new_products' => false, 'order_updates' => true],
        ['authorization' => 'Bearer valid']
    ));
    push_assert_same(42, $db->lastInsertData['user_id'], 'refresh must keep the authenticated user');

    $GLOBALS['push_test_user_id'] = 0;
    $anonymous = CriosRango_Push::register(new FakeRequest(['platform' => 'ios', 'token' => 'token-G']));
    push_assert(!is_wp_error($anonymous), 'anonymous device may register');
    push_assert_same(0, $db->lastInsertData['user_id'], 'anonymous device must have user_id=0');

    $invalid = CriosRango_Push::register(new FakeRequest(
        ['platform' => 'ios', 'token' => 'token-B'],
        ['authorization' => 'Bearer expired']
    ));
    push_assert(is_wp_error($invalid) && $invalid->data['status'] === 401, 'invalid bearer must not create a private association');
}

function test_product_event_detection(): void {
    $db = reset_push_db();
    $GLOBALS['push_test_user_id'] = 0;
    CriosRango_Push::product_publish('publish', 'draft', (object)['post_type' => 'product', 'ID' => 101]);
    push_assert(count($db->queries) === 1, 'first publication must create exactly one event');
    push_assert(str_contains($db->queries[0], 'product_published:101'), 'product event must be idempotent by product id');

    $db->queries = [];
    CriosRango_Push::product_publish('publish', 'publish', (object)['post_type' => 'product', 'ID' => 101]);
    push_assert(count($db->queries) === 0, 'editing an already published product must not create an event');
}

function test_daily_digest_and_idempotency(): void {
    $db = reset_push_db();
    $db->productEvents = [
        (object)['id' => 1, 'sent_gmt' => null],
        (object)['id' => 2, 'sent_gmt' => null],
    ];
    $db->devices = [
        (object)['id' => 10, 'active' => 1, 'new_products' => 1, 'order_updates' => 1, 'user_id' => 0],
        (object)['id' => 11, 'active' => 1, 'new_products' => 0, 'order_updates' => 1, 'user_id' => 0],
    ];
    $sent = 0;
    $sender = function($device, $payload) use (&$sent): array { $sent++; return ['result' => 'sent', 'provider_id' => 'test', 'invalid' => false]; };
    CriosRango_Push::digest($sender);
    push_assert_same(1, $sent, 'one digest must send once to each eligible device');
    push_assert(count(array_filter($db->queries, fn($q) => str_contains($q, 'digest:' . wp_date('Y-m-d')))) === 1, 'one digest event must be created');

    $db->digestRow = (object)['id' => 1, 'sent_gmt' => gmdate('Y-m-d H:i:s')];
    $before = $sent;
    CriosRango_Push::digest($sender);
    push_assert_same($before, $sent, 'a sent digest must never be sent twice');

    $db = reset_push_db();
    $db->productEvents = [];
    CriosRango_Push::digest($sender);
    push_assert_same(0, $sent - $before, 'zero products must not send anything');
}

function test_order_ownership_and_preferences(): void {
    $db = reset_push_db();
    $db->eventRow = (object)[
        'id' => 50,
        'entity_id' => 700,
        'payload' => wp_json_encode(['type' => 'order_status', 'order_id' => 700, 'user_id' => 999]),
    ];
    $db->devices = [
        (object)['id' => 1, 'active' => 1, 'new_products' => 1, 'order_updates' => 1, 'user_id' => 7],
        (object)['id' => 2, 'active' => 1, 'new_products' => 1, 'order_updates' => 1, 'user_id' => 8],
        (object)['id' => 3, 'active' => 1, 'new_products' => 1, 'order_updates' => 0, 'user_id' => 7],
        (object)['id' => 4, 'active' => 1, 'new_products' => 1, 'order_updates' => 1, 'user_id' => 0],
    ];
    $GLOBALS['push_test_order'] = new FakeOrder(7);
    $sentDevices = [];
    $sender = function($device, $payload) use (&$sentDevices): array {
        $sentDevices[] = $device->id;
        return ['result' => 'sent', 'provider_id' => 'test', 'invalid' => false];
    };
    CriosRango_Push::send_event(50, $sender);
    push_assert_same([1], $sentDevices, 'order push must target only the WooCommerce owner with order_updates enabled');
    push_assert(!in_array(2, $sentDevices, true), 'another user must never receive the order');
    push_assert(!in_array(3, $sentDevices, true), 'order_updates=false must block the order');
    push_assert(!in_array(4, $sentDevices, true), 'anonymous devices must never receive private orders');
}

function test_invalid_and_transient_tokens(): void {
    $db = reset_push_db();
    CriosRango_Push::delivery(20, 30, ['result' => 'failed', 'provider_id' => '404', 'invalid' => true]);
    push_assert(count(array_filter($db->updates, fn($u) => ($u[1]['active'] ?? null) === 0)) === 1, 'invalid provider token must be deactivated');

    $db = reset_push_db();
    CriosRango_Push::delivery(20, 30, ['result' => 'failed', 'provider_id' => '500', 'invalid' => false]);
    push_assert(count(array_filter($db->updates, fn($u) => isset($u[1]['active']))) === 0, 'transient provider error must not deactivate a valid token');
}

$tests = [
    'device auth and dedup' => 'test_device_auth_and_dedup',
    'product event detection' => 'test_product_event_detection',
    'daily digest and idempotency' => 'test_daily_digest_and_idempotency',
    'order ownership and preferences' => 'test_order_ownership_and_preferences',
    'invalid and transient tokens' => 'test_invalid_and_transient_tokens',
];

foreach ($tests as $name => $fn) {
    $fn();
    echo "PASS: {$name}\n";
}
echo "ALL PUSH PLUGIN CONTRACT TESTS PASSED\n";
