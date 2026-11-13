package com.medicapp.ui.today

/**
 * Lo que enseña la línea del día. Las tomas sin marcar de días anteriores se pliegan en una
 * fila: con muchas, llenaban la pantalla de rojo y tapaban lo de hoy.
 */
data class TodayTimeline(
    val visible: List<TodayDose>,
    val earlierCount: Int,
    val open: TodayDose?,
) {
    companion object {
        /**
         * [selectedId] es la toma que el usuario tocó; si queda dentro del pliegue, se abre la
         * siguiente visible que toca.
         */
        fun of(plan: TodayPlan, showEarlier: Boolean, selectedId: String?): TodayTimeline {
            val earlierCount = plan.carriedOverCount
            val visible = if (showEarlier) plan.doses else plan.doses.filterNot { it.carriedOver }
            val open = visible.firstOrNull { it.dose.id == selectedId }
                ?: plan.next?.takeIf { it in visible }
                ?: visible.firstOrNull { it.pending }
            return TodayTimeline(visible, earlierCount, open)
        }
    }
}
