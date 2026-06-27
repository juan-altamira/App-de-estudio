package com.estudio.antiprocrastinacion.app.data.local.db

import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ProgressRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SnapshotRepository
import com.estudio.antiprocrastinacion.app.model.content.ManualItemEdit
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import com.estudio.antiprocrastinacion.app.model.json.SyncSnapshotDto
import com.estudio.antiprocrastinacion.app.ui.common.AppJson
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Escritor de contenido EXCLUSIVO para recuperación: hace upsert verbatim de los paquetes que se le
 * pasan, sin validación, sin eventos de import y SIN archivado. Lo implementa [LocalContentRepository].
 * Se usa para reinsertar contenido perdido sin pasar por el pipeline de import (que archivaría siblings).
 */
interface ContentRecoveryWriter {
    suspend fun restoreContentForRecovery(packages: List<ContentPackageDto>)
}

/** Resultado de una recuperación: qué cursos y cuántas tarjetas se restauraron. */
data class RecoveryReport(
    val recoveredCourseTitles: List<String>,
    val recoveredCardCount: Int,
)

/** Abstracción del respaldo/recuperación para no acoplar la UI a la implementación concreta. */
interface CourseRecovery {
    suspend fun backup(): Boolean

    suspend fun detectAndRecover(): RecoveryReport?
}

/** Abstracción del aviso persistente de recuperación. */
interface RecoveryNoticeAccess {
    fun setNotice(message: String, now: Long)

    fun activeNotice(): RecoveryNotice?

    fun acknowledge()
}

/**
 * Respaldo de recuperación de cursos y restauración selectiva tras un borrado.
 *
 * Diseño (rigor exigido por el usuario tras un incidente de borrado de datos):
 * - **Respaldo**: usa [SnapshotRepository.exportSyncSnapshot] (solo lectura) y escribe el JSON a un
 *   archivo en [backupDir], con historial (se conservan los últimos [maxBackups]). NO toca la base.
 *   Es intocable por tests: los tests instrumentados corren con base aislada (no generan contenido real)
 *   y el historial evita que una escritura borre respaldos previos.
 * - **Recuperación**: compara el último respaldo con la base actual y restaura **solo lo que falta**
 *   (cursos/tarjetas + su progreso SR + sus ediciones), con *upserts* aditivos. NUNCA llama
 *   `clearAllProgress` ni reescribe el progreso de lo que sí sobrevivió → cero efecto sobre la SR
 *   existente. Para una pérdida total, "todo falta" → se restaura todo exactamente como estaba.
 *
 * Todas las operaciones están envueltas en runCatching: un fallo de respaldo/recuperación jamás puede
 * tumbar la app ni afectar otra lógica.
 */
class CourseRecoveryStore(
    private val backupDir: File,
    private val snapshotRepository: SnapshotRepository,
    private val contentRepository: ContentRepository,
    private val contentRecoveryWriter: ContentRecoveryWriter,
    private val progressRepository: ProgressRepository,
    private val timeProvider: TimeProvider,
    private val json: Json = AppJson,
    private val maxBackups: Int = 20,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) : CourseRecovery {
    override suspend fun backup(): Boolean =
        runCatching {
            val snapshot = snapshotRepository.exportSyncSnapshot()
            // No respaldar un estado sin contenido (no pisar respaldos buenos con vacío).
            if (snapshot.recoveryPackages().all { it.nodes.isEmpty() }) return@runCatching false
            backupDir.mkdirs()
            val target = File(backupDir, "$BACKUP_PREFIX${timeProvider.now()}$BACKUP_SUFFIX")
            val tmp = File(backupDir, "${target.name}.tmp")
            tmp.writeText(json.encodeToString(snapshot))
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            prune()
            true
        }.getOrElse { error ->
            onError("backup", error)
            false
        }

    /**
     * Detecta si falta contenido respecto del último respaldo y, si falta, lo restaura selectivamente.
     * Devuelve el reporte de lo recuperado, o null si no había respaldo o no faltaba nada.
     */
    override suspend fun detectAndRecover(): RecoveryReport? =
        runCatching {
            val latest = latestBackup() ?: return@runCatching null
            recoverFrom(json.decodeFromString<SyncSnapshotDto>(latest.readText()))
        }.getOrElse { error ->
            onError("recover", error)
            null
        }

    private suspend fun recoverFrom(snapshot: SyncSnapshotDto): RecoveryReport? {
        val packages = snapshot.recoveryPackages()
        if (packages.isEmpty()) return null

        val tree = contentRepository.getContentTree()
        val curCourses = tree.courses.map { it.course.courseId }.toSet()
        val curUnits = tree.courses.flatMap { it.units }.map { it.unit.unitId }.toSet()
        val curOutcomes = tree.courses.flatMap { it.units }.flatMap { it.outcomes }.map { it.outcome.outcomeId }.toSet()
        val curNodes = tree.courses.flatMap { it.units }.flatMap { it.outcomes }.flatMap { it.nodes }.map { it.nodeId }.toSet()
        val curItems = contentRepository.getItemsForNodes(curNodes.toList(), includeArchived = true).map { it.itemId }.toSet()

        // Solo lo que falta de cada paquete (additive: nunca tocamos lo presente).
        val missingPackages =
            computeMissingPackages(
                packages = packages,
                currentCourseIds = curCourses,
                currentUnitIds = curUnits,
                currentOutcomeIds = curOutcomes,
                currentNodeIds = curNodes,
                currentItemIds = curItems,
            )
        if (missingPackages.isEmpty()) return null

        val missingNodeIds = missingPackages.flatMap { pkg -> pkg.nodes.map { it.nodeId } }.toSet()
        val missingItemIds = missingPackages.flatMap { pkg -> pkg.items.map { it.itemId } }.toSet()

        // 1) Contenido faltante (upsert verbatim, sin archivar ni validar).
        contentRecoveryWriter.restoreContentForRecovery(missingPackages)

        // 2) Progreso SR SOLO de lo restaurado (additive; no toca el progreso existente).
        val userState = snapshot.userStatePackage
        userState.nodeStates
            .filter { it.nodeId in missingNodeIds }
            .forEach { progressRepository.upsertNodeState(it.asDomain()) }
        val restoredItemStates = userState.itemStates.filter { it.itemId in missingItemIds }.map { it.asDomain() }
        if (restoredItemStates.isNotEmpty()) progressRepository.upsertItemStates(restoredItemStates)
        userState.nodeFormatStats
            .filter { it.nodeId in missingNodeIds }
            .groupBy { it.nodeId }
            .values
            .forEach { stats -> progressRepository.upsertFormatStats(stats.map { it.asDomain() }) }

        // 3) Ediciones (overrides) de las tarjetas restauradas → el usuario vuelve a ver la versión editada.
        userState.itemOverrides
            .filter { it.itemId in missingItemIds }
            .forEach { dto ->
                val override = dto.asDomain()
                contentRepository.setItemArchived(override.itemId, override.archived)
                if (override.stemOverride != null || override.correctAnswerOverride != null || override.optionsOverride != null) {
                    val base = contentRepository.getItem(override.itemId, includeArchived = true)
                    contentRepository.updateItemManualEdit(
                        ManualItemEdit(
                            itemId = override.itemId,
                            stem = override.stemOverride ?: base?.stem.orEmpty(),
                            correctAnswer = override.correctAnswerOverride ?: base?.correctAnswer.orEmpty(),
                            options = override.optionsOverride,
                        ),
                    )
                }
            }

        // 4) Archivado de los nodos restaurados, igual que en el respaldo.
        val archivedNodeIds = userState.archivedNodeIds.toSet()
        missingNodeIds.forEach { nodeId -> contentRepository.setArchivedCandidate(nodeId, nodeId in archivedNodeIds) }

        val coursesById = packages.flatMap { it.courses }.associateBy { it.courseId }
        val affectedCourseIds =
            (
                missingPackages.flatMap { pkg -> pkg.courses.map { it.courseId } } +
                    missingPackages.flatMap { pkg -> pkg.nodes.map { it.courseId } }
            ).toSet()
        val titles = affectedCourseIds.mapNotNull { coursesById[it]?.title }.distinct().sorted()
        return RecoveryReport(recoveredCourseTitles = titles, recoveredCardCount = missingItemIds.size)
    }

    private fun latestBackup(): File? = backupFiles().maxByOrNull { it.second }?.first

    private fun backupFiles(): List<Pair<File, Long>> =
        backupDir
            .listFiles { file -> file.isFile && file.name.startsWith(BACKUP_PREFIX) && file.name.endsWith(BACKUP_SUFFIX) }
            ?.mapNotNull { file ->
                file.name.removePrefix(BACKUP_PREFIX).removeSuffix(BACKUP_SUFFIX).toLongOrNull()?.let { file to it }
            }
            .orEmpty()

    private fun prune() {
        backupFiles()
            .sortedByDescending { it.second }
            .drop(maxBackups)
            .forEach { (file, _) -> runCatching { file.delete() } }
    }

    private fun SyncSnapshotDto.recoveryPackages(): List<ContentPackageDto> =
        contentPackages.ifEmpty { contentPackage?.let(::listOf).orEmpty() }

    companion object {
        private const val BACKUP_PREFIX = "recovery-"
        private const val BACKUP_SUFFIX = ".json"
    }
}

/**
 * Calcula, por cada paquete, SOLO las entidades que no están presentes actualmente (additive). Devuelve
 * los paquetes recortados a lo que falta y omite los que no tienen nada faltante. Pura → testeable.
 */
internal fun computeMissingPackages(
    packages: List<ContentPackageDto>,
    currentCourseIds: Set<String>,
    currentUnitIds: Set<String>,
    currentOutcomeIds: Set<String>,
    currentNodeIds: Set<String>,
    currentItemIds: Set<String>,
): List<ContentPackageDto> =
    packages.mapNotNull { pkg ->
        val courses = pkg.courses.filter { it.courseId !in currentCourseIds }
        val units = pkg.units.filter { it.unitId !in currentUnitIds }
        val outcomes = pkg.outcomes.filter { it.outcomeId !in currentOutcomeIds }
        val nodes = pkg.nodes.filter { it.nodeId !in currentNodeIds }
        val items = pkg.items.filter { it.itemId !in currentItemIds }
        if (courses.isEmpty() && units.isEmpty() && outcomes.isEmpty() && nodes.isEmpty() && items.isEmpty()) {
            null
        } else {
            pkg.copy(courses = courses, units = units, outcomes = outcomes, nodes = nodes, items = items)
        }
    }

/** Aviso persistente de que hubo un borrado y se recuperó contenido. Vive en un archivo aparte. */
@Serializable
data class RecoveryNotice(
    val message: String,
    val createdAt: Long,
)

/**
 * Persiste el aviso de recuperación en un archivo (intocable por la base). Se muestra hasta que el
 * usuario lo confirma ("OK"), momento en que se borra.
 */
class RecoveryNoticeStore(
    private val file: File,
    private val json: Json = AppJson,
) : RecoveryNoticeAccess {
    override fun setNotice(message: String, now: Long) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(RecoveryNotice(message = message, createdAt = now)))
        }
    }

    override fun activeNotice(): RecoveryNotice? =
        runCatching {
            if (!file.exists()) null else json.decodeFromString<RecoveryNotice>(file.readText())
        }.getOrNull()

    override fun acknowledge() {
        runCatching { if (file.exists()) file.delete() }
    }
}
