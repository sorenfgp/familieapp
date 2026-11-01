package dk.familieapp

import dk.familieapp.data.Ingredient
import dk.familieapp.data.RecipeImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlRecipeTest {
    /** Opbygget som Gourministeriet: ingredienser i afsnit under mellemoverskrifter, ingen JSON-LD. */
    private val gourministeriet = """
        <html><head><title>Bøf stroganoff med kartoffelmos - Gourministeriet</title>
        <meta property="og:image" content="https://gourministeriet.dk/billede.jpg">
        <script type="application/ld+json">{"@context":"https://schema.org","@graph":[{"@type":"Article","headline":"x"}]}</script>
        </head><body>
        <header><nav><ul><li>Opskrifter</li><li>Hovedretter</li></ul></nav></header>
        <article>
          <h1>Bøf stroganoff med kartoffelmos</h1>
          <p>Bøf stroganoff er en klassiker, som hele familien elsker, og her får du vores bedste udgave med cremet kartoffelmos ved siden af. Retten er til 4 personer.</p>
          <p>Klik &amp; bedøm på stjernerne nedenfor</p>
          <h3>Bøf stroganoff</h3>
          <p>600 g oksefilet<br>2 løg<br>250 g champignon<br>2 spsk smør<br>1 spsk paprika<br>2,5 dl fløde<br>Salt og peber</p>
          <h3>Kartoffelmos</h3>
          <ul><li>1 kg kartofler</li><li>1 dl mælk</li><li>50 g smør</li></ul>
          <h3>Sådan gør du</h3>
          <p>Skær kødet i strimler, og hak løgene fint.</p>
          <p>Brun kødet i smør på en varm pande, og tag det af igen.</p>
          <p><strong>Kartoffelmos</strong></p>
          <p>Kog kartoflerne møre, og mos dem med mælk og smør.</p>
          <h2>Flere opskrifter med oksekød</h2>
          <p>Prøv også vores lasagne, som er nem at lave.</p>
        </article>
        <div class="comments"><p>Lækkert!</p></div>
        </body></html>
    """.trimIndent()

    @Test
    fun readsRecipeFromPlainText() {
        val recipe = RecipeImporter.parseHtml(gourministeriet, "https://gourministeriet.dk/boef-stroganoff-med-kartoffelmos/")
        assertNotNull(recipe)
        recipe!!
        assertEquals("Bøf stroganoff med kartoffelmos", recipe.name)
        assertEquals(4, recipe.servings)
        assertEquals("https://gourministeriet.dk/billede.jpg", recipe.imageUrl)
        assertEquals(10, recipe.ingredients.size)
        assertEquals(Ingredient("Oksefilet", "600 g"), recipe.ingredients.first())
        assertEquals(Ingredient("Salt og peber", ""), recipe.ingredients[6])
        assertEquals(Ingredient("Smør", "50 g"), recipe.ingredients.last())
        assertEquals(
            listOf(
                "Skær kødet i strimler, og hak løgene fint.",
                "Brun kødet i smør på en varm pande, og tag det af igen.",
                "Kog kartoflerne møre, og mos dem med mælk og smør.",
            ),
            recipe.steps,
        )
    }

    @Test
    fun usesIngredientHeadingWhenPresent() {
        val html = """
            <body><h1>Pandekager</h1><h2>Ingredienser</h2><ul><li>3 æg</li><li>3 dl mælk</li><li>1,5 dl hvedemel</li></ul>
            <h2>Fremgangsmåde</h2><ol><li>Pisk æg og mælk sammen.</li><li>Pisk melet i, og bag pandekagerne.</li></ol></body>
        """.trimIndent()
        val recipe = RecipeImporter.parseHtml(html, "https://example.com/p")!!
        assertEquals(listOf("Æg", "Mælk", "Hvedemel"), recipe.ingredients.map { it.name })
        assertEquals(2, recipe.steps.size)
    }

    @Test
    fun readsWpRecipeMaker() {
        val html = """
            <body><h1>Suppe</h1><ul><li class="wprm-recipe-ingredient">1 l vand</li><li class="wprm-recipe-ingredient">2 gulerødder</li></ul>
            <div class="wprm-recipe-instruction-text">Kog det hele.</div></body>
        """.trimIndent()
        val recipe = RecipeImporter.parseHtml(html, "https://example.com/s")!!
        assertEquals(Ingredient("Vand", "1 l"), recipe.ingredients[0])
        assertEquals(listOf("Kog det hele."), recipe.steps)
    }

    @Test
    fun findsRecipeNestedInJsonLd() {
        val html = """<script type="application/ld+json">{"@type":"WebPage","mainEntity":{"@type":"Recipe","name":"Gryde","recipeIngredient":["1 løg"]}}</script>"""
        assertEquals("Gryde", RecipeImporter.parseHtml(html, "https://example.com/g")!!.name)
    }

    @Test
    fun givesUpOnPagesWithoutRecipe() {
        assertNull(RecipeImporter.parseHtml("<body><h1>Nyheder</h1><p>Intet her.</p></body>", "https://example.com"))
        assertTrue(true)
    }
}
