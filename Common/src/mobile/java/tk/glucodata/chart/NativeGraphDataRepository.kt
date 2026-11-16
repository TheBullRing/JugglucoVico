/*
 * Phase 2 — Data Layer Abstraction
 * Transitional implementation that reads glucose data from the C++ layer via JNI.
 *
 * This class does NOT modify any native code.  It reads the same data structures
 * that the existing NanoVG renderer already reads, using only native methods that
 * are already declared in Natives.java.
 *
 * Threading contract
 * ──────────────────
 * All Flow emissions happen on [Dispatchers.IO] via [flowOn].  Callers (the
 * ViewModel) always collect on [Dispatchers.Main] via [stateIn].
 *
 * Lifecycle of data refresh
 * ──────────────────────────
 * Data is refreshed on every emission of [dataRefreshBus].  During Phase 2
 * (before the Compose screen is wired up) the bus is driven by a 1-second
 * heartbeat so the repository is testable end-to-end.  In Phase 3, GlucoseCurve
 * will additionally push to [notifyDataChanged] from its summaryready() callback.
 */
package tk.glucodata.chart

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import tk.glucodata.Applic
import tk.glucodata.Natives
import tk.glucodata.MainActivity

/**
 * [GraphDataRepository] implementation backed by the existing JNI/C++ data layer.
 *
 * Obtain the singleton instance via [NativeGraphDataRepository.instance].
 */
class NativeGraphDataRepository private constructor() : GraphDataRepository {

    // ── Companion / singleton ─────────────────────────────────────────────────

    companion object {
        /** Singleton — one per process.  Thread-safe via double-checked locking. */
        @Volatile private var _instance: NativeGraphDataRepository? = null
        @JvmStatic
        val instance: NativeGraphDataRepository
            get() = _instance ?: synchronized(this) {
                _instance ?: NativeGraphDataRepository().also { _instance = it }
            }

        /**
         * Push-to-refresh bus.
         *
         * Call this from [tk.glucodata.GlucoseCurve.summaryready] (Phase 3) to trigger
         * an immediate data snapshot without waiting for the next heartbeat tick.
         *
         * ```java
         * // In GlucoseCurve.summaryready() — Phase 3 addition:
         * NativeGraphDataRepository.Companion.notifyDataChanged();
         * ```
         */
        private val _dataRefreshBus = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Public surface so Java callers (GlucoseCurve) can emit without reflection. */
        @JvmStatic
        fun notifyDataChanged() {
            _dataRefreshBus.tryEmit(Unit)
        }

        const val TAG = "VicoRepo"
    }

    // ── Heartbeat tick (1 s) ─────────────────────────────────────────────────

    /** Emits a Unit every second so the chart refreshes even without a native push. */
    private val heartbeat: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1_000L)
        }
    }

    /** Combined trigger: either a heartbeat or an explicit native push. */
    private val refreshTrigger: Flow<Unit> =
        merge(heartbeat, _dataRefreshBus.asSharedFlow())

    // ── GraphDataRepository ───────────────────────────────────────────────────

    override fun observeGlucoseEntries(): Flow<List<GlucoseEntry>> = flow {
        refreshTrigger.collect {
            emit(snapshotGlucoseEntries())
        }
    }.flowOn(Dispatchers.IO)

    override fun observeViewport(): Flow<ChartViewport> = flow {
        refreshTrigger.collect {
            emit(snapshotViewport())
        }
    }.flowOn(Dispatchers.IO)

    override fun observeSettings(): Flow<ChartSettings> = flow {
        refreshTrigger.collect {
            emit(snapshotSettings())
        }
    }.flowOn(Dispatchers.IO)

    // ── Private snapshot helpers ─────────────────────────────────────────────

    /**
     * Packed-long protocol shared by [Natives.streamfromSensorptr] and
     * [Natives.historyfromSensorptr] (defined in g.cpp):
     *   bits  0-31  → Unix time in seconds (UTC)
     *   bits 32-47  → glucose value in mg/dL (calibrated when available)
     *   bits 48-63  → next pos to pass on the following call
     *
     * When no more valid entries exist at or after [pos], bits 0-31 are 0.
     */
    private fun readPackedEntries(
        sensorPtr: Long,
        sensorIndex: Int,
        startPos: Int,
        callJni: (Long, Int) -> Long,
        into: MutableMap<Long, GlucoseEntry>,
    ) {
        var pos = startPos
        while (true) {
            val packed  = callJni(sensorPtr, pos)
            val timeSec = packed and 0xFFFFFFFFL
            if (timeSec == 0L) break          // no more entries

            val nextPos = ((packed shr 48) and 0xFFFFL).toInt()
            val mgdL    = ((packed shr 32) and 0xFFFFL).toFloat()
            if (mgdL > 0f) {
                // Deduplicate by timestamp: polls override history if same second
                val tsMs = timeSec * 1_000L
                into[tsMs] = GlucoseEntry(tsMs, mgdL, sensorIndex)
            }

            if (nextPos <= pos) break         // safety: no forward progress
            pos = nextPos
        }
    }

    /**
     * Walks all active sensors via [Natives.activeSensorPtrs] and returns a
     * flat, time-sorted list of [GlucoseEntry] objects.
     *
     * Each sensor has TWO independent data sources that must both be read:
     *  1. **history[]** — NFC scan entries (up to 8h back at 1-min resolution),
     *     read via [Natives.historyfromSensorptr].
     *  2. **polls[]**   — BLE streaming entries (most recent, ~5-min resolution),
     *     read via [Natives.streamfromSensorptr].
     *
     * Both are merged into a single timestamp-keyed map (polls win on collision)
     * so the Vico chart shows the same data as the legacy C++/NanoVG renderer.
     *
     * NOTE: healthConnectfromSensorptr is NOT used — it tracks the Health Connect
     * export cursor, which starts at 0 when HC is disabled, reading nothing.
     */
    private fun snapshotGlucoseEntries(): List<GlucoseEntry> {
        val sensorPtrs: LongArray = Natives.allSensorPtrs() ?: return emptyList()
        if (sensorPtrs.isEmpty()) return emptyList()

        val result = mutableListOf<GlucoseEntry>()

        sensorPtrs.forEachIndexed { sensorIndex, sensorPtr ->
            if (sensorPtr == 0L || Natives.getHidefromSensorptr(sensorPtr)) return@forEachIndexed

            // Keyed by timestampMs so duplicates between history and polls are merged
            val merged = mutableMapOf<Long, GlucoseEntry>()

            // 1. NFC history (up to 8 h back)
            readPackedEntries(sensorPtr, sensorIndex, 0, Natives::historyfromSensorptr, merged)
            // 2. BLE stream polls (most recent readings, override history on same ts)
            readPackedEntries(sensorPtr, sensorIndex, 0, Natives::streamfromSensorptr, merged)

            val count = merged.size
            Log.d(TAG, "  sensor[$sensorIndex] entries=$count (history+polls merged)")
            result += merged.values
        }

        Log.d(TAG, "snapshotGlucoseEntries: total=${result.size}")
        return result.sortedBy { it.timestampMs }
    }

    /**
     * Reads the current chart viewport from the C++ renderer state.
     *
     * [Natives.getstarttime] / [Natives.getendtime] return Unix seconds (not ms).
     * [Natives.graphlow] / [Natives.graphhigh] return mg/dL float values.
     */
    private fun snapshotViewport(): ChartViewport = ChartViewport(
        startMs = Natives.getstarttime() * 1_000L,
        endMs   = Natives.getendtime()   * 1_000L,
        minY    = Natives.graphlow(),
        maxY    = Natives.graphhigh(),
    )

    /**
     * Reads per-render settings that affect chart appearance.
     * All Natives calls are fast synchronous reads from a shared native struct.
     */
    private fun snapshotSettings(): ChartSettings {
        // Natives.getunit() encoding (from Settings.java / javasettings.cpp):
        //   0 = not configured yet
        //   1 = mmol/L
        //   2 = mg/dL
        val ismmol = Natives.getunit() == 1

        // targetlow() / targethigh() call gconvert() in C++ which converts from
        // internal mg/L storage to the display unit (mmol/L or mg/dL).
        // Store them in DISPLAY units so no further conversion is needed in the UI.
        val targetLow  = Natives.targetlow()
        val targetHigh = Natives.targethigh()

        // Umbrales "muy bajo" y "muy alto" para coloración de 5 zonas.
        //
        // Jerarquía de fuentes (de más a menos específico):
        //   veryLow  : alarmverylow  → alarmlow (umbral bajo normal) → 60 mg/dL fijo
        //   veryHigh : alarmveryhigh → alarmhigh (umbral alto normal) → targetHigh + 40
        //
        // Esto asegura que la franja amarilla refleja el rango de alarma real del usuario,
        // y no un valor hardcoded que puede ser demasiado amplio (ej. 180→250).
        val veryLow = when {
            Natives.hasalarmverylow()  -> Natives.alarmverylow()
            Natives.hasalarmlow()      -> Natives.alarmlow()
            else                       -> if (ismmol) 3.3f else 60f
        }
        val veryHigh = when {
            Natives.hasalarmveryhigh() -> Natives.alarmveryhigh()
            Natives.hasalarmhigh()     -> Natives.alarmhigh()
            else                       -> targetHigh + if (ismmol) 2.2f else 40f
        }

        // sensorends() devuelve uint32_t en SEGUNDOS desde epoch (ver javacurve.cpp:60).
        // Multiplicamos por 1000 para obtener milisegundos.
        val sensorEndsRaw = Natives.sensorends()
        val sensorEndsMs  = if (sensorEndsRaw > 0L) sensorEndsRaw * 1_000L else 0L
        Log.d(TAG, "snapshotSettings: sensorEndsRaw=$sensorEndsRaw sensorEndsMs=$sensorEndsMs")

        return ChartSettings(
            targetLow       = targetLow,
            targetHigh      = targetHigh,
            veryLow         = veryLow,
            veryHigh        = veryHigh,
            invertColors    = Natives.getInvertColors(),
            unit            = if (ismmol) GlucoseUnit.MMOL else GlucoseUnit.MGDL,
            isRtl           = MainActivity.rtl,
            sensorEndsMs    = sensorEndsMs,
            portraitPivoted = Applic.usePortraitPivoted(),
        )
    }
}
