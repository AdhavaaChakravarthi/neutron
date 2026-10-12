package com.adhavaa.commander.fuel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class FoodsTest {
    private val header = "id\tgroup\tname\tkcal\tprotein\tfat\tcarb\tfiber\tsugar\tsodium\tservings"
    private val rows = listOf(
        "16070\tLegumes and Legume Products\tLentils, mature seeds, cooked, boiled, without salt\t116\t9.02\t0.38\t20.13\t7.9\t1.8\t2\tcup=198",
        "16069\tLegumes and Legume Products\tLentils, raw\t352\t24.63\t1.06\t63.35\t10.7\t2.03\t6\tcup=192",
        "9040\tFruits and Fruit Juices\tBananas, raw\t89\t1.09\t0.33\t22.84\t2.6\t12.23\t1\tmedium (7\" to 7-7/8\" long)=118|cup, sliced=150",
        "5006\tPoultry Products\tChicken, broilers or fryers, meat and skin, raw\t215\t18.6\t15.06\t0\t0\t0\t70\t",
        "1077\tDairy and Egg Products\tMilk, whole, 3.25% milkfat, with added vitamin D\t61\t3.15\t3.25\t4.8\t0\t5.05\t43\tcup=244",
        "15076\tFinfish and Shellfish Products\tFish, salmon, Atlantic, wild, raw\t142\t19.84\t6.34\t0\t0\t0\t44\tfillet=198",
        "20044\tCereal Grains and Pasta\tRice, white, long-grain, regular, enriched, cooked\t130\t2.69\t0.28\t28.17\t0.4\t0.05\t1\tcup=158",
        "broken line",
    )
    private val foods = parseCatalog((listOf(header) + rows).asSequence())
    private val idx = index(foods)

    @Test
    fun parsesRowsAndServings() {
        assertEquals(7, foods.size)
        val banana = foods.first { it.ref == "usda:9040" }
        assertEquals(89.0, banana.per100.kcal, 0.0)
        assertEquals(listOf(118.0, 150.0), banana.servings.map { it.grams })
        assertEquals("g", servingChoices(banana).first().label)
        assertTrue(foods.first { it.ref == "usda:5006" }.servings.isEmpty())
    }

    @Test
    fun nutrientsScaleWithGrams() {
        val lentils = foods.first { it.ref == "usda:16070" }
        val n = nutrientsFor(lentils, 198.0)
        assertEquals(229.68, n.kcal, 1e-9)
        assertEquals(17.8596, n.protein, 1e-9)
    }

    @Test
    fun dietComesFromGroupAndName() {
        assertEquals(Diet.DAIRY_EGG, dietOf("Dairy and Egg Products", "Butter, salted"))
        assertEquals(Diet.PLANT, dietOf("Legumes and Legume Products", "Peanut butter, smooth style, without salt"))
        assertEquals(Diet.PLANT, dietOf("Nut and Seed Products", "Nuts, coconut milk, raw"))
        assertEquals(Diet.MEAT, dietOf("Soups, Sauces, and Gravies", "Soup, chicken noodle, canned"))
        assertEquals(Diet.FISH, dietOf("Finfish and Shellfish Products", "Fish, salmon, Atlantic, wild, raw"))
        assertEquals(Diet.FISH, dietOf("Fast Foods", "Fast foods, shrimp, breaded and fried"))
        assertEquals(Diet.PLANT, dietOf("Vegetables and Vegetable Products", "Eggplant, raw"))
        assertEquals(Diet.PLANT, dietOf("Baked Products", "Crackers, graham, plain or honey"))
        assertEquals(Diet.DAIRY_EGG, dietOf("Baked Products", "Bread, egg"))
        assertEquals(Diet.MEAT, dietOf("Beef Products", "Beef, ground, 80% lean"))
    }

    @Test
    fun searchRanksNameMatchesFirst() {
        val r = searchFoods("lentil boiled", idx, SearchFilter())
        assertEquals("usda:16070", r.first().ref)
        assertEquals(1, r.size)
        assertEquals(listOf("usda:16069", "usda:16070"), searchFoods("lentils", idx, SearchFilter()).map { it.ref }.sorted())
        assertTrue(searchFoods("xyz", idx, SearchFilter()).isEmpty())
    }

    @Test
    fun filtersFollowYourPractices() {
        assertTrue(searchFoods("chicken", idx, SearchFilter()).isNotEmpty())
        assertTrue(searchFoods("chicken", idx, SearchFilter(fishOnly = true)).isEmpty())
        assertTrue(searchFoods("salmon", idx, SearchFilter(fishOnly = true)).isNotEmpty())
        assertTrue(searchFoods("salmon", idx, SearchFilter(vegan = true)).isEmpty())
        assertTrue(searchFoods("milk", idx, SearchFilter(vegan = true)).isEmpty())
        val fruit = searchFoods("ra", idx, SearchFilter(fruitOnly = true))
        assertEquals(listOf("usda:9040"), fruit.map { it.ref })
    }

    @Test
    fun recentFoodsComeFirstAndFillAnEmptySearch() {
        val recent = listOf("usda:20044", "usda:9040")
        assertEquals(recent, searchFoods("", idx, SearchFilter(), recent).map { it.ref })
        assertEquals("usda:16069", searchFoods("raw", idx, SearchFilter(), listOf("usda:16069")).first().ref)
        assertTrue(searchFoods("", idx, SearchFilter(fruitOnly = true), recent).all { it.group == FRUIT_GROUP })
    }

    @Test
    fun recipeSpreadsIngredientsOverCookedWeight() {
        val lentilsRaw = foods.first { it.ref == "usda:16069" }
        val milk = foods.first { it.ref == "usda:1077" }
        val r = recipeFood(5, "Dal", listOf(Ingredient(lentilsRaw, 200.0), Ingredient(milk, 100.0)), cookedG = 800.0, servings = 4)
        val total = 352.0 * 2 + 61.0
        assertEquals(total * 100 / 800, r.per100.kcal, 1e-9)
        assertEquals(200.0, r.servings.first().grams, 1e-9)
        assertEquals(800.0, r.servings.last().grams, 1e-9)
        assertEquals(Diet.DAIRY_EGG, r.diet)
        assertTrue(r.recipe && r.custom)
        val raw = recipeFood(6, "Dal", listOf(Ingredient(lentilsRaw, 200.0)), cookedG = null, servings = 1)
        assertEquals(352.0, raw.per100.kcal, 1e-9)
        assertEquals("whole recipe", raw.servings.single().label)
    }

    @Test
    fun servingsRoundTrip() {
        val s = listOf(Serving("cup, sliced", 150.0), Serving("piece", 30.5))
        assertEquals(s, parseServings(formatServings(s)))
        assertEquals("1.5 cup (360 g)", amountLabel(1.5, Serving("cup", 240.0)))
        assertEquals("120 g", amountLabel(120.0, Serving("g", 1.0)))
    }

    @Test
    fun readsTheListGzippedOrPlain() {
        val text = (listOf(header) + rows).joinToString("\n") + "\n"
        val plain = readCatalog(text.byteInputStream())
        val zipped = java.io.ByteArrayOutputStream().also { out -> java.util.zip.GZIPOutputStream(out).use { it.write(text.toByteArray()) } }
        assertEquals(foods, plain)
        assertEquals(foods, readCatalog(zipped.toByteArray().inputStream()))
    }

    /** The real bundled database: right size, right numbers, and search finds everyday foods. */
    @Test
    fun bundledUsdaDatabase() {
        val file = listOf(File("src/main/assets/foods.tsv.gz"), File("app/src/main/assets/foods.tsv.gz")).first { it.exists() }
        val all = GZIPInputStream(file.inputStream()).bufferedReader().useLines { parseCatalog(it) }
        assertTrue("${all.size} foods", all.size > 7000)
        val byName = all.associateBy { it.name }
        assertEquals(89.0, byName.getValue("Bananas, raw").per100.kcal, 0.0)
        assertEquals(116.0, byName.getValue("Lentils, mature seeds, cooked, boiled, without salt").per100.kcal, 0.0)
        val i = index(all)
        assertEquals("Bananas, raw", searchFoods("banana", i, SearchFilter()).first().name)
        assertNotNull(searchFoods("rice white cooked", i, SearchFilter()).firstOrNull { it.name.startsWith("Rice, white") })
        val noMeat = searchFoods("beef", i, SearchFilter(fishOnly = true), limit = 1000)
        assertFalse(noMeat.any { it.group == "Beef Products" })
        assertTrue(all.count { it.diet == Diet.MEAT } > 2000)
    }
}
