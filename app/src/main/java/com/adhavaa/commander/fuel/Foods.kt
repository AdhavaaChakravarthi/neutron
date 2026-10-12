package com.adhavaa.commander.fuel

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

/*
 * Foods, servings, nutrients and search. Pure Kotlin so it is unit-tested on the JVM.
 * The built-in foods are USDA SR Legacy (MODEL.md section 10), read from assets/foods.tsv.gz.
 */

/** What a food contains, for the diet filters. Ordered from least to most restrictive to eat. */
enum class Diet(val label: String) { PLANT("Plant"), DAIRY_EGG("Dairy or egg"), FISH("Fish"), MEAT("Meat or poultry") }

data class Serving(val label: String, val grams: Double)

data class Nutrients(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carb: Double = 0.0,
    val fiber: Double = 0.0,
) {
    operator fun plus(o: Nutrients) = Nutrients(kcal + o.kcal, protein + o.protein, fat + o.fat, carb + o.carb, fiber + o.fiber)
    operator fun times(k: Double) = Nutrients(kcal * k, protein * k, fat * k, carb * k, fiber * k)

    companion object {
        val ZERO = Nutrients()
    }
}

/**
 * A food per 100 g. [ref] is "usda:<id>" for USDA foods and "food:<id>" for your own foods and recipes.
 */
data class Food(
    val ref: String,
    val name: String,
    val group: String,
    val per100: Nutrients,
    val servings: List<Serving>,
    val diet: Diet,
    val recipe: Boolean = false,
) {
    val custom: Boolean get() = ref.startsWith("food:")
}

const val FRUIT_GROUP = "Fruits and Fruit Juices"
const val CUSTOM_GROUP = "Your foods"

fun nutrientsFor(food: Food, grams: Double): Nutrients = food.per100 * (grams / 100.0)

/** "cup=240|tbsp=15" ⇄ servings. Labels never contain "=" or "|" (the builder replaces them). */
fun parseServings(s: String): List<Serving> = s.split('|').mapNotNull { part ->
    val i = part.lastIndexOf('=')
    if (i <= 0) return@mapNotNull null
    val g = part.substring(i + 1).toDoubleOrNull() ?: return@mapNotNull null
    if (g > 0) Serving(part.substring(0, i), g) else null
}

fun formatServings(list: List<Serving>): String =
    list.joinToString("|") { "${it.label.replace('|', '/').replace('=', '-')}=${it.grams}" }

/** Every food can be logged in grams; servings follow. */
fun servingChoices(food: Food): List<Serving> = listOf(Serving("g", 1.0)) + food.servings

private val MEAT_GROUPS = setOf(
    "Beef Products", "Pork Products", "Lamb, Veal, and Game Products", "Poultry Products", "Sausages and Luncheon Meats",
)
private val MEAT_WORDS = Regex(
    "\\b(beef|pork|chicken|turkey|lamb|veal|mutton|goat|bacon|ham|sausages?|pepperoni|salami|duck|goose|venison|" +
        "bison|elk|moose|caribou|meat|meats|meatballs?|meatloaf|hamburgers?|cheeseburgers?|frankfurters?|hot dogs?|" +
        "bologna|lard|liver|chorizo|pastrami|corned|jerky|gelatin)\\b",
)
private val FISH_WORDS = Regex(
    "\\b(fish|salmon|tuna|cod|shrimp|crab|lobster|anchov(y|ies)|sardines?|mackerel|tilapia|catfish|clams?|oysters?|" +
        "mussels?|scallops?|squid|prawns?|herring|trout|haddock|pollock|halibut|seafood|crayfish|octopus|roe|caviar)\\b",
)
private val DAIRY_EGG_WORDS = Regex(
    "\\b(milk|cheese|cheeses|butter|cream|creamy|yogurt|yoghurt|eggs?|whey|ghee|paneer|curd|buttermilk|custard|" +
        "mayonnaise|casein|lactose|kefir)\\b",
)
private val PLANT_DAIRY = Regex(
    "\\b(peanut|almond|cashew|nut|nuts|cocoa|apple|soy|soymilk|coconut|oat|rice|shea|seed|sesame|sunflower)\\s+" +
        "(butter|milk|cream|beverage)\\b",
)

/**
 * Best guess at a USDA food's diet from its food group and name. Approximate: a "Fast foods" item whose name
 * doesn't mention meat counts as plant. The filters say so on screen (D-58).
 */
fun dietOf(group: String, name: String): Diet {
    val n = name.lowercase()
    if (group in MEAT_GROUPS || MEAT_WORDS.containsMatchIn(n)) return Diet.MEAT
    if (group == "Finfish and Shellfish Products" || FISH_WORDS.containsMatchIn(n)) return Diet.FISH
    if (group == "Dairy and Egg Products") return Diet.DAIRY_EGG
    if (DAIRY_EGG_WORDS.containsMatchIn(PLANT_DAIRY.replace(n, " "))) return Diet.DAIRY_EGG
    return Diet.PLANT
}

private fun num(s: String): Double = s.toDoubleOrNull() ?: 0.0

/** Parses foods.tsv (header first): id, group, name, kcal, protein, fat, carb, fiber, sugar, sodium, servings. */
fun parseCatalog(lines: Sequence<String>): List<Food> = lines.drop(1).mapNotNull { line ->
    val c = line.split('\t')
    if (c.size < 11) return@mapNotNull null
    Food(
        ref = "usda:${c[0]}",
        name = c[2],
        group = c[1],
        per100 = Nutrients(num(c[3]), num(c[4]), num(c[5]), num(c[6]), num(c[7])),
        servings = parseServings(c[10]),
        diet = dietOf(c[1], c[2]),
    )
}.toList()

/**
 * Reads foods.tsv whether or not it is gzipped, judged by the gzip magic bytes rather than the file name: the
 * Android build unpacks a ".gz" asset, so the APK holds plain foods.tsv while tests read the .gz source.
 */
fun readCatalog(input: InputStream): List<Food> {
    val buf = BufferedInputStream(input)
    buf.mark(2)
    val gz = buf.read() == 0x1f && buf.read() == 0x8b
    buf.reset()
    val stream: InputStream = if (gz) GZIPInputStream(buf) else buf
    return stream.bufferedReader().useLines { parseCatalog(it) }
}

/** Which foods search shows. Vegan hides meat, fish, dairy and egg; fruit-only shows fruit and your own foods. */
data class SearchFilter(val fishOnly: Boolean = false, val vegan: Boolean = false, val fruitOnly: Boolean = false) {
    fun allows(f: Food): Boolean = when {
        fruitOnly && f.group != FRUIT_GROUP && !f.custom -> false
        vegan && f.diet != Diet.PLANT -> false
        fishOnly && f.diet == Diet.MEAT -> false
        else -> true
    }
}

private val SPLIT = Regex("[^a-z0-9]+")

private fun words(s: String): List<String> = s.lowercase().split(SPLIT).filter { it.isNotEmpty() }

/** A food with its search words worked out once. */
class Indexed(val food: Food) {
    val all: List<String> = words(food.name)
    val head: List<String> = words(food.name.substringBefore(','))
}

fun index(foods: List<Food>): List<Indexed> = foods.map(::Indexed)

/**
 * Foods whose name has a word starting with every word you typed. Ranking: matches in the name's first part
 * (before the first comma) count double, a name that starts with your first word gets a bonus, your recent foods
 * come first, and shorter names win ties. An empty query lists your recent foods.
 */
fun searchFoods(
    query: String,
    foods: List<Indexed>,
    filter: SearchFilter,
    recent: List<String> = emptyList(),
    limit: Int = 60,
): List<Food> {
    val tokens = words(query)
    val recentRank = recent.withIndex().associate { (i, r) -> r to i }
    if (tokens.isEmpty()) {
        return foods.filter { it.food.ref in recentRank && filter.allows(it.food) }
            .sortedBy { recentRank.getValue(it.food.ref) }
            .take(limit)
            .map { it.food }
    }
    val scored = ArrayList<Pair<Double, Food>>()
    for (f in foods) {
        if (!filter.allows(f.food)) continue
        var score = 0.0
        var ok = true
        for (t in tokens) {
            when {
                f.head.any { it.startsWith(t) } -> score += 2.0
                f.all.any { it.startsWith(t) } -> score += 1.0
                else -> { ok = false; break }
            }
        }
        if (!ok) continue
        if (f.all.firstOrNull()?.startsWith(tokens[0]) == true) score += 3.0
        recentRank[f.food.ref]?.let { score += 5.0 - it * 0.01 }
        if (f.food.custom) score += 1.0
        score -= f.food.name.length / 200.0
        scored += score to f.food
    }
    return scored.sortedWith(compareByDescending<Pair<Double, Food>> { it.first }.thenBy { it.second.name }).take(limit).map { it.second }
}

/** One ingredient of a recipe. */
data class Ingredient(val food: Food, val grams: Double)

/**
 * A recipe as a food: the sum of its ingredients spread over the cooked weight (or the raw total when no cooked
 * weight is given). Its diet is the most restrictive of its ingredients'. A "serving" is the cooked weight divided
 * by the number of servings.
 */
fun recipeFood(id: Long, name: String, ingredients: List<Ingredient>, cookedG: Double?, servings: Int): Food {
    val total = ingredients.fold(Nutrients.ZERO) { acc, i -> acc + nutrientsFor(i.food, i.grams) }
    val rawG = ingredients.sumOf { it.grams }
    val yieldG = cookedG?.takeIf { it > 0 } ?: rawG
    val per100 = if (yieldG > 0) total * (100.0 / yieldG) else Nutrients.ZERO
    val list = buildList {
        if (servings > 0 && yieldG > 0) add(Serving(if (servings == 1) "whole recipe" else "serving (1/$servings)", yieldG / servings))
        if (servings > 1) add(Serving("whole recipe", yieldG))
    }
    return Food("food:$id", name, CUSTOM_GROUP, per100, list, ingredients.maxOfOrNull { it.food.diet } ?: Diet.PLANT, recipe = true)
}

/** "1.5 cup (360 g)", "120 g". */
fun amountLabel(qty: Double, serving: Serving): String {
    val q = if (qty == qty.roundToInt().toDouble()) qty.roundToInt().toString() else String.format(java.util.Locale.US, "%.2f", qty).trimEnd('0').trimEnd('.')
    return if (serving.grams == 1.0 && serving.label == "g") "$q g" else "$q ${serving.label} (${(qty * serving.grams).roundToInt()} g)"
}
