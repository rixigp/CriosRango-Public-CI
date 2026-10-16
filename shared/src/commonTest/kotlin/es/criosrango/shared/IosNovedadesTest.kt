package es.criosrango.shared

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
    private class Fake:StoreNovedadesDataSource{
        val pages=mutableListOf<Int>(); val sizes=mutableListOf<Int>(); val categoriesRequested=mutableListOf<List<Int>>(); val sorts=mutableListOf<StoreNovedadesSort>()
        var tree=listOf(StoreCategory(id=10,name="Niña"),StoreCategory(id=11,parent=10,name="Vestidos"),StoreCategory(id=20,name="Niño"))
        var loader: suspend (Int, Int, List<Int>, StoreNovedadesSort) -> CatalogPage<StoreProduct> = { p, _, _, _ -> CatalogPage(listOf(StoreProduct(id = p)), false, 1) }
        override suspend fun categories(page:Int,perPage:Int)=if(page==1)tree else emptyList()
        override suspend fun products(page:Int,perPage:Int,categoryIds:List<Int>,sort:StoreNovedadesSort):CatalogPage<StoreProduct>{pages+=page;sizes+=perPage;categoriesRequested+=categoryIds;sorts+=sort;return loader(page,perPage,categoryIds,sort)}
    }
    @Test fun initialQuery_isDateDescAndPageSize24()=runTest{
        val s=Fake();val c=StoreNovedadesController(this,s);c.start();advanceUntilIdle()
        assertEquals("date",s.sorts.first().orderBy);assertEquals("desc",s.sorts.first().order);assertEquals(24,s.sizes.first());assertEquals(listOf(1),s.pages)
    }
    @Test fun loadNextPage_usesPageTwo()=runTest{
        val s=Fake();s.loader={p,_,_,_->CatalogPage(listOf(StoreProduct(id=p)),p==1,50)}
        val c=StoreNovedadesController(this,s);c.start();advanceUntilIdle();c.loadNextPage();advanceUntilIdle()
        assertEquals(listOf(1,2),s.pages);assertEquals(listOf(24,24),s.sizes)
    }
    @Test fun hasMoreFalse_stopsPagination()=runTest{
        val s=Fake();val c=StoreNovedadesController(this,s);c.start();advanceUntilIdle();c.loadNextPage();advanceUntilIdle()
        assertEquals(listOf(1),s.pages);assertFalse(c.state.value.paging.hasMore)
    }
    @Test fun audienceChange_resetsPageAndQueriesDescendants()=runTest{
        val s=Fake();val c=StoreNovedadesController(this,s);c.start();advanceUntilIdle();c.selectAudience(StoreNovedadesAudience.GIRL);advanceUntilIdle()
        assertEquals(listOf(10,11),s.categoriesRequested.last());assertEquals(1,s.pages.last());assertTrue(c.state.value.queryIdentity.contains("girl"))
    }
    @Test fun sortChange_resetsPageAndUsesBackendSort()=runTest{
        val s=Fake();val c=StoreNovedadesController(this,s);c.start();advanceUntilIdle();c.selectSort(StoreNovedadesSort.PRICE_ASC);advanceUntilIdle()
        assertEquals(1,s.pages.last());assertEquals("price",s.sorts.last().orderBy);assertEquals("asc",s.sorts.last().order)
    }
    @Test fun queryIdentity_excludesPage()=runTest{
        val s=Fake();s.loader={p,_,_,_->CatalogPage(listOf(StoreProduct(id=p)),p==1,50)}
        val c=StoreNovedadesController(this,s);c.start();advanceUntilIdle();val key=c.state.value.queryIdentity;c.loadNextPage();advanceUntilIdle()
        assertEquals(key,c.state.value.queryIdentity)
    }
    @Test fun staleResponse_doesNotReplaceNewQuery()=runTest{
        val s=Fake();val gate=CompletableDeferred<CatalogPage<StoreProduct>>()
        s.loader={_,_,ids,_->if(ids.isEmpty())gate.await() else CatalogPage(listOf(StoreProduct(id=2)),false)}
        val c=StoreNovedadesController(this,s);c.start();runCurrent();c.selectAudience(StoreNovedadesAudience.GIRL);advanceUntilIdle()
        gate.complete(CatalogPage(listOf(StoreProduct(id=1)),false));advanceUntilIdle()
        assertEquals(listOf(2),c.state.value.paging.items.map{it.id})
    }
    @Test fun retry_reissuesFailedQuery()=runTest{
        val s=Fake();var n=0;s.loader={p,_,_,_->n++;if(n==1)throw IllegalStateException("temporary") else CatalogPage(listOf(StoreProduct(id=p)),false)}
        val c=StoreNovedadesController(this,s);c.start();advanceUntilIdle();assertEquals("temporary",c.state.value.paging.initialError?.message)
        c.retry();advanceUntilIdle();assertEquals(2,n);assertEquals(1,c.state.value.paging.items.size)
    }
    @Test fun audienceResolutionIncludesDescendants(){
        assertEquals(listOf(10,11),resolveAudienceCategoryIds(listOf(StoreCategory(id=10,name="Niña"),StoreCategory(id=11,parent=10,name="Vestidos"),StoreCategory(id=20,name="Niño")),StoreNovedadesAudience.GIRL))
    }
    @Test fun platformWrappersCanUseOnlyTheSharedControllerContract()=runTest{
        assertEquals(24,StoreNovedadesController.PAGE_SIZE);assertEquals("novedades:all:recent",StoreNovedadesState().queryIdentity)
        val s=Fake();val c=StoreNovedadesController(this,s);c.start();advanceUntilIdle();assertEquals(StoreNovedadesSort.RECENT,c.state.value.selectedSort)
    }
}
