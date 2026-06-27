package com.estudio.antiprocrastinacion.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ProgressRepository
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val SPANISH = Locale("es")

/** One schedulable item plus its persisted spaced-repetition state, as the screen needs it. */
data class UpcomingReviewSource(
    val itemId: String,
    val stem: String,
    val unitTitle: String,
    val courseTitle: String,
    val nextReviewAt: Long?,
    val stage: Int,
)

data class UpcomingReviewCard(
    val itemId: String,
    val stem: String,
    val unitTitle: String,
    val courseTitle: String,
    val timeLabel: String,
    val stage: Int,
)

data class UpcomingReviewDay(
    val dayLabel: String,
    val dateLabel: String,
    val isToday: Boolean,
    val cards: List<UpcomingReviewCard>,
) {
    val count: Int = cards.size
}

data class UpcomingReviewsUiState(
    val isLoading: Boolean = true,
    val dueNowCount: Int = 0,
    val days: List<UpcomingReviewDay> = emptyList(),
    val laterCount: Int = 0,
    val totalScheduled: Int = 0,
) {
    val isEmpty: Boolean = !isLoading && dueNowCount == 0 && days.isEmpty() && laterCount == 0
}

class UpcomingReviewsViewModel(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val timeProvider: TimeProvider,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(UpcomingReviewsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val tree = contentRepository.getContentTree()
            val courseTitles = tree.courses.associate { it.course.courseId to it.course.title }
            val unitTitles =
                tree.courses.flatMap { course -> course.units }.associate { it.unit.unitId to it.unit.title }
            val nodes = contentRepository.getSchedulableNodes()
            val nodeById = nodes.associateBy { it.nodeId }
            val items = contentRepository.getItemsForNodes(nodes.map { it.nodeId })
            val statesByItem = progressRepository.getAllItemStates().associateBy { it.itemId }

            val sources =
                items.map { item ->
                    val node = nodeById[item.nodeId]
                    val state = statesByItem[item.itemId]
                    UpcomingReviewSource(
                        itemId = item.itemId,
                        stem = item.stem,
                        unitTitle = node?.let { unitTitles[it.unitId] }.orEmpty(),
                        courseTitle = node?.let { courseTitles[it.courseId] }.orEmpty(),
                        nextReviewAt = state?.nextReviewAt,
                        stage = state?.stage ?: 0,
                    )
                }

            _uiState.value = buildUpcomingReviewWeek(sources, timeProvider.now(), zoneId)
        }
    }
}

internal fun buildUpcomingReviewWeek(
    sources: List<UpcomingReviewSource>,
    now: Long,
    zoneId: ZoneId,
    daysAhead: Int = 7,
): UpcomingReviewsUiState {
    val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    val horizon = today.plusDays(daysAhead.toLong())

    val dueNow =
        sources.count { source ->
            source.nextReviewAt == null || source.stage == 0 || source.nextReviewAt <= now
        }

    val scheduled =
        sources.filter { source ->
            source.nextReviewAt != null && source.stage > 0 && source.nextReviewAt > now
        }

    val byDate =
        scheduled.groupBy { source ->
            Instant.ofEpochMilli(source.nextReviewAt!!).atZone(zoneId).toLocalDate()
        }

    val days =
        byDate
            .filterKeys { date -> !date.isAfter(horizon) }
            .toSortedMap()
            .map { (date, daySources) ->
                val cards =
                    daySources
                        .sortedBy { it.nextReviewAt }
                        .map { source ->
                            val time = Instant.ofEpochMilli(source.nextReviewAt!!).atZone(zoneId).toLocalTime()
                            UpcomingReviewCard(
                                itemId = source.itemId,
                                stem = source.stem,
                                unitTitle = source.unitTitle,
                                courseTitle = source.courseTitle,
                                timeLabel = "%02d:%02d".format(time.hour, time.minute),
                                stage = source.stage,
                            )
                        }
                val isToday = date == today
                val dayLabel =
                    when {
                        isToday -> "Hoy"
                        date == today.plusDays(1) -> "Mañana"
                        else ->
                            date.dayOfWeek
                                .getDisplayName(TextStyle.SHORT, SPANISH)
                                .replaceFirstChar { it.titlecase(SPANISH) }
                                .trimEnd('.') + " ${date.dayOfMonth}"
                    }
                val monthLabel = date.month.getDisplayName(TextStyle.SHORT, SPANISH).trimEnd('.')
                UpcomingReviewDay(
                    dayLabel = dayLabel,
                    dateLabel = "${date.dayOfMonth} $monthLabel",
                    isToday = isToday,
                    cards = cards,
                )
            }

    val laterCount = scheduled.count { Instant.ofEpochMilli(it.nextReviewAt!!).atZone(zoneId).toLocalDate().isAfter(horizon) }

    return UpcomingReviewsUiState(
        isLoading = false,
        dueNowCount = dueNow,
        days = days,
        laterCount = laterCount,
        totalScheduled = scheduled.size,
    )
}
