package com.adhavaa.commander.train

import com.adhavaa.commander.model.Equipment
import com.adhavaa.commander.model.Prescription
import com.adhavaa.commander.model.TrainParams
import java.io.BufferedReader
import java.io.InputStream

/*
 * Exercises (free-exercise-db, public domain) and Commander's program (MODEL.md section 11). Pure Kotlin.
 */

data class Exercise(
    val id: String,
    val name: String,
    val category: String,
    val equipment: String,
    val level: String,
    val primary: List<String>,
    val secondary: List<String>,
    val instructions: List<String>,
)

private fun list(s: String): List<String> = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** Parses exercises.tsv (header first). */
fun parseExercises(lines: Sequence<String>): List<Exercise> = lines.drop(1).mapNotNull { line ->
    val c = line.split('\t')
    if (c.size < 10) return@mapNotNull null
    Exercise(c[0], c[1], c[2], c[3], c[4], list(c[7]), list(c[8]), c[9].split(" ¶ ").map { it.trim() }.filter { it.isNotEmpty() })
}.toList()

fun readExercises(input: InputStream): List<Exercise> = input.bufferedReader().use { r: BufferedReader -> parseExercises(r.lineSequence()) }

/** The load step that applies to an exercise from the database's equipment field. */
fun equipmentOf(dbEquipment: String): Equipment = when (dbEquipment.lowercase()) {
    "barbell", "e-z curl bar" -> Equipment.BARBELL
    "dumbbell", "kettlebells" -> Equipment.DUMBBELL
    "cable" -> Equipment.CABLE
    "machine" -> Equipment.MACHINE
    else -> Equipment.BODY
}

/** Strength exercises that train the same primary muscle, for swapping one out (e.g. a machine is taken). */
fun alternatives(e: Exercise, all: List<Exercise>): List<Exercise> =
    all.filter { it.id != e.id && it.category in STRENGTH_CATEGORIES && it.primary.firstOrNull() == e.primary.firstOrNull() }
        .sortedWith(compareBy({ it.level != "beginner" }, { it.name }))

val STRENGTH_CATEGORIES = setOf("strength", "powerlifting")

/** One exercise in a day of the program. [timed]: reps are seconds (plank). */
data class ProgramItem(
    val slot: String,
    val exerciseId: String,
    val sets: Int,
    val lo: Int,
    val hi: Int,
    val large: Boolean,
    val main: Boolean,
    val timed: Boolean = false,
) {
    val restSeconds: Int get() = if (main) TrainParams.REST_MAIN_S else TrainParams.REST_ACCESSORY_S

    fun prescription(equipment: Equipment): Prescription = Prescription(sets, lo, hi, large, equipment)

    fun rangeLabel(): String = "$sets × $lo–$hi" + if (timed) " s" else ""
}

/** A lifting day. [day] is ISO (Monday = 1). */
data class LiftDay(val key: String, val label: String, val day: Int, val items: List<ProgramItem>)

/** The 4-day upper/lower program (MODEL.md 11.1, D-64), Thursday to Sunday (D-76). */
val PROGRAM: List<LiftDay> = listOf(
    LiftDay(
        "LOWER_A", "Lower A", 4,
        listOf(
            ProgramItem("LA1", "Barbell_Squat", 3, 6, 8, large = true, main = true),
            ProgramItem("LA2", "Romanian_Deadlift", 3, 8, 10, large = true, main = true),
            ProgramItem("LA3", "Lying_Leg_Curls", 2, 10, 12, large = false, main = false),
            ProgramItem("LA4", "Standing_Calf_Raises", 3, 10, 12, large = false, main = false),
            ProgramItem("LA5", "Plank", 3, 30, 45, large = false, main = false, timed = true),
        ),
    ),
    LiftDay(
        "UPPER_A", "Upper A", 5,
        listOf(
            ProgramItem("UA1", "Barbell_Bench_Press_-_Medium_Grip", 3, 6, 8, large = false, main = true),
            ProgramItem("UA2", "Bent_Over_Barbell_Row", 3, 8, 10, large = false, main = true),
            ProgramItem("UA3", "Dumbbell_Shoulder_Press", 2, 8, 10, large = false, main = false),
            ProgramItem("UA4", "Wide-Grip_Lat_Pulldown", 2, 10, 12, large = false, main = false),
            ProgramItem("UA5", "Triceps_Pushdown", 2, 10, 12, large = false, main = false),
            ProgramItem("UA6", "Barbell_Curl", 2, 10, 12, large = false, main = false),
        ),
    ),
    LiftDay(
        "LOWER_B", "Lower B", 6,
        listOf(
            ProgramItem("LB1", "Barbell_Deadlift", 2, 5, 6, large = true, main = true),
            ProgramItem("LB2", "Split_Squat_with_Dumbbells", 3, 8, 10, large = false, main = true),
            ProgramItem("LB3", "Leg_Extensions", 2, 10, 12, large = false, main = false),
            ProgramItem("LB4", "Seated_Calf_Raise", 3, 12, 15, large = false, main = false),
            ProgramItem("LB5", "Hanging_Leg_Raise", 2, 10, 12, large = false, main = false),
        ),
    ),
    LiftDay(
        "UPPER_B", "Upper B", 7,
        listOf(
            ProgramItem("UB1", "Pullups", 3, 6, 8, large = false, main = true),
            ProgramItem("UB2", "Incline_Dumbbell_Press", 3, 8, 10, large = false, main = true),
            ProgramItem("UB3", "Seated_Cable_Rows", 3, 10, 12, large = false, main = false),
            ProgramItem("UB4", "Side_Lateral_Raise", 2, 12, 15, large = false, main = false),
            ProgramItem("UB5", "Face_Pull", 2, 12, 15, large = false, main = false),
            ProgramItem("UB6", "Hammer_Curls", 2, 10, 12, large = false, main = false),
        ),
    ),
)

fun liftDayFor(isoDay: Int): LiftDay? = PROGRAM.firstOrNull { it.day == isoDay }

/** One timed hold in a mobility routine. */
data class Move(val exerciseId: String, val seconds: Int, val perSide: Boolean)

data class Routine(val key: String, val label: String, val moves: List<Move>) {
    /** Each hold in order, with its side ("Left", "Right" or null). */
    fun steps(): List<Pair<Move, String?>> = moves.flatMap { m -> if (m.perSide) listOf(m to "Left", m to "Right") else listOf(m to null) }

    val totalSeconds: Int get() = steps().sumOf { it.first.seconds }
}

/** Daily recovery stretch: static holds at the top of ACSM's 10-30 s (MODEL.md 11.4). */
val RECOVERY_STRETCH = Routine(
    "RECOVERY", "Recovery stretch",
    listOf(
        Move("Kneeling_Hip_Flexor", 30, perSide = true),
        Move("Hamstring_Stretch", 30, perSide = true),
        Move("Standing_Gastrocnemius_Calf_Stretch", 30, perSide = true),
        Move("Standing_Soleus_And_Achilles_Stretch", 30, perSide = true),
        Move("On_Your_Side_Quad_Stretch", 30, perSide = true),
        Move("Ankle_On_The_Knee", 30, perSide = true),
        Move("Chest_And_Front_Of_Shoulder_Stretch", 30, perSide = false),
        Move("One_Arm_Against_Wall", 30, perSide = true),
        Move("Childs_Pose", 30, perSide = false),
    ),
)

/** Dynamic warm-up before lifting or running (Behm et al. 2016). */
val WARM_UP = Routine(
    "WARM_UP", "Warm-up",
    listOf(
        Move("Arm_Circles", 30, perSide = false),
        Move("Standing_Hip_Circles", 30, perSide = true),
        Move("Front_Leg_Raises", 30, perSide = true),
        Move("Worlds_Greatest_Stretch", 30, perSide = true),
        Move("Inchworm", 30, perSide = false),
        Move("Dynamic_Chest_Stretch", 30, perSide = false),
    ),
)

val ROUTINES = listOf(RECOVERY_STRETCH, WARM_UP)

/** Every exercise id the program and routines use, so a test can check they all exist in the asset. */
val PROGRAM_EXERCISE_IDS: Set<String> =
    PROGRAM.flatMap { d -> d.items.map { it.exerciseId } }.toSet() + ROUTINES.flatMap { r -> r.moves.map { it.exerciseId } }
