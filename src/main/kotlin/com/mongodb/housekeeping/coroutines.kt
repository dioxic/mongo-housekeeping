package com.mongodb.housekeeping

import com.mongodb.client.model.changestream.FullDocument
import com.mongodb.housekeeping.model.*
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


suspend fun CoroutineScope.windowState(cfgState: StateFlow<Config>): StateFlow<Window> {
    val nowState = flow {
        while (true) {
            emit(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
            delay(1.seconds)
        }
    }.stateIn(this)

    return cfgState.combine(nowState) { cfg, now ->
        Window(
            cfg.windows.isNullOrEmpty() || cfg.windows.accept(now)
        )
    }.stateIn(this)
}

suspend fun CoroutineScope.serverStatusState(db: MongoDatabase, refreshInterval: Duration) =
    flow {
        while (true) {
            emit(db.serverStatus())
            delay(refreshInterval)
        }
    }.stateIn(this)

suspend fun CoroutineScope.opcounterState(serverStatusState: StateFlow<ServerStatus>) = flow {
    var lastTime: Instant? = null
    var lastOpcounters: ServerStatus.Opcounters? = null

    serverStatusState.collect { ss ->
        val now = Clock.System.now()

        if (lastOpcounters != null && lastTime != null) {
            val duration = now.minus(lastTime!!)
            emit(lastOpcounters!!.toRate(ss.opcounters, duration))
        }
        lastTime = now
        lastOpcounters = ss.opcounters
    }
}.stateIn(this)

suspend fun CoroutineScope.configState(cfgCollection: MongoCollection<Config>) =
    cfgCollection
        .watch()
        .fullDocument(FullDocument.UPDATE_LOOKUP)
        .map { it.fullDocument }
        .stateIn(this, started = SharingStarted.Eagerly, initialValue = cfgCollection.find().first())

suspend fun CoroutineScope.rateState(
    cfgState: StateFlow<Config>,
    opcounterState: StateFlow<ServerStatus.Opcounters>
): StateFlow<Rate> =
    cfgState.combine(opcounterState) { cfg, ops ->
        Rate(cfg.rates.calculateRate(ops))
    }.stateIn(this)

suspend fun CoroutineScope.housekeepingEnabled(
    cfgState: StateFlow<Config>,
    windowState: StateFlow<Window>,
    rateState: StateFlow<Rate>
): StateFlow<Enabled> = cfgState
    .combine(windowState) { cfg, window ->
        cfg.enabled && window.value
    }
    .combine(rateState) { enabled, rate ->
        Enabled(enabled && rate.value > 0)
    }.stateIn(this)

suspend fun CoroutineScope.enabledAndCriteria(
    cfgState: StateFlow<Config>,
    enabledState: StateFlow<Enabled>
) = cfgState
    .map { it.criteria }
    .distinctUntilChanged()
    .combine(enabledState) { criteria, enabled ->
        criteria to enabled
    }.stateIn(this)

suspend fun CoroutineScope.housekeepingState(
    enabled: StateFlow<Enabled>,
    windowState: StateFlow<Window>,
    rateState: StateFlow<Rate>,
    jobStatus: StateFlow<String?>,
    opcounterState: StateFlow<ServerStatus.Opcounters>
): StateFlow<HousekeepingState> = enabled
    .combine(windowState) { enabled, window ->
        HousekeepingState.default.copy(
            enabled = enabled.value,
            window = window.toString()
        )
    }
    .combine(opcounterState) { hkState, ocState ->
        hkState.copy(dbMetrics = ocState)
    }
    .combine(rateState) { hkState, rate ->
        hkState.copy(
            rate = rate.value,
            lastUpdated = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        )
    }.combine(jobStatus) { hkState, jobStatus ->
        hkState.copy(
            status = jobStatus
        )
    }.stateIn(this)

fun <T> Flow<T>.chunked(size: StateFlow<Rate>): Flow<List<T>> {
    return flow {
        var result: ArrayList<T>? = null
        collect { value ->
            val acc = result ?: ArrayList<T>(size.value.value).also { result = it }
            acc.add(value)
            if (acc.size == size.value.value) {
                emit(acc)
                result = null
            }
        }
        result?.let { emit(it) }
    }
}

fun Flow<Map<String, Any?>>.batchMerge(size: StateFlow<Rate>): Flow<Map<String, Collection<Any>>> {
    return flow {
        var result: MutableMap<String, Set<Any>>? = null
        var count = 1
        collect { value ->
            val acc = result ?: mutableMapOf<String, Set<Any>>().also { result = it }
            value.filterValues { it != null }.forEach { (k, v) ->
                val l = when (v) {
                    is List<*> -> v.filterNotNull().toSet()
                    else -> setOf(v!!)
                }
                acc.merge(k, l) { v1, v2 ->
                    v1.plus(v2)
                }
            }
            if (count == size.value.value) {
                emit(acc)
                result = null
                count = 0
            }
            count++
        }
        result?.let { emit(it) }
    }
}

