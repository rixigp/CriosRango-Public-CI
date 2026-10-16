package es.criosrango.shared
import es.criosrango.shared.model.ProductPrices
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosNovedadesTest {
 private class Fake:StoreNovedadesDataSource {
  val tree=listOf(StoreCategory(id=10,name="Bautizo",slug="bautizo"),StoreCategory(id=11,parent=10,name="Ropa bautizo"),StoreCategory(id=20,name="Bebé niña",slug="bebe-nina"),StoreCategory(id=21,parent=20,name="Vestidos bebé"),StoreCategory(id=30,name="Bebé niño",slug="bebe-nino"),StoreCategory(id=40,name="Niña",slug="nina"),StoreCategory(id=41,parent=40,name="Vestidos niña"),StoreCategory(id=50,name="Niño",slug="nino"),StoreCategory(id=60,name="Comunión niña",slug="comunion-nina"),StoreCategory(id=61,parent=60,name="Vestidos comunión"),StoreCategory(id=70,name="Comunión niño",slug="comunion-nino"),StoreCategory(id=80,name="Mujer",slug="mujer"),StoreCategory(id=81,parent=80,name="Vestidos mujer"),StoreCategory(id=90,name="Hombre",slug="hombre"),StoreCategory(id=100,name="Recién nacido",slug="recien-nacido"),StoreCategory(id=101,parent=100,name="Pijamas"))
  var calls=0;var gate:CompletableDeferred<CatalogPage<StoreProduct>>?=null
  val pages=mutableListOf<Int>();val sizes=mutableListOf<Int>();val categoryQueries=mutableListOf<List<Int>>();val sorts=mutableListOf<StoreNovedadesSort>()
  var optionsCalls=0;var filterCalls=0;val filterRequests=mutableListOf<Triple<List<Int>,List<Int>,List<Int>>>()
  var filterIds:Set<Int> = setOf(2,4,999)
  var products=(1..10).map { id ->
   val cat=when(id){1->11;2->21;3->30;4->41;5->50;6->61;7->70;8->81;9->90;else->101}
   val name=when(id){1->"Bautizo";2->"Bebé niña";3->"Bebé niño";4->"Niña";5->"Niño";6->"Comunión niña";7->"Comunión niño";8->"Mujer";9->"Hombre";else->"Recién nacido"}
   StoreProduct(id=id,name=name,prices=ProductPrices(price=(id*10).toString()),categories=listOf(StoreCategory(id=cat)))
  }
  val options=StoreFilterOptions(listOf(StoreFilterOption(342,"6A","6a","pa_tallas")),listOf(StoreFilterOption(353,"Azul","azul","pa_color")),listOf(StoreFilterOption(777,"Marca A","marca-a")))
  override suspend fun categories(page:Int,perPage:Int)=if(page==1)tree else emptyList()
  override suspend fun products(page:Int,perPage:Int,categoryIds:List<Int>,sort:StoreNovedadesSort):CatalogPage<StoreProduct>{
   calls++;pages+=page;sizes+=perPage;categoryQueries+=categoryIds;sorts+=sort
   if(calls==1)gate?.let{return it.await()}
   return CatalogPage(products,true,999)
  }
  override suspend fun loadFilterOptions():StoreFilterOptions{optionsCalls++;return options}
  override suspend fun matchingProductIds(sizeIds:List<Int>,colorIds:List<Int>,brandIds:List<Int>):Set<Int>{filterCalls++;filterRequests+=Triple(sizeIds,colorIds,brandIds);return filterIds}
 }
 private suspend fun open(c:StoreNovedadesController){c.openFilters()}

 @Test fun initialQueryLoadsOnlyOneGlobalRecentPageOf24()=runTest{
  val f=Fake();val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle()
  assertEquals(1,f.calls);assertEquals(listOf(1),f.pages);assertEquals(listOf(24),f.sizes);assertEquals(listOf(emptyList()),f.categoryQueries)
  assertEquals(listOf(StoreNovedadesSort.RECENT),f.sorts);assertEquals(10,c.state.value.paging.items.size);assertFalse(c.state.value.paging.hasMore)
 }
 @Test fun audienceGroupsFilterOnlyLoadedSnapshot()=runTest{
  val f=Fake();val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle()
  c.selectAudience(StoreNovedadesAudience.CRIOS);assertEquals(listOf(2,3,4,5),c.state.value.paging.items.map{it.id}.sorted())
  c.selectAudience(StoreNovedadesAudience.COMUNION);assertEquals(listOf(6,7),c.state.value.paging.items.map{it.id}.sorted())
  c.selectAudience(StoreNovedadesAudience.BAUTIZO);assertEquals(listOf(1),c.state.value.paging.items.map{it.id})
  c.selectAudience(StoreNovedadesAudience.MUJER);assertEquals(listOf(8),c.state.value.paging.items.map{it.id})
  c.selectAudience(StoreNovedadesAudience.HOMBRE);assertEquals(listOf(9),c.state.value.paging.items.map{it.id})
  c.selectAudience(StoreNovedadesAudience.RECIEN_NACIDO);assertEquals(listOf(10),c.state.value.paging.items.map{it.id})
  c.selectAudience(StoreNovedadesAudience.ALL);assertEquals((1..10).toList(),c.state.value.paging.items.map{it.id});assertEquals(1,f.calls)
 }
 @Test fun categoryResolverIncludesDescendants(){assertEquals(listOf(20,21),resolveAudienceCategoryIds(listOf(StoreCategory(id=20,name="Bebé niña",slug="bebe-nina"),StoreCategory(id=21,parent=20,name="Vestidos")),StoreNovedadesAudience.CRIOS))}
 @Test fun openingCancelingAndApplyingFiltersWorks()=runTest{
  val f=Fake();val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle();val before=c.state.value.paging.items.map{it.id}
  open(c);advanceUntilIdle();assertTrue(c.state.value.filterDialogVisible);c.changeDraftSizes(setOf("6A"));c.cancelFilters()
  assertFalse(c.state.value.filterDialogVisible);assertEquals(emptySet<String>(),c.state.value.selectedSizes);assertEquals(before,c.state.value.paging.items.map{it.id});assertEquals(0,f.filterCalls)
  open(c);advanceUntilIdle();c.changeDraftSizes(setOf("6A"));c.applyFilters();advanceUntilIdle()
  assertEquals(setOf("6A"),c.state.value.selectedSizes);assertEquals(listOf(2,4),c.state.value.paging.items.map{it.id});assertEquals(1,f.calls)
 }
 @Test fun clearFiltersRestoresTheLoadedSnapshot()=runTest{
  val f=Fake();val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle();open(c);advanceUntilIdle();c.changeDraftSizes(setOf("6A"));c.applyFilters();advanceUntilIdle()
  open(c);advanceUntilIdle();c.clearDraftFilters();c.applyFilters();advanceUntilIdle()
  assertEquals(emptySet<String>(),c.state.value.selectedSizes);assertEquals((1..10).toList(),c.state.value.paging.items.map{it.id});assertEquals(1,f.calls)
 }
 @Test fun sizeFilterUsesExactFilterApiIdsAndIntersectsSnapshot()=runTest{
  val f=Fake();f.filterIds=setOf(2,25,999);val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle();open(c);advanceUntilIdle();c.changeDraftSizes(setOf("6A"));c.applyFilters();advanceUntilIdle()
  assertEquals(listOf(2),c.state.value.paging.items.map{it.id});assertEquals(Triple(listOf(342),emptyList<Int>(),emptyList<Int>()),f.filterRequests.single())
 }
 @Test fun colorFilterUsesExactFilterApiIds()=runTest{
  val f=Fake();f.filterIds=setOf(2,8,999);val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle();open(c);advanceUntilIdle();c.changeDraftColors(setOf("Azul"));c.applyFilters();advanceUntilIdle()
  assertEquals(listOf(2,8),c.state.value.paging.items.map{it.id});assertEquals(Triple(emptyList<Int>(),listOf(353),emptyList<Int>()),f.filterRequests.single())
 }
 @Test fun brandFilterUsesExactTagIds()=runTest{
  val f=Fake();f.filterIds=setOf(3,9,999);val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle();open(c);advanceUntilIdle();c.changeDraftBrands(setOf("Marca A"));c.applyFilters();advanceUntilIdle()
  assertEquals(listOf(3,9),c.state.value.paging.items.map{it.id});assertEquals(Triple(emptyList<Int>(),emptyList<Int>(),listOf(777)),f.filterRequests.single())
 }
 @Test fun categoryAndVariationFiltersAreIntersected()=runTest{
  val f=Fake();f.filterIds=setOf(2,4,5,9,999);val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle();c.selectAudience(StoreNovedadesAudience.CRIOS);open(c);advanceUntilIdle()
  c.changeDraftSizes(setOf("6A"));c.applyFilters();advanceUntilIdle();assertEquals(listOf(2,4,5),c.state.value.paging.items.map{it.id})
 }
 @Test fun filtersAndSortComposeWithoutReloadingGlobalProducts()=runTest{
  val f=Fake();f.filterIds=setOf(2,4,9,999);val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle();open(c);advanceUntilIdle();c.changeDraftBrands(setOf("Marca A"));c.applyFilters();advanceUntilIdle()
  c.selectSort(StoreNovedadesSort.PRICE_ASC);assertEquals(listOf(2,4,9),c.state.value.paging.items.map{it.id});assertEquals(1,f.calls);assertEquals(1,f.filterCalls)
 }
 @Test fun filtersCannotChangeDatasetOrIntroduceOlderProducts()=runTest{
  val f=Fake();f.products=(1..30).map{StoreProduct(id=it,prices=ProductPrices(price=it.toString()))};f.filterIds=setOf(2,25,26,999)
  val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle();val before=c.state.value.paging.items.map{it.id}.toSet();assertEquals((1..24).toSet(),before)
  open(c);advanceUntilIdle();c.changeDraftSizes(setOf("6A"));c.applyFilters();advanceUntilIdle();assertEquals(listOf(2),c.state.value.paging.items.map{it.id})
  c.clearFilters();assertEquals(before,c.state.value.paging.items.map{it.id}.toSet());assertEquals(1,f.calls);assertFalse(c.state.value.paging.items.any{it.id !in before})
 }
 @Test fun counterEqualsFinalVisibleResult()=runTest{
  val f=Fake();f.filterIds=setOf(2,4,9,999);val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle();c.selectAudience(StoreNovedadesAudience.CRIOS);open(c);advanceUntilIdle()
  c.changeDraftSizes(setOf("6A"));c.applyFilters();advanceUntilIdle();c.selectSort(StoreNovedadesSort.PRICE_DESC)
  assertEquals(2,c.state.value.paging.items.size);assertEquals(c.state.value.paging.items.size,c.state.value.paging.totalItems)
 }
 @Test fun sortNeverRequestsNewProductsAndPaginationIsDisabled()=runTest{
  val f=Fake();val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle();val ids=c.state.value.paging.items.map{it.id}.toSet()
  c.selectSort(StoreNovedadesSort.PRICE_ASC);c.selectSort(StoreNovedadesSort.PRICE_DESC);c.selectSort(StoreNovedadesSort.NAME_ASC);c.loadNextPage()
  assertEquals(ids,c.state.value.paging.items.map{it.id}.toSet());assertEquals(1,f.calls);assertFalse(c.state.value.paging.hasMore)
 }
 @Test fun retryReloadsOnlyGlobalPageAndPreservesAudience()=runTest{
  val f=Fake();val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle();c.selectAudience(StoreNovedadesAudience.CRIOS);c.retry();advanceUntilIdle()
  assertEquals(2,f.calls);assertEquals(listOf(1,1),f.pages);assertEquals(listOf(24,24),f.sizes);assertEquals(listOf(emptyList(),emptyList()),f.categoryQueries);assertEquals(StoreNovedadesAudience.CRIOS,c.state.value.selectedAudience)
 }
 @Test fun staleGlobalResponseCannotOverwriteRefresh()=runTest{
  val f=Fake();val gate=CompletableDeferred<CatalogPage<StoreProduct>>();f.gate=gate;val c=StoreNovedadesController(this,f);c.start();runCurrent();c.refresh();runCurrent()
  assertEquals(2,f.calls);gate.complete(CatalogPage(listOf(StoreProduct(id=999)),false,1));advanceUntilIdle();assertFalse(c.state.value.paging.items.any{it.id==999});assertEquals((1..10).toList(),c.state.value.paging.items.map{it.id})
 }
 @Test fun responseIsCappedAt24AndRecentOrderPreserved()=runTest{
  val f=Fake();f.products=(1..30).map{StoreProduct(id=it)};val c=StoreNovedadesController(this,f);c.start();advanceUntilIdle();assertEquals(24,c.state.value.paging.items.size)
  val f2=Fake();f2.products=listOf(StoreProduct(id=3),StoreProduct(id=2),StoreProduct(id=1));val c2=StoreNovedadesController(this,f2);c2.start();advanceUntilIdle();c2.selectSort(StoreNovedadesSort.RECENT);assertEquals(listOf(3,2,1),c2.state.value.paging.items.map{it.id})
 }
}
