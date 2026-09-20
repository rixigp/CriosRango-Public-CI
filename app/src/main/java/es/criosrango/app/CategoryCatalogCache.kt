package es.criosrango.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query as RetrofitQuery
import java.util.concurrent.TimeUnit

data class CategoryNetworkTelemetry(val dnsMs:Long?=null,val connectMs:Long?=null,val tlsMs:Long?=null,val requestHeadersMs:Long?=null,val requestBodyMs:Long?=null,val ttfbMs:Long?=null,val responseHeadersMs:Long?=null,val bodyDownloadMs:Long?=null,val totalNetworkMs:Long?=null,val connectionReused:Boolean?=null,val protocol:String?=null,val requestBodyBytes:Long?=null,val responseBodyBytes:Long?=null)
data class CategoryTelemetrySnapshot(val categoryId:Int,val source:String?=null,val products:Int?=null,val tapMs:Long?=null,val cacheStartMs:Long?=null,val cacheEndMs:Long?=null,val cacheHit:String?=null,val networkStartMs:Long?=null,val networkEndMs:Long?=null,val parseEndMs:Long?=null,val roomWriteEndMs:Long?=null,val uiProductsMs:Long?=null,val firstImageMs:Long?=null,val firstImageProductId:Int?=null,val network:CategoryNetworkTelemetry?=null)
internal object CategoryLoadTelemetry {
 private val snapshots=mutableMapOf<Int,CategoryTelemetrySnapshot>(); private val starts=mutableMapOf<Int,Long>(); private val firstImageLogged=mutableSetOf<Int>(); private val lock=Any()
 fun tap(id:Int){synchronized(lock){starts[id]=android.os.SystemClock.elapsedRealtime();firstImageLogged.remove(id);snapshots[id]=CategoryTelemetrySnapshot(id,tapMs=0L)};log(id,"CATEGORY_TAP")}
 fun cacheStart(id:Int){update(id){it.copy(cacheStartMs=elapsed(id))};log(id,"CATEGORY_CACHE_START")}
 fun cacheEnd(id:Int,source:String,p:Int){update(id){it.copy(source=source,products=p,cacheEndMs=elapsed(id))};log(id,"CATEGORY_CACHE_END source=$source products=$p")}
 fun cacheHit(id:Int,source:String,p:Int){update(id){it.copy(source=source,products=p,cacheHit=source)};log(id,"CATEGORY_CACHE_HIT source=$source products=$p")}
 fun cacheMiss(id:Int){update(id){it.copy(source="network",cacheHit="MISS")};log(id,"CATEGORY_CACHE_MISS")}
 fun networkStart(id:Int){update(id){it.copy(networkStartMs=elapsed(id))};log(id,"CATEGORY_NETWORK_START")}
 fun networkEnd(id:Int,p:Int){update(id){it.copy(products=p,networkEndMs=elapsed(id))};log(id,"CATEGORY_NETWORK_END products=$p")}
 fun parseEnd(id:Int,p:Int){update(id){it.copy(products=p,parseEndMs=elapsed(id))};log(id,"CATEGORY_PARSE_END products=$p")}
 fun roomWriteEnd(id:Int,p:Int){update(id){it.copy(products=p,roomWriteEndMs=elapsed(id))};log(id,"CATEGORY_ROOM_WRITE_END products=$p")}
 fun uiProducts(id:Int,p:Int,source:String){update(id){it.copy(source=source,products=p,uiProductsMs=elapsed(id))};log(id,"CATEGORY_UI_PRODUCTS source=$source products=$p")}
 fun network(id:Int,n:CategoryNetworkTelemetry){update(id){it.copy(network=n)}}
 fun firstImage(id:Int,pid:Int){synchronized(lock){if(!firstImageLogged.add(id))return;val cur=snapshots[id]?:CategoryTelemetrySnapshot(id);snapshots[id]=cur.copy(firstImageMs=elapsed(id),firstImageProductId=pid)};log(id,"CATEGORY_FIRST_IMAGE productId=$pid")}
 fun snapshot(id:Int)=synchronized(lock){snapshots[id]}
 private fun elapsed(id:Int):Long?{val s=synchronized(lock){starts[id]}?:return null;return android.os.SystemClock.elapsedRealtime()-s}
 private fun update(id:Int,f:(CategoryTelemetrySnapshot)->CategoryTelemetrySnapshot){synchronized(lock){snapshots[id]=f(snapshots[id]?:CategoryTelemetrySnapshot(id))}}
 private fun log(id:Int,e:String){android.util.Log.d("CategoryTiming",e+" id="+id+" t="+(elapsed(id)?:-1)+"ms")}
}
internal class CategoryNetworkEventListener:okhttp3.EventListener(){
 private var id:Int?=null;private var call=0L;private var dnsS:Long?=null;private var dnsE:Long?=null;private var conS:Long?=null;private var conE:Long?=null;private var tlsS:Long?=null;private var tlsE:Long?=null;private var rhS:Long?=null;private var rhE:Long?=null;private var rbS:Long?=null;private var rbE:Long?=null;private var shS:Long?=null;private var shE:Long?=null;private var bodyS:Long?=null;private var reused=true;private var proto:okhttp3.Protocol?=null
 override fun callStart(c:okhttp3.Call){val u=c.request().url;if(u.encodedPath.endsWith("/products")){id=u.queryParameter("category")?.toIntOrNull();if(id!=null)call=System.nanoTime()}}
 override fun dnsStart(c:okhttp3.Call,d:String){if(id!=null)dnsS=System.nanoTime()}
 override fun dnsEnd(c:okhttp3.Call,d:String,a:List<java.net.InetAddress>){if(id!=null)dnsE=System.nanoTime()}
 override fun connectStart(c:okhttp3.Call,a:java.net.InetSocketAddress,p:java.net.Proxy){if(id!=null){conS=System.nanoTime();reused=false}}
 override fun connectEnd(c:okhttp3.Call,a:java.net.InetSocketAddress,callbackProxy:java.net.Proxy,callbackProtocol:okhttp3.Protocol?){if(id!=null){conE=System.nanoTime();proto=callbackProtocol}}
 override fun secureConnectStart(c:okhttp3.Call){if(id!=null)tlsS=System.nanoTime()}
 override fun secureConnectEnd(c:okhttp3.Call,h:okhttp3.Handshake?){if(id!=null)tlsE=System.nanoTime()}
 override fun connectionAcquired(c:okhttp3.Call,con:okhttp3.Connection){if(id!=null)proto=con.protocol()}
 override fun requestHeadersStart(c:okhttp3.Call){if(id!=null)rhS=System.nanoTime()}
 override fun requestHeadersEnd(c:okhttp3.Call,r:okhttp3.Request){if(id!=null)rhE=System.nanoTime()}
 override fun requestBodyStart(c:okhttp3.Call){if(id!=null)rbS=System.nanoTime()}
 override fun requestBodyEnd(c:okhttp3.Call,n:Long){if(id!=null)rbE=System.nanoTime()}
 override fun responseHeadersStart(c:okhttp3.Call){if(id!=null)shS=System.nanoTime()}
 override fun responseHeadersEnd(c:okhttp3.Call,r:okhttp3.Response){if(id!=null)shE=System.nanoTime()}
 override fun responseBodyStart(c:okhttp3.Call){if(id!=null)bodyS=System.nanoTime()}
 override fun responseBodyEnd(c:okhttp3.Call,n:Long){val x=id?:return;val end=System.nanoTime();val ms:(Long?,Long?)->Long?={a,b->if(a!=null&&b!=null)(b-a)/1000000L else null};CategoryLoadTelemetry.network(x,CategoryNetworkTelemetry(ms(dnsS,dnsE),ms(conS,conE),ms(tlsS,tlsE),ms(rhS,rhE),ms(rbS,rbE),ms(rhE,shS),ms(shS,shE),ms(bodyS,end),(end-call)/1000000L,reused,proto?.toString(),null,n))}
}

private const val CATALOG_TTL_MS = 15L * 60L * 1000L
private const val CATALOG_PAGE_SIZE = 100
private const val CATALOG_MAX_CONCURRENCY = 1

@Entity(tableName = "catalog_products", primaryKeys = ["generation", "productId"], indices = [Index(value = ["generation", "productId"])])
data class CatalogProductEntity(val generation: Long, val productId: Int, val payloadJson: String, val catalogOrder: Int)

@Entity(tableName = "catalog_product_categories", primaryKeys = ["generation", "productId", "categoryId"], indices = [Index(value = ["generation", "categoryId"]), Index(value = ["generation", "productId"])])
data class CatalogProductCategoryEntity(val generation: Long, val productId: Int, val categoryId: Int)

@Entity(tableName = "catalog_categories", indices = [Index(value = ["generation", "parentId"])])
data class CatalogCategoryEntity(@PrimaryKey val key: String, val generation: Long, val categoryId: Int, val parentId: Int, val name: String, val slug: String, val count: Int)

@Entity(tableName = "catalog_sync_metadata")
data class CatalogSyncMetadataEntity(@PrimaryKey val id: Int = 1, val activeGeneration: Long? = null, val lastCompleteSyncAt: Long? = null, val hasValidSnapshot: Boolean = false)

@Entity(tableName = "catalog_priority_fallback", primaryKeys = ["categoryId", "productId"], indices = [Index(value = ["categoryId"])])
data class CatalogPriorityFallbackEntity(val categoryId: Int, val productId: Int, val payloadJson: String, val catalogOrder: Int, val fetchedAt: Long)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_sync_metadata WHERE id = 1 LIMIT 1") suspend fun getMetadata(): CatalogSyncMetadataEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMetadata(metadata: CatalogSyncMetadataEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProducts(products: List<CatalogProductEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRelations(relations: List<CatalogProductCategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(categories: List<CatalogCategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPriorityFallback(products: List<CatalogPriorityFallbackEntity>)
    @Query("DELETE FROM catalog_products WHERE generation = :generation") suspend fun deleteProductsForGeneration(generation: Long)
    @Query("DELETE FROM catalog_product_categories WHERE generation = :generation") suspend fun deleteRelationsForGeneration(generation: Long)
    @Query("DELETE FROM catalog_categories WHERE generation = :generation") suspend fun deleteCategoriesForGeneration(generation: Long)
    @Query("DELETE FROM catalog_products WHERE generation != :generation") suspend fun deleteOldProducts(generation: Long)
    @Query("DELETE FROM catalog_product_categories WHERE generation != :generation") suspend fun deleteOldRelations(generation: Long)
    @Query("DELETE FROM catalog_categories WHERE generation != :generation") suspend fun deleteOldCategories(generation: Long)
    @Query("DELETE FROM catalog_priority_fallback") suspend fun clearPriorityFallback()
    @Query("SELECT * FROM catalog_categories WHERE generation = (SELECT activeGeneration FROM catalog_sync_metadata WHERE id = 1)") suspend fun getActiveCategories(): List<CatalogCategoryEntity>
    @Query("SELECT * FROM catalog_categories WHERE generation = (SELECT activeGeneration FROM catalog_sync_metadata WHERE id = 1) AND parentId = 0 AND count > 0 ORDER BY categoryId ASC") suspend fun getActiveRootCategories(): List<CatalogCategoryEntity>
    @Query("SELECT * FROM catalog_products WHERE generation = (SELECT activeGeneration FROM catalog_sync_metadata WHERE id = 1) ORDER BY catalogOrder ASC LIMIT :limit") suspend fun getHomeProducts(limit: Int): List<CatalogProductEntity>
    @Query("SELECT * FROM catalog_products p WHERE p.generation = (SELECT activeGeneration FROM catalog_sync_metadata WHERE id = 1) AND p.productId IN (SELECT r.productId FROM catalog_product_categories r WHERE r.generation = p.generation AND r.categoryId IN (:categoryIds)) GROUP BY p.productId ORDER BY p.catalogOrder ASC") suspend fun getProductsForCategories(categoryIds: List<Int>): List<CatalogProductEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM catalog_categories WHERE generation = (SELECT activeGeneration FROM catalog_sync_metadata WHERE id = 1) AND categoryId = :categoryId)") suspend fun hasActiveCategory(categoryId: Int): Boolean
    @Query("SELECT * FROM catalog_priority_fallback WHERE categoryId = :categoryId ORDER BY catalogOrder ASC") suspend fun getPriorityFallback(categoryId: Int): List<CatalogPriorityFallbackEntity>
}

@Database(entities = [CatalogProductEntity::class, CatalogProductCategoryEntity::class, CatalogCategoryEntity::class, CatalogSyncMetadataEntity::class, CatalogPriorityFallbackEntity::class], version = 4, exportSchema = false)
abstract class CategoryProductCacheDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS catalog_priority_fallback (categoryId INTEGER NOT NULL, productId INTEGER NOT NULL, payloadJson TEXT NOT NULL, catalogOrder INTEGER NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(categoryId, productId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_catalog_priority_fallback_categoryId ON catalog_priority_fallback(categoryId)")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The cached JSON payload now carries Outlet origin metadata. Invalidate the
                // previous snapshot so stale Room rows can never hide originalCategoryIds.
                db.execSQL("UPDATE catalog_sync_metadata SET activeGeneration = NULL, lastCompleteSyncAt = NULL, hasValidSnapshot = 0 WHERE id = 1")
                db.execSQL("DELETE FROM catalog_priority_fallback")
            }
        }
        fun create(context: Context): CategoryProductCacheDatabase = Room.databaseBuilder(context.applicationContext, CategoryProductCacheDatabase::class.java, "criosrango_catalog.db").addMigrations(MIGRATION_2_3, MIGRATION_3_4).fallbackToDestructiveMigration(false).build()
    }
}

data class CategoryCacheUpdate(val categoryId: Int, val products: List<StoreProduct>)

private interface GlobalCatalogApi {
    @GET("products")
    suspend fun productsPage(@RetrofitQuery("per_page") perPage: Int = CATALOG_PAGE_SIZE, @RetrofitQuery("page") page: Int = 1): Response<List<StoreProduct>>
}

class CategoryCatalogCache(private val database: CategoryProductCacheDatabase) {
    private val gson = Gson()
    private val dao = database.catalogDao()
    private val memory = mutableMapOf<Int, List<StoreProduct>>()
    private val memoryMutex = Mutex()
    private val syncMutex = Mutex()
    private val priorityMutex = Mutex()
    private val priorityInFlight = mutableMapOf<Int, CompletableDeferred<List<StoreProduct>>>()
    private val networkGate = Mutex()
    private val priorityNetworkMutex = Mutex()
    private var priorityWaiters = 0
    private val priorityWaitersMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncInFlight: CompletableDeferred<Boolean>? = null
    private var generationInMemory: Long? = null
    private val emittedGenerationByCategory = mutableMapOf<Int, Long>()
    private val emittedMutex = Mutex()
    private val _updates = MutableSharedFlow<CategoryCacheUpdate>(extraBufferCapacity = 64)
    val updates: SharedFlow<CategoryCacheUpdate> = _updates.asSharedFlow()
    private val _catalogRefreshing = MutableStateFlow(false)
    val catalogRefreshing: StateFlow<Boolean> = _catalogRefreshing.asStateFlow()

    private val network: GlobalCatalogApi = Retrofit.Builder().baseUrl(STORE_API_BASE_URL).addConverterFactory(GsonConverterFactory.create()).client(okhttp3.OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).build()).build().create(GlobalCatalogApi::class.java)

    init {
        scope.launch { syncIfNeeded() }
    }

    fun bindRepository(@Suppress("UNUSED_PARAMETER") repository: StoreRepository) = Unit

    suspend fun snapshotMetadata(): CatalogSyncMetadataEntity = dao.getMetadata() ?: CatalogSyncMetadataEntity()
    suspend fun hasValidSnapshot(): Boolean = snapshotMetadata().hasValidSnapshot

    suspend fun localHomeProducts(limit: Int = 24): List<StoreProduct> {
val metadata = snapshotMetadata()
        if (!metadata.hasValidSnapshot) return emptyList()
        val type = object : TypeToken<StoreProduct>() {}.type
        val products = dao.getHomeProducts(limit).mapNotNull { runCatching { gson.fromJson<StoreProduct>(it.payloadJson, type) }.getOrNull() }
        return products
    }

    suspend fun localCategories(): List<ProductCategory> {
        val metadata = snapshotMetadata()
        if (!metadata.hasValidSnapshot) return emptyList()
        return dao.getActiveRootCategories().map { ProductCategory(id = it.categoryId, parent = it.parentId, name = it.name, slug = it.slug, count = it.count) }
    }

    suspend fun localRecentProducts(limit: Int = 8): List<StoreProduct> = localHomeProducts(limit)

    suspend fun syncIfNeeded(force: Boolean = false): Boolean {
        val metadata = dao.getMetadata()
        val fresh = metadata?.hasValidSnapshot == true && metadata.lastCompleteSyncAt?.let { System.currentTimeMillis() - it < CATALOG_TTL_MS } == true
        if (!force && fresh) return true
        val deferred: CompletableDeferred<Boolean>
        var owner = false
        syncMutex.withLock {
            val existing = syncInFlight
            if (existing != null) deferred = existing else {
                val created = CompletableDeferred<Boolean>()
                syncInFlight = created
                deferred = created
                owner = true
            }
        }
        if (!owner) return deferred.await()
        _catalogRefreshing.value = true
        return try {
            val success = runCatching { performGlobalSync() }.getOrElse { error ->
                false
            }
            deferred.complete(success)
            success
        } finally {
            _catalogRefreshing.value = false
            syncMutex.withLock { if (syncInFlight === deferred) syncInFlight = null }
        }
    }

    suspend fun load(categoryId: Int, networkFallback: suspend () -> List<StoreProduct>): List<StoreProduct> {
        CategoryLoadTelemetry.cacheStart(categoryId)
        val metadataBeforeMemory = snapshotMetadata()
        val memoryValue = memoryMutex.withLock { if (generationInMemory == metadataBeforeMemory.activeGeneration) memory[categoryId] else null }
        if (memoryValue != null) {
            if (metadataBeforeMemory.lastCompleteSyncAt?.let { System.currentTimeMillis() - it >= CATALOG_TTL_MS } == true) scope.launch { syncIfNeeded() }
            emitCategoryAndAncestors(categoryId, memoryValue, metadataBeforeMemory.activeGeneration)
            CategoryLoadTelemetry.cacheEnd(categoryId, "memory", memoryValue.size)
            CategoryLoadTelemetry.cacheHit(categoryId, "memory", memoryValue.size)
            return memoryValue
        }

        val metadata = snapshotMetadata()
        if (metadata.hasValidSnapshot) {
            if (metadata.lastCompleteSyncAt?.let { System.currentTimeMillis() - it >= CATALOG_TTL_MS } == true) scope.launch { syncIfNeeded() }
            val products = queryCategoryTree(categoryId)
            val categoryExists = dao.hasActiveCategory(categoryId)
            if (products.isNotEmpty() || categoryExists) {
                memoryMutex.withLock { memory[categoryId] = products; generationInMemory = metadata.activeGeneration }
                CategoryLoadTelemetry.cacheEnd(categoryId, "room", products.size)
                CategoryLoadTelemetry.cacheHit(categoryId, "room", products.size)
                emitCategoryAndAncestors(categoryId, products, metadata.activeGeneration)
                return products
            }
        } else {
            scope.launch { syncIfNeeded() }
        }

        val priorityCached = dao.getPriorityFallback(categoryId)
        if (priorityCached.isNotEmpty()) {
            val products = decodePriority(priorityCached)
            CategoryLoadTelemetry.cacheEnd(categoryId, "priorityRoom", products.size)
            CategoryLoadTelemetry.cacheHit(categoryId, "priorityRoom", products.size)
            memoryMutex.withLock { memory[categoryId] = products; generationInMemory = metadata.activeGeneration }
            return products
        }

        CategoryLoadTelemetry.cacheMiss(categoryId)
        val products = loadPriorityCategory(categoryId, networkFallback)
        memoryMutex.withLock { memory[categoryId] = products; generationInMemory = metadata.activeGeneration }
        emitCategoryAndAncestors(categoryId, products, metadata.activeGeneration)
        return products
    }

    suspend fun cached(categoryId: Int): List<StoreProduct>? {
        val metadata = snapshotMetadata()
        if (!metadata.hasValidSnapshot) return null
        val cached = memoryMutex.withLock { if (generationInMemory == metadata.activeGeneration) memory[categoryId] else null }
        if (cached != null) return cached
        val products = queryCategoryTree(categoryId)
        memoryMutex.withLock { generationInMemory = metadata.activeGeneration; memory[categoryId] = products }
        return products
    }

    private suspend fun loadPriorityCategory(categoryId: Int, networkFallback: suspend () -> List<StoreProduct>): List<StoreProduct> {
        val deferred: CompletableDeferred<List<StoreProduct>>
        var owner = false
        priorityMutex.withLock {
            val existing = priorityInFlight[categoryId]
            if (existing != null) deferred = existing else {
                val created = CompletableDeferred<List<StoreProduct>>()
                priorityInFlight[categoryId] = created
                deferred = created
                owner = true
            }
        }
        if (!owner) return deferred.await()
        return try {
            priorityWaitersMutex.withLock { priorityWaiters++ }
            CategoryLoadTelemetry.networkStart(categoryId)
            val products = priorityNetworkMutex.withLock { networkFallback() }
            CategoryLoadTelemetry.networkEnd(categoryId, products.size)
            CategoryLoadTelemetry.parseEnd(categoryId, products.size)
            deferred.complete(products)
            val type = object : TypeToken<StoreProduct>() {}.type
            val fetchedAt = System.currentTimeMillis()
            val entities = products.distinctBy { it.id }.mapIndexed { index, product -> CatalogPriorityFallbackEntity(categoryId, product.id, gson.toJson(product, type), index, fetchedAt) }
            if (entities.isNotEmpty()) {
                scope.launch { runCatching { database.withTransaction { dao.insertPriorityFallback(entities) } }.onSuccess { CategoryLoadTelemetry.roomWriteEnd(categoryId, products.size) } }
            } else CategoryLoadTelemetry.roomWriteEnd(categoryId, 0)
            products
        } catch (error: Exception) {
            deferred.completeExceptionally(error)
            throw error
        } finally {
            priorityWaitersMutex.withLock { priorityWaiters-- }
            priorityMutex.withLock { if (priorityInFlight[categoryId] === deferred) priorityInFlight.remove(categoryId) }
        }
    }

    private suspend fun <T> backgroundNetwork(block: suspend () -> T): T {
        while (priorityWaitersMutex.withLock { priorityWaiters > 0 }) delay(10)
        return networkGate.withLock { block() }
    }

    private fun decodePriority(rows: List<CatalogPriorityFallbackEntity>): List<StoreProduct> {
        val type = object : TypeToken<StoreProduct>() {}.type
        return rows.mapNotNull { runCatching { gson.fromJson<StoreProduct>(it.payloadJson, type) }.getOrNull() }.distinctBy { it.id }
    }

    private suspend fun queryCategoryTree(categoryId: Int): List<StoreProduct> {
        val categories = dao.getActiveCategories()
        if (categories.isEmpty()) return emptyList()
        val childrenByParent = categories.groupBy { it.parentId }
        val ids = linkedSetOf<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(categoryId)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!ids.add(id)) continue
            childrenByParent[id].orEmpty().forEach { queue.addLast(it.categoryId) }
        }
        val queryIds = ids.take(998)
        if (queryIds.isEmpty()) return emptyList()
        val type = object : TypeToken<StoreProduct>() {}.type
        return dao.getProductsForCategories(queryIds).mapNotNull { entity -> runCatching { gson.fromJson<StoreProduct>(entity.payloadJson, type) }.getOrNull() }.distinctBy { it.id }
    }

    private suspend fun emitCategoryAndAncestors(categoryId: Int, products: List<StoreProduct>, generation: Long?) {
        if (generation == null) return
        val categories = dao.getActiveCategories()
        val byId = categories.associateBy { it.categoryId }
        val ids = linkedSetOf<Int>()
        var current: Int? = categoryId
        while (current != null && ids.add(current)) current = byId[current]?.parentId?.takeIf { it != 0 }
        ids.forEach { id ->
            val shouldEmit = emittedMutex.withLock {
                if (emittedGenerationByCategory[id] == generation) false else { emittedGenerationByCategory[id] = generation; true }
            }
            if (shouldEmit) {
                val value = if (id == categoryId) products else queryCategoryTree(id)
                _updates.tryEmit(CategoryCacheUpdate(id, value))
            }
        }
    }

    private suspend fun performGlobalSync(): Boolean = coroutineScope {
        val generation = System.currentTimeMillis()
        val firstResponse = backgroundNetwork { network.productsPage() }
        if (!firstResponse.isSuccessful) throw IllegalStateException("Catalog first page HTTP ${firstResponse.code()}")
        val firstPage = firstResponse.body().orEmpty()
        val totalPages = firstResponse.headers()["X-WP-TotalPages"]?.toIntOrNull()
        val pages = mutableListOf<Pair<Int, List<StoreProduct>>>()
        pages += 1 to firstPage
        val pageCount = totalPages?.takeIf { it > 0 }
        if (pageCount != null) {
            for (page in 2..pageCount) {
                val result = backgroundNetwork {
                    val response = network.productsPage(page = page)
                    if (!response.isSuccessful) throw IllegalStateException("Catalog page $page HTTP ${response.code()}")
                    response.body().orEmpty()
                }
                pages += page to result
            }
        } else {
            var page = 2
            while (true) {
                val batch = backgroundNetwork {
                    val response = network.productsPage(page = page)
                    if (!response.isSuccessful) throw IllegalStateException("Catalog page $page HTTP ${response.code()}")
                    response.body().orEmpty()
                }
                pages += page to batch
                if (batch.size < CATALOG_PAGE_SIZE) break
                page++
            }
        }
        val orderedProducts = pages.sortedBy { it.first }.flatMap { it.second }.distinctBy { it.id }
        val productEntities = ArrayList<CatalogProductEntity>(orderedProducts.size)
        val relationEntities = ArrayList<CatalogProductCategoryEntity>()
        val categoryEntities = LinkedHashMap<Int, CatalogCategoryEntity>()
        val type = object : TypeToken<StoreProduct>() {}.type
        orderedProducts.forEachIndexed { index, product ->
            productEntities += CatalogProductEntity(generation, product.id, gson.toJson(product, type), index)
            product.categories.forEach { category ->
                relationEntities += CatalogProductCategoryEntity(generation, product.id, category.id)
                categoryEntities[category.id] = CatalogCategoryEntity("$generation:${category.id}", generation, category.id, category.parent, category.name, category.slug, category.count)
            }
        }
        database.withTransaction {
            dao.deleteProductsForGeneration(generation)
            dao.deleteRelationsForGeneration(generation)
            dao.deleteCategoriesForGeneration(generation)
            if (productEntities.isNotEmpty()) dao.insertProducts(productEntities)
            if (relationEntities.isNotEmpty()) dao.insertRelations(relationEntities)
            if (categoryEntities.isNotEmpty()) dao.insertCategories(categoryEntities.values.toList())
            dao.deleteOldRelations(generation)
            dao.deleteOldCategories(generation)
            dao.deleteOldProducts(generation)
            dao.clearPriorityFallback()
            dao.upsertMetadata(CatalogSyncMetadataEntity(1, generation, System.currentTimeMillis(), true))
        }
        memoryMutex.withLock { memory.clear(); generationInMemory = generation }
        emittedMutex.withLock { emittedGenerationByCategory.clear() }
        true
    }
}

class CategoryCacheStoreApi(private val delegate: StoreApi, private val cache: CategoryCatalogCache) : StoreApi by delegate {
    override suspend fun products(perPage: Int, page: Int, search: String?, category: Int?, orderBy: String?, order: String?, after: String?, featured: Boolean?): List<StoreProduct> {
        if (category != null && search == null && page == 1 && orderBy == null && order == null && after == null && featured == null) {
            return cache.load(category) { delegate.products(perPage, page, search, category, orderBy, order, after, featured) }
        }
        if (search == null && category == null && page == 1 && orderBy == null && order == null && after == null && featured == null) {
            val local = cache.localHomeProducts(perPage.coerceAtMost(24))
            if (local.isNotEmpty()) return local
        }
        if (search == null && category == null && page == 1 && orderBy == "date" && order == "desc") {
            val local = cache.localRecentProducts(perPage.coerceAtMost(8))
            if (local.isNotEmpty()) return local
        }
        return delegate.products(perPage, page, search, category, orderBy, order, after, featured)
    }

    override suspend fun categories(perPage: Int): List<ProductCategory> {
        val local = cache.localCategories()
        if (local.isNotEmpty()) return local
        return delegate.categories(perPage)
    }
}
