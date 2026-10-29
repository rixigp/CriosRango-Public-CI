package es.criosrango.shared.api
import kotlin.time.TimeSource
/** Structured Store timing. Paths are endpoint templates, never full URLs. */
data class StoreRequestTiming(val requestType:String,val path:String,val networkMs:Long,val deserializationMs:Long,val totalOperationMs:Long,val httpStatus:Int?,val success:Boolean,val timeout:Boolean,val page:Int?,val perPage:Int?,val scope:String?,val errorType:String?)
fun interface StoreRequestTimingRecorder{fun record(timing:StoreRequestTiming)}
object NoOpStoreRequestTimingRecorder:StoreRequestTimingRecorder{override fun record(timing:StoreRequestTiming)=Unit}
internal class StoreRequestTimingSession(private val recorder:StoreRequestTimingRecorder,private val requestType:String,private val path:String,private val page:Int?,private val perPage:Int?,private val scope:String?){
 private val started=TimeSource.Monotonic.markNow();private var responseMark:kotlin.time.TimeMark?=null;private var networkMs=0L;private var deserializationMs=0L;private var status:Int?=null;private var finished=false
 fun networkComplete(httpStatus:Int){if(responseMark!=null)return;networkMs=started.elapsedNow().inWholeMilliseconds;responseMark=TimeSource.Monotonic.markNow();status=httpStatus}
 fun deserializationComplete(){responseMark?.let{deserializationMs=it.elapsedNow().inWholeMilliseconds}}
 fun finish(success:Boolean,error:Throwable?=null){if(finished)return;finished=true;val timeout=error?.let{it::class.simpleName.orEmpty().contains("timeout",true)}==true;if(responseMark==null)networkMs=started.elapsedNow().inWholeMilliseconds
  val errorType=when{timeout->"TIMEOUT";status!=null&&status !in 200..299->"HTTP_ERROR";error!=null->"CLIENT_ERROR";!success->"REQUEST_FAILED";else->null}
  recorder.record(StoreRequestTiming(requestType,path,networkMs,deserializationMs,started.elapsedNow().inWholeMilliseconds,status,success&&!timeout&&(status==null||status in 200..299),timeout,page,perPage,scope,errorType))
 }
}
internal suspend fun <T> recordStoreOperationTiming(recorder:StoreRequestTimingRecorder,requestType:String,path:String,page:Int?=null,perPage:Int?=null,scope:String?=null,operation:suspend()->T):T{val timing=StoreRequestTimingSession(recorder,requestType,path,page,perPage,scope);return try{operation().also{timing.finish(true)}}catch(error:Throwable){timing.finish(false,error);throw error}}
