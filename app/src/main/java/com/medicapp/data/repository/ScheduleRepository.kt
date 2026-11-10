package com.medicapp.data.repository

import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import com.medicapp.model.SavedMedication
import kotlinx.coroutines.flow.Flow

data class ScheduleWithDoses(val schedule: MedicationSchedule, val doses: List<MedicationDose>)

/** Una toma junto a la pauta de la que sale, para pintarla sin otra consulta. */
data class ScheduledDose(val schedule: MedicationSchedule, val dose: MedicationDose)

/** Lo que cambia al editar una pauta: las tomas pendientes que se quitan y las que se crean, para mover sus alarmas. */
data class ScheduleEditResult(
    val schedule: MedicationSchedule,
    val removedDoseIds: List<String>,
    val newDoses: List<MedicationDose>,
)

interface ScheduleRepository {

    /**
     * Tomas previstas entre [fromMillis] (incluido) y [toMillis] (excluido) de todas las pautas,
     * activas o cerradas, ordenadas por hora y en tiempo real. Es lo que pintan Hoy y el
     * historial semanal.
     */
    fun observeDosesBetween(fromMillis: Long, toMillis: Long): Flow<List<ScheduledDose>>

    /**
     * Tomas sin marcar cuya hora es anterior a [beforeMillis], en pautas activas, de la más
     * antigua a la más reciente y en tiempo real. Es lo que Hoy enseña como atrasado cuando una
     * toma de un día anterior se quedó sin tomar.
     */
    fun observeMissedDoses(beforeMillis: Long): Flow<List<ScheduledDose>>

    fun getSchedulesPage(limit: Long): Flow<PagedResult<MedicationSchedule>>

    /**
     * Medicamentos con pauta activa, uno por número de registro, con su nombre y su foto: para
     * marcarlos como guardados y para encontrarlos en la búsqueda sin pasar por CIMA.
     */
    fun getActiveScheduleNregistros(): Flow<List<SavedMedication>>

    fun getDoses(scheduleId: String): Flow<List<MedicationDose>>

    /** Una única pauta en tiempo real, sin traerse el resto de la colección. */
    fun observeSchedule(scheduleId: String): Flow<MedicationSchedule?>

    suspend fun getSchedule(scheduleId: String): MedicationSchedule?

    /** Si ya hay una pauta activa para este medicamento, para avisar del duplicado. */
    suspend fun hasActiveScheduleFor(nregistro: String): Boolean

    suspend fun getDose(scheduleId: String, doseId: String): MedicationDose?

    suspend fun createSchedule(
        nregistro: String,
        nombreMedicamento: String,
        fotoUrl: String?,
        intervalHours: Int,
        totalDoses: Int,
        startAtMillis: Long,
        reasonId: String,
        reasonNombre: String,
        /** Sin fin: se ignora [totalDoses] y se crean solo las tomas de los próximos días. */
        openEnded: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ): ScheduleWithDoses

    /**
     * Cambia frecuencia, número de tomas, hora de la próxima toma y motivo de una pauta activa.
     * Las tomas hechas se conservan y las pendientes se sustituyen (ver `ScheduleEditPlanner`).
     * Devuelve null si la pauta ya no está activa o el total no deja ninguna toma por hacer.
     *
     * Con [openEnded] la pauta pasa a (o sigue siendo) sin fin: se ignora [totalDoses] y las
     * pendientes se sustituyen por la ventana de los próximos días con la nueva frecuencia.
     */
    suspend fun updateSchedule(
        scheduleId: String,
        intervalHours: Int,
        totalDoses: Int,
        nextDoseAtMillis: Long,
        reasonId: String,
        reasonNombre: String,
        openEnded: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ): ScheduleEditResult?

    /**
     * Marca la toma como hecha. Al marcar la última pendiente, una pauta de total fijo se cierra
     * sola; una sin fin no, solo se cierra con [closeSchedule].
     */
    suspend fun markDoseTaken(scheduleId: String, doseId: String, historyEntryId: String)

    /**
     * Crea las tomas que le faltan a una pauta sin fin activa para cubrir los próximos días
     * (ver `DoseScheduleCalculator.openEndedReplenish`) y las devuelve para ponerles alarma.
     * Lista vacía si la pauta no es sin fin, no está activa o no le falta ninguna.
     */
    suspend fun replenishOpenEndedDoses(scheduleId: String, nowMillis: Long): List<MedicationDose>

    /** [replenishOpenEndedDoses] de todas las pautas sin fin activas, consultando solo esas. */
    suspend fun replenishOpenEndedSchedules(nowMillis: Long)

    suspend fun getPendingDoses(scheduleId: String): List<MedicationDose>

    /**
     * Cierra la pauta y pasa a omitidas sus tomas pendientes. Devuelve los ids de esas tomas
     * para quitarles la alarma: pueden incluir alguna que una reposición creó mientras tanto.
     */
    suspend fun closeSchedule(scheduleId: String): List<String>

    suspend fun getActiveSchedulesWithPendingDoses(): List<ScheduleWithDoses>
}
