package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class StoreNovedadesAudience(val key:String,val label:String) {
    ALL("all","Todas"), GIRL("girl","Niña"), BOY("boy","Niño"), BABY("baby","Bebé"), WOMAN("woman","Mujer"), MAN("man","Hombre")
}
enum class StoreNovedadesSort(val key:String,val label:String,val orderBy:String,val order:String) {
    RECENT("recent","Más recientes","date","desc"),
    PRICE_ASC("price_asc","Precio: menor a mayor","price","asc"),
    PRICE_DESC("price_desc","Precio: mayor a menor","price","desc"),
    NAME_ASC("name_asc","Nombre A-Z","title","asc")
}
data class StoreNovedadesState(
    val selectedAudience:StoreNovedadesAudience=StoreNovedadesAudience.ALL,
    val selectedSort:StoreNovedadesSort=StoreNovedadesSort.RECENT,
    val queryIdentity:String="novedades:all:recent",
    val paging:CatalogPagingState<StoreProduct> = CatalogPagingState()
)
interface StoreNovedadesDataSource {
    suspend fun categories(page:Int,perPage:Int):List<StoreCategory>
    suspend fun products(page:Int,perPage:Int,categoryIds:List<Int>,sort:StoreNovedadesSort):CatalogPage<StoreProduct>
}
class StoreApiNovedadesDataSource(private val api:StoreApiClient):StoreNovedadesDataSource {
    override suspend fun categories(page:Int,perPage:Int)=api.categories(perPage=perPage,page=page)
    override suspend fun products(page:Int,perPage:Int,categoryIds:List<Int>,sort:StoreNovedadesSort):CatalogPage<StoreProduct> {
        val result=api.productsPage(perPage,page,categoryIds,sort.orderBy,sort.order)
        val more=result.totalPages?.let { page<it } ?: (result.items.size>=perPage)
        return CatalogPage(result.items,more,result.totalItems)
    }
}
class StoreNovedadesController(private val scope:CoroutineScope,private val source:StoreNovedadesDataSource) {
    companion object { const val PAGE_SIZE=24 }
    private val paginator=CatalogPaginator<StoreProduct>(pageSize=PAGE_SIZE,identity={it.id})
    private var generation=0L
    private var started=false
    private var activeLoader:(suspend (Int,Int)->CatalogPage<StoreProduct>)?=null
    private val mutableState=MutableStateFlow(StoreNovedadesState())
    val state:StateFlow<StoreNovedadesState> = mutableState
    fun start(){ if(!started){started=true;restart()} }
    fun selectAudience(value:StoreNovedadesAudience){
        if(value==mutableState.value.selectedAudience)return
        mutableState.value=mutableState.value.copy(selectedAudience=value,queryIdentity=identity(value,mutableState.value.selectedSort))
        restart()
    }
    fun selectSort(value:StoreNovedadesSort){
        if(value==mutableState.value.selectedSort)return
        mutableState.value=mutableState.value.copy(selectedSort=value,queryIdentity=identity(mutableState.value.selectedAudience,value))
        restart()
    }
    fun refresh()=restart()
    fun retry()=restart()
    fun loadNextPage(expectedIdentity:String?=null){
        val snapshot=mutableState.value
        if(expectedIdentity!=null && expectedIdentity!=snapshot.queryIdentity)return
        val loader=activeLoader?:return
        val requestGeneration=generation
        if(snapshot.paging.isAppending||snapshot.paging.isInitialLoading||!snapshot.paging.hasMore||snapshot.paging.currentPage<=0)return
        mutableState.value=snapshot.copy(paging=snapshot.paging.copy(isAppending=true,appendError=null))
        scope.launch {
            paginator.loadNext(loader)
            if(requestGeneration==generation && snapshot.queryIdentity==mutableState.value.queryIdentity)
                mutableState.value=mutableState.value.copy(paging=paginator.state.value)
        }
    }
    private fun restart(){
        val requestGeneration=++generation
        val snapshot=mutableState.value
        val key=identity(snapshot.selectedAudience,snapshot.selectedSort)
        mutableState.value=snapshot.copy(queryIdentity=key,paging=CatalogPagingState(isInitialLoading=true))
        var idsCache:List<Int>?=null
        val loader:suspend (Int,Int)->CatalogPage<StoreProduct> = { page,size ->
            val ids=idsCache ?: if(snapshot.selectedAudience==StoreNovedadesAudience.ALL) emptyList() else resolveAudienceCategoryIds(loadCategories(),snapshot.selectedAudience).also { idsCache=it }
            if(snapshot.selectedAudience!=StoreNovedadesAudience.ALL && ids.isEmpty()) CatalogPage(emptyList(),false,0)
            else source.products(page,size,ids,snapshot.selectedSort)
        }
        activeLoader=loader
        scope.launch {
            if(requestGeneration!=generation)return@launch
            paginator.start(key,loader)
            if(requestGeneration==generation && key==mutableState.value.queryIdentity)
                mutableState.value=mutableState.value.copy(paging=paginator.state.value)
        }
    }
    private suspend fun loadCategories():List<StoreCategory>{
        val all=mutableListOf<StoreCategory>(); var page=1
        while(true){ val batch=source.categories(page,100); all+=batch; if(batch.size<100)break; page++ }
        return all.distinctBy { it.id }
    }
    private fun identity(a:StoreNovedadesAudience,s:StoreNovedadesSort)="novedades:"+a.key+":"+s.key
}
internal fun resolveAudienceCategoryIds(categories:List<StoreCategory>,audience:StoreNovedadesAudience):List<Int>{
    if(audience==StoreNovedadesAudience.ALL)return emptyList()
    val byId=categories.associateBy{it.id}
    val targets=when(audience){
        StoreNovedadesAudience.ALL->emptySet()
        StoreNovedadesAudience.GIRL->setOf("nina")
        StoreNovedadesAudience.BOY->setOf("nino")
        StoreNovedadesAudience.BABY->setOf("bebe nina","bebe nino","recien nacido")
        StoreNovedadesAudience.WOMAN->setOf("mujer")
        StoreNovedadesAudience.MAN->setOf("hombre")
    }
    fun normalize(s:String)=s.lowercase().trim().replace('á','a').replace('é','e').replace('í','i').replace('ó','o').replace('ú','u').replace('ü','u').replace('ñ','n')
    return categories.filter { category ->
        var current:StoreCategory?=category; val visited=mutableSetOf<Int>(); var match=false
        while(current!=null && current.id!=0 && visited.add(current.id)){
            if(normalize(current.name) in targets){match=true;break}
            current=byId[current.parent]
        }
        match
    }.map{it.id}.distinct().sorted()
}
