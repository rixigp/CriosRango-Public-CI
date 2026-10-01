<?php
/**
 * Plugin Name: Crios&Rango Push Notifications
 * Version: 1.0.0
 */
if (!defined('ABSPATH')) exit;

final class CriosRango_Push {
    const NS='criosrango/v1'; const GROUP='criosrango-push';
    static function init(){
        add_action('rest_api_init',[__CLASS__,'routes']);
        add_action('transition_post_status',[__CLASS__,'product_publish'],10,3);
        add_action('woocommerce_order_status_changed',[__CLASS__,'order_status'],10,4);
        add_action('criosrango_push_daily_digest',[__CLASS__,'digest']);
        add_action('action_scheduler_init',[__CLASS__,'ensure_schedule']);
    }
    static function table($n){global $wpdb;return $wpdb->prefix.'criosrango_push_'.$n;}
    static function activate(){
        global $wpdb; require_once ABSPATH.'wp-admin/includes/upgrade.php'; $c=$wpdb->get_charset_collate();
        dbDelta("CREATE TABLE ".self::table('devices')." (id bigint unsigned NOT NULL AUTO_INCREMENT, token_hash char(64) NOT NULL, token text NOT NULL, platform varchar(10) NOT NULL, user_id bigint unsigned NOT NULL DEFAULT 0, new_products tinyint(1) NOT NULL DEFAULT 1, order_updates tinyint(1) NOT NULL DEFAULT 1, active tinyint(1) NOT NULL DEFAULT 1, last_seen_gmt datetime NOT NULL, created_gmt datetime NOT NULL, updated_gmt datetime NOT NULL, PRIMARY KEY(id), UNIQUE KEY token_hash(token_hash), KEY user_id(user_id), KEY active(active)) $c;");
        dbDelta("CREATE TABLE ".self::table('events')." (id bigint unsigned NOT NULL AUTO_INCREMENT, idempotency_key varchar(190) NOT NULL, type varchar(40) NOT NULL, entity_id bigint unsigned NOT NULL DEFAULT 0, entity_state varchar(60) NOT NULL DEFAULT '', payload longtext NULL, created_gmt datetime NOT NULL, sent_gmt datetime NULL, PRIMARY KEY(id), UNIQUE KEY idempotency_key(idempotency_key), KEY type_sent(type,sent_gmt)) $c;");
        dbDelta("CREATE TABLE ".self::table('deliveries')." (id bigint unsigned NOT NULL AUTO_INCREMENT,event_id bigint unsigned NOT NULL,device_id bigint unsigned NOT NULL,result varchar(30) NOT NULL,provider_id varchar(255) NOT NULL DEFAULT '',created_gmt datetime NOT NULL,PRIMARY KEY(id),UNIQUE KEY event_device(event_id,device_id)) $c;");
        self::schedule();
    }
    static function schedule(){
        if(function_exists('as_has_scheduled_action') && function_exists('as_schedule_recurring_action') && !as_has_scheduled_action('criosrango_push_daily_digest',[],self::GROUP))
            as_schedule_recurring_action(time()+DAY_IN_SECONDS,DAY_IN_SECONDS,'criosrango_push_daily_digest',[],self::GROUP,true);
    }
    static function ensure_schedule(){self::schedule();}
    static function authenticated_user_id($r){
    $header = trim((string)$r->get_header('authorization'));

    if ($header === '') {
        return 0;
    }

    if (!preg_match('/^Bearer\s+.+$/i', $header)) {
        return new WP_Error(
            'push_invalid_auth',
            'Autenticación no válida',
            ['status' => 401]
        );
    }

    $me_request = new WP_REST_Request('GET', '/criosrango/v1/me');
    $me_request->set_header('Authorization', $header);
    $me_response = rest_do_request($me_request);

    if ($me_response->get_status() !== 200) {
        return new WP_Error(
            'push_auth_required',
            'No se ha podido autenticar la cuenta.',
            ['status' => 401]
        );
    }

    $data = $me_response->get_data();
    $user_id = absint($data['user']['id'] ?? 0);

    if ($user_id <= 0) {
        return new WP_Error(
            'push_auth_required',
            'No se ha podido autenticar la cuenta.',
            ['status' => 401]
        );
    }

    return $user_id;
    }
    static function routes(){
        register_rest_route(self::NS,'/push/device',[
            ['methods'=>WP_REST_Server::CREATABLE,'callback'=>[__CLASS__,'register'],'permission_callback'=>'__return_true'],
            ['methods'=>WP_REST_Server::DELETABLE,'callback'=>[__CLASS__,'unregister'],'permission_callback'=>'__return_true']
        ]);
    }
    static function register($r){
        global $wpdb; $platform=sanitize_key($r->get_param('platform')); $token=trim((string)$r->get_param('token'));
        if(!in_array($platform,['android','ios'],true)||$token==='') return new WP_Error('invalid_push_device','Datos no válidos',['status'=>400]);
        $user=self::authenticated_user_id($r); if(is_wp_error($user)) return $user;
        $hash=hash('sha256',$platform.':'.$token); $now=gmdate('Y-m-d H:i:s'); $table=self::table('devices');
        $data=['token_hash'=>$hash,'token'=>$token,'platform'=>$platform,'user_id'=>(int)$user,'new_products'=>$r->get_param('new_products')===null?1:(bool)$r->get_param('new_products'),'order_updates'=>$r->get_param('order_updates')===null?1:(bool)$r->get_param('order_updates'),'active'=>1,'last_seen_gmt'=>$now,'updated_gmt'=>$now];
        $id=$wpdb->get_var($wpdb->prepare("SELECT id FROM $table WHERE token_hash=%s",$hash));
        if($id)$wpdb->update($table,$data,['id'=>(int)$id]);else{$data['created_gmt']=$now;$wpdb->insert($table,$data);}
        return rest_ensure_response(['success'=>true]);
    }
    static function unregister($r){
        global $wpdb; $platform=sanitize_key($r->get_param('platform'));$token=trim((string)$r->get_param('token'));if(!in_array($platform,['android','ios'],true)||$token==='') return new WP_Error('invalid_push_device','Datos no válidos',['status'=>400]);$hash=hash('sha256',$platform.':'.$token);
        $wpdb->update(self::table('devices'),['active'=>0,'user_id'=>0,'updated_gmt'=>gmdate('Y-m-d H:i:s')],['token_hash'=>$hash]);
        return rest_ensure_response(['success'=>true]);
    }
    static function product_publish($new,$old,$post){
        if($post->post_type!=='product'||$new!=='publish'||$old==='publish'||get_post_meta($post->ID,'_criosrango_push_first_published_recorded',true))return;
        update_post_meta($post->ID,'_criosrango_push_first_published_recorded',gmdate('c'));
        self::event('product_published',(int)$post->ID,'publish',['product_id'=>(int)$post->ID]);
    }
    static function order_status($id,$old,$new,$order){
        if($old===$new||!in_array($new,['processing','completed'],true))return;
        $number=$order->get_order_number();$body=$new==='completed'?'Tu pedido #'.$number.' ha sido completado.':'Hemos recibido tu pedido #'.$number.' y ya está en preparación.';
        self::event('order_status',(int)$id,$new,['type'=>'order_status','order_id'=>(int)$id,'user_id'=>(int)$order->get_customer_id(),'title'=>$new==='completed'?'Pedido completado':'Pedido recibido','body'=>$body]);
    }
    static function event($type,$entity,$state,$payload){
        global $wpdb;$key=$type==='product_published'?'product_published:'.$entity:'order:'.$entity.':'.$state;
        $inserted=$wpdb->query($wpdb->prepare("INSERT IGNORE INTO ".self::table('events')." (idempotency_key,type,entity_id,entity_state,payload,created_gmt) VALUES(%s,%s,%d,%s,%s,%s)",$key,$type,$entity,$state,wp_json_encode($payload),gmdate('Y-m-d H:i:s')));
        if($type==='order_status' && $inserted)self::send_event((int)$wpdb->insert_id);
    }
    static function digest($sender=null){
        global $wpdb;$sender=$sender?:function($d,$p){return self::send($d,$p);};$key='digest:'.wp_date('Y-m-d');
        $existing_digest=$wpdb->get_row($wpdb->prepare("SELECT * FROM ".self::table('events')." WHERE idempotency_key=%s",$key));
        if($existing_digest && $existing_digest->sent_gmt)return;
        $events=$wpdb->get_results("SELECT * FROM ".self::table('events')." WHERE type='product_published' AND sent_gmt IS NULL ORDER BY created_gmt ASC");
        if(!$events)return;$wpdb->query($wpdb->prepare("INSERT IGNORE INTO ".self::table('events')." (idempotency_key,type,entity_state,payload,created_gmt) VALUES(%s,'digest','daily',%s,%s)",$key,wp_json_encode(['count'=>count($events)]),gmdate('Y-m-d H:i:s')));
        $id=(int)($existing_digest->id??$wpdb->insert_id);$devices=$wpdb->get_results("SELECT * FROM ".self::table('devices')." WHERE active=1 AND new_products=1");
        $all_ok=!empty($devices); foreach($devices as $d){
            $existing=$wpdb->get_var($wpdb->prepare("SELECT result FROM ".self::table('deliveries')." WHERE event_id=%d AND device_id=%d",$id,$d->id));
            if($existing==='sent')continue;
            $r=$sender($d,['type'=>'new_products','title'=>'¡Hay novedades! 🛍️','body'=>'Hoy hemos añadido '.count($events).' nuevos productos. Échales un vistazo.']);self::delivery($id,$d->id,$r);if(!$r['invalid']&&$r['result']!=='sent')$all_ok=false;
        }
        if($all_ok){$now=gmdate('Y-m-d H:i:s');$wpdb->update(self::table('events'),['sent_gmt'=>$now],['id'=>$id]);$ids=implode(',',array_map('intval',wp_list_pluck($events,'id')));$wpdb->query("UPDATE ".self::table('events')." SET sent_gmt='$now' WHERE id IN ($ids)");}
    }
    static function send_event($id,$sender=null){
        global $wpdb;$sender=$sender?:function($d,$p){return self::send($d,$p);};$e=$wpdb->get_row($wpdb->prepare("SELECT * FROM ".self::table('events')." WHERE id=%d",$id));if(!$e)return;
        $p=json_decode($e->payload,true);
        $order=function_exists('wc_get_order')?wc_get_order((int)$e->entity_id):null;
        $user=$order?(int)$order->get_customer_id():0;
        if($user<=0){return;}
        $devices=$wpdb->get_results($wpdb->prepare("SELECT * FROM ".self::table('devices')." WHERE active=1 AND order_updates=1 AND user_id=%d",$user));
        $all_ok=true; foreach($devices as $d){
            $existing=$wpdb->get_var($wpdb->prepare("SELECT result FROM ".self::table('deliveries')." WHERE event_id=%d AND device_id=%d",$e->id,$d->id));
            if($existing==='sent')continue;
            $r=$sender($d,$p);self::delivery($e->id,$d->id,$r);if(!$r['invalid']&&$r['result']!=='sent')$all_ok=false;
        }
        if($all_ok)$wpdb->update(self::table('events'),['sent_gmt'=>gmdate('Y-m-d H:i:s')],['id'=>$id]);
    }
    static function send($d,$p){
        if($d->platform==='android')return self::fcm($d->token,$p); return self::apns($d->token,$p);
    }
    static function delivery($eid,$did,$r){
        global $wpdb;$wpdb->query($wpdb->prepare("INSERT IGNORE INTO ".self::table('deliveries')." (event_id,device_id,result,provider_id,created_gmt) VALUES(%d,%d,%s,%s,%s)",$eid,$did,$r['result'],$r['provider_id'],gmdate('Y-m-d H:i:s')));
        if($r['invalid'])$wpdb->update(self::table('devices'),['active'=>0],['id'=>$did]);
    }
    static function fcm($token,$p){
        $cfg=defined('CRIOSRANGO_PUSH_FCM_SERVICE_ACCOUNT')?CRIOSRANGO_PUSH_FCM_SERVICE_ACCOUNT:get_option('criosrango_push_fcm_service_account');if(!$cfg)return['result'=>'config_missing','provider_id'=>'','invalid'=>false];
        $c=is_string($cfg)?json_decode($cfg,true):$cfg;if(empty($c['project_id'])||empty($c['client_email'])||empty($c['private_key']))return['result'=>'config_invalid','provider_id'=>'','invalid'=>false];
        $now=time();$b=function($v){return rtrim(strtr(base64_encode($v),'+/','-_'),'=');};$h=$b(wp_json_encode(['alg'=>'RS256','typ'=>'JWT']));$pl=$b(wp_json_encode(['iss'=>$c['client_email'],'scope'=>'https://www.googleapis.com/auth/firebase.messaging','aud'=>'https://oauth2.googleapis.com/token','iat'=>$now,'exp'=>$now+3600]));$sig='';openssl_sign("$h.$pl",$sig,$c['private_key'],OPENSSL_ALGO_SHA256);$jwt="$h.$pl.".$b($sig);
        $r=wp_remote_post('https://oauth2.googleapis.com/token',['body'=>['grant_type'=>'urn:ietf:params:oauth:grant-type:jwt-bearer','assertion'=>$jwt],'timeout'=>15]);if(is_wp_error($r))return['result'=>'oauth_error','provider_id'=>'','invalid'=>false];$access=json_decode(wp_remote_retrieve_body($r),true)['access_token']??'';if(!$access)return['result'=>'oauth_error','provider_id'=>'','invalid'=>false];
        $body=['message'=>['token'=>$token,'notification'=>['title'=>$p['title'],'body'=>$p['body']],'data'=>['type'=>$p['type'],'order_id'=>(string)($p['order_id']??'')],'android'=>['notification'=>['channel_id'=>$p['type']==='order_status'?'criosrango_orders':'criosrango_general']]]];
        $r=wp_remote_post('https://fcm.googleapis.com/v1/projects/'.rawurlencode($c['project_id']).'/messages:send',['headers'=>['Authorization'=>'Bearer '.$access,'Content-Type'=>'application/json'],'body'=>wp_json_encode($body),'timeout'=>15]);$code=is_wp_error($r)?0:wp_remote_retrieve_response_code($r);$raw=is_wp_error($r)?'':wp_remote_retrieve_body($r);return['result'=>$code>=200&&$code<300?'sent':'failed','provider_id'=>(string)$code,'invalid'=>$code===404||stripos($raw,'UNREGISTERED')!==false];
    }
    static function apns($token,$p){
        $key=defined('CRIOSRANGO_PUSH_APNS_KEY')?CRIOSRANGO_PUSH_APNS_KEY:get_option('criosrango_push_apns_key');
        $kid=defined('CRIOSRANGO_PUSH_APNS_KEY_ID')?CRIOSRANGO_PUSH_APNS_KEY_ID:get_option('criosrango_push_apns_key_id');
        $team=defined('CRIOSRANGO_PUSH_APNS_TEAM_ID')?CRIOSRANGO_PUSH_APNS_TEAM_ID:get_option('criosrango_push_apns_team_id');
        $bundle=defined('CRIOSRANGO_PUSH_APNS_BUNDLE_ID')?CRIOSRANGO_PUSH_APNS_BUNDLE_ID:(get_option('criosrango_push_apns_bundle_id')?:'es.criosrango.app');
        $env=defined('CRIOSRANGO_PUSH_APNS_ENV')?CRIOSRANGO_PUSH_APNS_ENV:(get_option('criosrango_push_apns_environment')?:'development');
        if(!$key||!$kid||!$team)return['result'=>'config_missing','provider_id'=>'','invalid'=>false];
        $b=function($v){return rtrim(strtr(base64_encode($v),'+/','-_'),'=');};
        $h=$b(wp_json_encode(['alg'=>'ES256','kid'=>$kid]));$pl=$b(wp_json_encode(['iss'=>$team,'iat'=>time()]));$der='';
        $private=str_replace("\\n","\n",$key);
        if(!openssl_sign("$h.$pl",$der,$private,OPENSSL_ALGO_SHA256))return['result'=>'jwt_error','provider_id'=>'','invalid'=>false];
        $pos=0;if(ord($der[$pos++])!==0x30)return['result'=>'jwt_error','provider_id'=>'','invalid'=>false];self::der_len($der,$pos);
        if(ord($der[$pos++])!==0x02)return['result'=>'jwt_error','provider_id'=>'','invalid'=>false];$rl=self::der_len($der,$pos);$rr=substr($der,$pos,$rl);$pos+=$rl;
        if(ord($der[$pos++])!==0x02)return['result'=>'jwt_error','provider_id'=>'','invalid'=>false];$sl=self::der_len($der,$pos);$ss=substr($der,$pos,$sl);
        $sig=str_pad(ltrim($rr,"\0"),32,"\0",STR_PAD_LEFT).str_pad(ltrim($ss,"\0"),32,"\0",STR_PAD_LEFT);
        $jwt="$h.$pl.".$b($sig);$host=$env==='production'?'https://api.push.apple.com':'https://api.sandbox.push.apple.com';
        $body=['aps'=>['alert'=>['title'=>$p['title'],'body'=>$p['body']],'sound'=>'default'],'type'=>$p['type'],'order_id'=>(string)($p['order_id']??'')];
        $r=wp_remote_post($host.'/3/device/'.rawurlencode($token),['httpversion'=>'2.0','headers'=>['authorization'=>'bearer '.$jwt,'apns-topic'=>$bundle,'apns-push-type'=>'alert','apns-priority'=>'10','Content-Type'=>'application/json'],'body'=>wp_json_encode($body),'timeout'=>15]);
        $code=is_wp_error($r)?0:wp_remote_retrieve_response_code($r);$raw=is_wp_error($r)?'':wp_remote_retrieve_body($r);
        return['result'=>$code>=200&&$code<300?'sent':'failed','provider_id'=>(string)$code,'invalid'=>in_array($code,[400,410],true)&& (stripos($raw,'BadDeviceToken')!==false||stripos($raw,'Unregistered')!==false)];
    }
    static function der_len($d,&$p){$l=ord($d[$p++]);if($l&0x80){$n=$l&0x7f;$l=0;for($i=0;$i<$n;$i++)$l=($l<<8)|ord($d[$p++]);}return $l;}
}
CriosRango_Push::init();register_activation_hook(__FILE__,['CriosRango_Push','activate']);
