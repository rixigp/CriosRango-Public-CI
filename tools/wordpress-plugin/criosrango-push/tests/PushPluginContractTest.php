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

final class WP_REST_Request {
    private array $headers = [];

    public function __construct(
        private string $method = 'GET',
        private string $route = ''
    ) {}

    public function set_header($key, $value): void {
        $this->headers[strtolower((string)$key)] = (string)$value;
    }

    public function get_header($key): string {
        return $this->headers[strtolower((string)$key)] ?? '';
    }
}

final class FakeRestResponse {
    public function __construct(
        private int $status,
        private array $data = []
    ) {}

    public function get_status(): int {
        return $this->status;
    }

    public function get_data(): array {
        return $this->data;
    }
}

function rest_do_request($request): FakeRestResponse {
    $authorization = trim((string)$request->get_header('authorization'));
    $user_id = (int)($GLOBALS['push_test_user_id'] ?? 0);

    if ($authorization === 'Bearer valid' && $user_id > 0) {
        return new FakeRestResponse(200, [
            'user' => ['id' => $user_id],
        ]);
    }

    return new FakeRestResponse(401, [
        'message' => 'Sesion no valida.',
    ]);
}

if (!function_exists('absint')) {
    function absint($value): int {
        return abs((int)$value);
    }
}

if (!function_exists('wp_json_encode')) {
    function wp_json_encode($value, $flags = 0, $depth = 512) {
        return json_encode($value, $flags, $depth);
    }
}

function add_action($hook, $callback, ...$args): void {
    $GLOBALS['push_test_hooks'][$hook][] = $callback;
}
function register_activation_hook($file, $callback): void {
    $GLOBALS['push_test_activation_callbacks'][] = $callback;
}
if (!defined('DAY_IN_SECONDS')) define('DAY_IN_SECONDS', 86400);
function as_has_scheduled_action($hook, $args = [], $group = ''): bool {
    foreach ($GLOBALS['push_test_scheduled_actions'] as $action) {
        if ($action['hook'] === $hook && $action['args'] === $args && $action['group'] === $group) return true;
    }
    return false;
}
function as_schedule_recurring_action($timestamp, $interval, $hook, $args = [], $group = '', $unique = false): int {
    $id = count($GLOBALS['push_test_scheduled_actions']) + 1;
    $GLOBALS['push_test_scheduled_actions'][] = [
        'id' => $id, 'hook' => $hook, 'timestamp' => $timestamp,
        'interval' => $interval, 'args' => $args, 'group' => $group, 'unique' => $unique,
    ];
    return $id;
}
function dbDelta(...$args): void {}
function is_wp_error(mixed $value): bool { return $value instanceof WP_Error; }
function rest_ensure_response(mixed $value): mixed { return $value; }
function sanitize_key($value): string { return preg_replace('/[^a-z0-9_\-]/', '', strtolower((string)$value)); }
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
    public function get_charset_collate(): string { return 'DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'; }
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
        if (str_starts_with(trim($query), 'INSERT INTO') && str_contains($query, 'criosrango_push_deliveries') && str_contains($query, 'ON DUPLICATE KEY UPDATE')) {
            preg_match("/VALUES\(([0-9]+),([0-9]+),'([^']+)',('?[^,)]*'?),('(?:[^']*)')\)/", $query, $m);
            $key = ((int)($m[1] ?? 0)) . ':' . ((int)($m[2] ?? 0));
            $this->deliveries[$key] = $m[3] ?? '';
            $this->insertedDeliveries[$key] = ['result' => $m[3] ?? '', 'provider_id' => $m[4] ?? '', 'created_gmt' => $m[5] ?? ''];
            return 1;
        }
        if (str_starts_with(trim($query), 'INSERT IGNORE')) {
            if (str_contains($query, "'digest'") && preg_match("/'digest:([^']+)'/", $query, $m)) {
                $idempotencyKey = 'digest:' . $m[1];
                if ($this->digestRow && $this->digestRow->idempotency_key === $idempotencyKey) {
                    return 0;
                }
                $this->insert_id++;
                $this->digestRow = (object)[
                    'id' => $this->insert_id,
                    'idempotency_key' => $idempotencyKey,
                    'sent_gmt' => null,
                ];
                return 1;
            }
            $this->insert_id++;
        }
        return 1;
    }

    public function update(string $table, array $data, array $where): int {
        $this->updates[] = [$table, $data, $where];
        if (str_contains($table, 'criosrango_push_events') && $this->digestRow && (int)($where['id'] ?? 0) === (int)$this->digestRow->id && array_key_exists('sent_gmt', $data)) {
            $this->digestRow->sent_gmt = $data['sent_gmt'];
        }
        return 1;
    }

    public function insert(string $table, array $data): int {
        $this->lastInsertData = $data;
        $this->insert_id++;
        return 1;
    }
}

$GLOBALS['wpdb'] = new FakeWpdb();
$GLOBALS['push_test_user_id'] = 0;
$GLOBALS['push_test_order'] = new FakeOrder(0);
$GLOBALS['push_test_hooks'] = [];
$GLOBALS['push_test_activation_callbacks'] = [];
$GLOBALS['push_test_scheduled_actions'] = [];
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

function reset_push_harness(): FakeWpdb {
    $db = new FakeWpdb();
    $GLOBALS['wpdb'] = $db;
    $GLOBALS['push_test_user_id'] = 0;
    $GLOBALS['push_test_order'] = new FakeOrder(0);
    return $db;
}

function test_action_scheduler(): void {
    $db = reset_push_harness();
    $GLOBALS['push_test_scheduled_actions'] = [];

    // The real activation callback includes WordPress's upgrade helper; provide only
    // the empty file needed for the harness's existing dbDelta stub.
    @mkdir('/tmp/wp-admin/includes', 0777, true);
    if (!is_file('/tmp/wp-admin/includes/upgrade.php')) {
        file_put_contents('/tmp/wp-admin/includes/upgrade.php', "<?php\n");
    }
    $activation = $GLOBALS['push_test_activation_callbacks'][0] ?? null;
    push_assert(is_callable($activation), 'plugin activation callback must be registered');
    $activation();

    push_assert_same(1, count($GLOBALS['push_test_scheduled_actions']), 'activation without a prior job must schedule exactly one digest');
    $scheduled = $GLOBALS['push_test_scheduled_actions'][0];
    push_assert_same('criosrango_push_daily_digest', $scheduled['hook'], 'scheduled hook must match the digest callback hook');
    push_assert_same(CriosRango_Push::GROUP, $scheduled['group'], 'scheduled action must use the plugin group');
    push_assert_same(DAY_IN_SECONDS, $scheduled['interval'], 'digest recurrence must be daily');
    push_assert($scheduled['timestamp'] >= time() + DAY_IN_SECONDS - 2 && $scheduled['timestamp'] <= time() + DAY_IN_SECONDS + 2, 'first digest must be scheduled about one day ahead');
    push_assert_same(true, $scheduled['unique'], 'scheduler request must ask for a unique recurring action');

    // Repeated activation and the action_scheduler_init callback must not duplicate it.
    $activation();
    CriosRango_Push::ensure_schedule();
    push_assert_same(1, count($GLOBALS['push_test_scheduled_actions']), 'repeated activation/registration must not create a duplicate');

    // A pre-existing matching action must also suppress scheduling.
    $GLOBALS['push_test_scheduled_actions'] = [[
        'id' => 99, 'hook' => 'criosrango_push_daily_digest', 'timestamp' => time() + DAY_IN_SECONDS,
        'interval' => DAY_IN_SECONDS, 'args' => [], 'group' => CriosRango_Push::GROUP, 'unique' => true,
    ]];
    CriosRango_Push::ensure_schedule();
    push_assert_same(1, count($GLOBALS['push_test_scheduled_actions']), 'an existing digest job must not be rescheduled');

    $callbacks = $GLOBALS['push_test_hooks']['criosrango_push_daily_digest'] ?? [];
    push_assert_same([['CriosRango_Push', 'digest']], $callbacks, 'scheduled hook must be registered to the real digest callback');

    // Invoke the callback captured from the plugin's actual add_action registration.
    $db->productEvents = [(object)['id' => 1, 'sent_gmt' => null]];
    $db->devices = [(object)['id' => 10, 'active' => 1, 'new_products' => 1, 'order_updates' => 1, 'user_id' => 0]];
    $sent = 0;
    $sender = function($device, $payload) use (&$sent): array {
        $sent++;
        return ['result' => 'sent', 'provider_id' => 'scheduler-test', 'invalid' => false];
    };
    $callback = $callbacks[0];
    call_user_func($callback, $sender);
    push_assert_same(1, $sent, 'scheduler callback must run the real digest and send once');
    push_assert($db->digestRow !== null && $db->digestRow->sent_gmt !== null, 'successful callback must persist the digest:<date> sent marker');

    call_user_func($callback, $sender);
    push_assert_same(1, $sent, 'running the callback twice must not send the same daily digest twice');
}

function reset_push_request_auth(): void {
    $GLOBALS['push_test_user_id'] = 0;
}

function test_device_auth_and_dedup(): void {
    $db = reset_push_harness();
    reset_push_request_auth();
    $GLOBALS['push_test_user_id'] = 42;
    $response = CriosRango_Push::register(new FakeRequest(
        ['platform' => 'android', 'token' => 'token-A'],
        ['authorization' => 'Bearer valid']
    ));
    push_assert(!is_wp_error($response), 'valid bearer must register');
    push_assert_same(42, $db->lastInsertData['user_id'], 'valid bearer must associate the real current user');

    $db->existingDeviceId = 9;
    reset_push_request_auth();
    $GLOBALS['push_test_user_id'] = 42;
    CriosRango_Push::register(new FakeRequest(
        ['platform' => 'android', 'token' => 'token-A', 'new_products' => false, 'order_updates' => true],
        ['authorization' => 'Bearer valid']
    ));
    push_assert_same(42, $db->lastInsertData['user_id'], 'refresh must keep the authenticated user');

    $db->existingDeviceId = null;
    reset_push_request_auth();
    $anonymous = CriosRango_Push::register(new FakeRequest(['platform' => 'ios', 'token' => 'token-G']));
    push_assert(!is_wp_error($anonymous), 'anonymous device may register');
    push_assert_same(0, $db->lastInsertData['user_id'], 'anonymous device must have user_id=0');

    reset_push_request_auth();
    $invalid = CriosRango_Push::register(new FakeRequest(
        ['platform' => 'ios', 'token' => 'token-B'],
        ['authorization' => 'Bearer expired']
    ));
    push_assert(is_wp_error($invalid) && $invalid->data['status'] === 401, 'invalid bearer must not create a private association');
}

function test_product_event_detection(): void {
    $db = reset_push_harness();
    CriosRango_Push::product_publish('publish', 'draft', (object)['post_type' => 'product', 'ID' => 101]);
    push_assert(count($db->queries) === 1, 'first publication must create exactly one event');
    push_assert(str_contains($db->queries[0], 'product_published:101'), 'product event must be idempotent by product id');

    $db->queries = [];
    CriosRango_Push::product_publish('publish', 'publish', (object)['post_type' => 'product', 'ID' => 101]);
    push_assert(count($db->queries) === 0, 'editing an already published product must not create an event');
}

function test_daily_digest_and_idempotency(): void {
    $db = reset_push_harness();
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

    $db = reset_push_harness();
    $db->productEvents = [];
    CriosRango_Push::digest($sender);
    push_assert_same(0, $sent - $before, 'zero products must not send anything');
}

function test_digest_zero_devices_stays_pending(): void {
    $db = reset_push_harness();
    $db->productEvents = [(object)['id' => 1, 'sent_gmt' => null]];
    $db->devices = [];

    $sent = 0;
    $sender = function($device, $payload) use (&$sent): array {
        $sent++;
        return ['result' => 'sent', 'provider_id' => 'zero-device-test', 'invalid' => false];
    };

    CriosRango_Push::digest($sender);

    push_assert_same(0, $sent, 'zero eligible devices must not attempt delivery');
    push_assert($db->digestRow !== null && $db->digestRow->sent_gmt === null, 'zero eligible devices must leave the daily digest unresolved');
    push_assert(count(array_filter($db->queries, fn($q) => str_contains($q, 'SET sent_gmt='))) === 0, 'zero eligible devices must not mark product events as sent');
}

function test_digest_transient_retry(): void {
    $db = reset_push_harness();
    $db->productEvents = [(object)['id' => 1, 'sent_gmt' => null]];
    $db->devices = [(object)['id' => 10, 'active' => 1, 'new_products' => 1, 'order_updates' => 1, 'user_id' => 0]];

    $attempts = 0;
    $sender = function($device, $payload) use (&$attempts): array {
        $attempts++;
        if ($attempts === 1) {
            return ['result' => 'failed', 'provider_id' => '500', 'invalid' => false];
        }
        return ['result' => 'sent', 'provider_id' => '200', 'invalid' => false];
    };

    CriosRango_Push::digest($sender);
    push_assert_same(1, $attempts, 'first digest execution must attempt the eligible device');
    push_assert($db->digestRow !== null && $db->digestRow->sent_gmt === null, 'transient failure must leave the digest unresolved');
    push_assert(count(array_filter($db->updates, fn($u) => isset($u[1]['active']))) === 0, 'transient failure must not deactivate the token');
    push_assert(count(array_filter($db->queries, fn($q) => str_contains($q, 'SET sent_gmt='))) === 0, 'transient failure must leave the product pending');

    CriosRango_Push::digest($sender);
    push_assert_same(2, $attempts, 'an unresolved digest must retry on a later execution');
    push_assert($db->digestRow->sent_gmt !== null, 'successful retry must resolve the daily digest');
    push_assert(count(array_filter($db->queries, fn($q) => str_contains($q, 'SET sent_gmt='))) === 1, 'successful retry must mark the product as sent');
}

function test_digest_staggered_retry_persists_success(): void {
    $db = reset_push_harness();
    $db->productEvents = [(object)['id' => 1, 'sent_gmt' => null]];
    $db->devices = [
        (object)['id' => 10, 'active' => 1, 'new_products' => 1, 'order_updates' => 1, 'user_id' => 0],
        (object)['id' => 11, 'active' => 1, 'new_products' => 1, 'order_updates' => 1, 'user_id' => 0],
    ];

    $attempts = [10 => 0, 11 => 0];
    $execution = 0;
    $sender = function($device, $payload) use (&$attempts, &$execution): array {
        $id = (int)$device->id;
        $attempts[$id]++;
        if ($execution === 1) {
            return ['result' => 'failed', 'provider_id' => '500', 'invalid' => false];
        }
        if ($execution === 2 && $id === 10) {
            return ['result' => 'sent', 'provider_id' => '200', 'invalid' => false];
        }
        if ($execution === 3 && $id === 11) {
            return ['result' => 'sent', 'provider_id' => '200', 'invalid' => false];
        }
        return ['result' => 'failed', 'provider_id' => '500', 'invalid' => false];
    };

    $execution = 1;
    CriosRango_Push::digest($sender);
    push_assert_same(1, $attempts[10], 'first execution must attempt device 10 once');
    push_assert_same(1, $attempts[11], 'first execution must attempt device 11 once');
    push_assert($db->digestRow !== null && $db->digestRow->sent_gmt === null, 'first execution must leave digest pending');

    $execution = 2;
    CriosRango_Push::digest($sender);
    push_assert_same(2, $attempts[10], 'second execution must retry device 10');
    push_assert_same(2, $attempts[11], 'second execution must retry device 11');
    push_assert_same('sent', $db->deliveries['1:10'] ?? null, 'device 10 successful retry must persist as sent');
    push_assert($db->digestRow !== null && $db->digestRow->sent_gmt === null, 'second execution must leave digest pending');

    $execution = 3;
    CriosRango_Push::digest($sender);
    push_assert_same(2, $attempts[10], 'third execution must skip resolved device 10');
    push_assert_same(3, $attempts[11], 'third execution must retry only device 11');
    push_assert($db->digestRow !== null && $db->digestRow->sent_gmt !== null, 'third execution must resolve the digest');
    push_assert_same('sent', $db->deliveries['1:11'] ?? null, 'device 11 final retry must persist as sent');
    push_assert(count(array_filter($db->queries, fn($q) => str_contains($q, 'SET sent_gmt='))) === 1, 'successful final retry must mark products as sent');
}

function test_digest_invalid_token_does_not_block_valid(): void {
    $db = reset_push_harness();
    $db->productEvents = [(object)['id' => 1, 'sent_gmt' => null]];
    $db->devices = [
        (object)['id' => 10, 'active' => 1, 'new_products' => 1, 'order_updates' => 1, 'user_id' => 0],
        (object)['id' => 11, 'active' => 1, 'new_products' => 1, 'order_updates' => 1, 'user_id' => 0],
    ];

    $sentDevices = [];
    $sender = function($device, $payload) use (&$sentDevices): array {
        $sentDevices[] = $device->id;
        if ((int)$device->id === 10) {
            return ['result' => 'failed', 'provider_id' => '404', 'invalid' => true];
        }
        return ['result' => 'sent', 'provider_id' => '200', 'invalid' => false];
    };

    CriosRango_Push::digest($sender);

    push_assert_same([10, 11], $sentDevices, 'an invalid token must not prevent other eligible recipients from being processed');
    push_assert(count(array_filter($db->updates, fn($u) => ($u[1]['active'] ?? null) === 0)) === 1, 'invalid token must be deactivated');
    push_assert($db->digestRow !== null && $db->digestRow->sent_gmt !== null, 'a digest resolved by the remaining valid recipient must be completed');
}

function test_digest_completed_is_idempotent(): void {
    $db = reset_push_harness();
    $db->productEvents = [(object)['id' => 1, 'sent_gmt' => null]];
    $db->devices = [(object)['id' => 10, 'active' => 1, 'new_products' => 1, 'order_updates' => 1, 'user_id' => 0]];

    $sent = 0;
    $sender = function($device, $payload) use (&$sent): array {
        $sent++;
        return ['result' => 'sent', 'provider_id' => '200', 'invalid' => false];
    };

    CriosRango_Push::digest($sender);
    push_assert_same(1, $sent, 'completed digest must send once');

    CriosRango_Push::digest($sender);
    push_assert_same(1, $sent, 'completed digest must not be resent on a later execution');
    push_assert($db->digestRow !== null && $db->digestRow->sent_gmt !== null, 'completed digest must remain resolved');
}

function test_order_ownership_and_preferences(): void {
    $db = reset_push_harness();
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
    $db = reset_push_harness();
    CriosRango_Push::delivery(20, 30, ['result' => 'failed', 'provider_id' => '404', 'invalid' => true]);
    push_assert(count(array_filter($db->updates, fn($u) => ($u[1]['active'] ?? null) === 0)) === 1, 'invalid provider token must be deactivated');

    $db = reset_push_harness();
    CriosRango_Push::delivery(20, 30, ['result' => 'failed', 'provider_id' => '500', 'invalid' => false]);
    push_assert(count(array_filter($db->updates, fn($u) => isset($u[1]['active']))) === 0, 'transient provider error must not deactivate a valid token');
}

$tests = [
    'device auth and dedup' => 'test_device_auth_and_dedup',
    'product event detection' => 'test_product_event_detection',
    'daily digest and idempotency' => 'test_daily_digest_and_idempotency',
    'digest zero devices stays pending' => 'test_digest_zero_devices_stays_pending',
    'digest transient retry' => 'test_digest_transient_retry',
    'digest staggered retry persists success' => 'test_digest_staggered_retry_persists_success',
    'digest invalid token does not block valid' => 'test_digest_invalid_token_does_not_block_valid',
    'digest completed is idempotent' => 'test_digest_completed_is_idempotent',
    'order ownership and preferences' => 'test_order_ownership_and_preferences',
    'invalid and transient tokens' => 'test_invalid_and_transient_tokens',
    'action scheduler' => 'test_action_scheduler',
];

foreach ($tests as $name => $fn) {
    $fn();
    echo "PASS: {$name}\n";
}
echo "ALL PUSH PLUGIN CONTRACT TESTS PASSED\n";
