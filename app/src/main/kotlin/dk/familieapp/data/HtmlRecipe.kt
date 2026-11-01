package dk.familieapp.data

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Opskrifter uden schema.org/Recipe som JSON-LD. Prøver i rækkefølge:
 * microdata (itemprop), kendte WordPress-plugins (WP Recipe Maker, Tasty Recipes)
 * og til sidst sidens tekst: ingredienslister før en overskrift som "Sådan gør du",
 * og trinene efter den. Det sidste passer til fx Gourministeriet.
 */
object HtmlRecipe {
    private val methodHeadings = setOf(
        "sådan gør du", "sådan gør man", "fremgangsmåde", "fremgangsmaade", "fremgangsmåden",
        "tilberedning", "opskrift", "method", "instructions", "directions", "preparation",
    )
    private val ingredientHeadings = setOf(
        "ingredienser", "ingredienserne", "du skal bruge", "det skal du bruge", "ingredients",
    )

    /** Overskrifter, hvor fremgangsmåden er slut. */
    private val stopWords = listOf(
        "tip", "tips", "video", "kommentar", "relaterede", "flere opskrifter", "del opskrift", "del dette",
        "næringsindhold", "næringsværdi", "bedøm", "anmeld", "nyhedsbrev", "læs også", "se også", "du vil måske",
        "andre opskrifter", "lignende", "om forfatteren", "comments", "related", "you may also",
    )

    /** Dele af siden, der aldrig er en del af opskriften. */
    private const val SKIP =
        "script, style, noscript, nav, header, footer, aside, form, iframe, svg, button, " +
            "[class*=comment], [id*=comment], [class*=related], [class*=share], [class*=newsletter], " +
            "[class*=breadcrumb], [class*=menu], [class*=sidebar], [class*=widget], [class*=cookie]"

    fun parse(doc: Document, url: String): Recipe? {
        val name = title(doc)
        val (ingredients, steps) = microdata(doc) ?: plugins(doc) ?: fromText(doc) ?: return null
        if (ingredients.isEmpty() && steps.isEmpty()) return null
        val all = doc.body()?.text().orEmpty()
        return Recipe(
            name = name,
            minutes = minutes(all),
            servings = Regex("""(\d+)\s*(?:personer|pers\.?|portioner|servings)""", RegexOption.IGNORE_CASE)
                .find(all)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..30 } ?: 4,
            ingredients = ingredients.map { RecipeImporter.splitIngredient(it) },
            steps = steps,
            sourceUrl = url,
            kind = RecipeImporter.guessKindFromText(name),
            imageUrl = doc.selectFirst("meta[property=og:image]")?.attr("abs:content").orEmpty(),
        )
    }

    private fun title(doc: Document): String {
        val h1 = doc.selectFirst("h1")?.text()?.trim()
        val og = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
        val raw = h1?.takeIf { it.isNotBlank() } ?: og?.takeIf { it.isNotBlank() } ?: doc.title()
        // "Bøf stroganoff med kartoffelmos - Gourministeriet" → kun opskriftens navn.
        return raw.split(" - ", " | ", " – ").first().trim()
    }

    private fun minutes(text: String): Int {
        val hours = Regex("""(\d+)\s*(?:timer|time|t\.)\b""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()
        val mins = Regex("""(?:tid|tilberedningstid|i alt)[^\d]{0,15}(\d+)\s*min""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
        return mins ?: hours?.takeIf { it in 1..12 }?.times(60) ?: 0
    }

    private fun lines(elements: List<Element>): List<String> =
        elements.map { RecipeImporter.clean(it.html().replace(Regex("<br\\s*/?>"), " ")) }.filter { it.isNotBlank() }

    private fun microdata(doc: Document): Pair<List<String>, List<String>>? {
        val ingredients = lines(doc.select("[itemprop=recipeIngredient], [itemprop=ingredients]"))
        if (ingredients.isEmpty()) return null
        val instructions = doc.select("[itemprop=recipeInstructions]")
        val steps = instructions.flatMap { el ->
            val items = el.select("li, p").filter { it.select("li, p").size <= 1 }
            if (items.isEmpty()) listOf(el.text()) else lines(items)
        }.filter { it.isNotBlank() }
        return ingredients to steps
    }

    private fun plugins(doc: Document): Pair<List<String>, List<String>>? {
        val wprm = lines(doc.select(".wprm-recipe-ingredient"))
        if (wprm.isNotEmpty()) return wprm to lines(doc.select(".wprm-recipe-instruction-text"))
        val tasty = lines(doc.select(".tasty-recipes-ingredients li"))
        if (tasty.isNotEmpty()) return tasty to lines(doc.select(".tasty-recipes-instructions li, .tasty-recipes-instructions p"))
        val mv = lines(doc.select(".mv-create-ingredients li"))
        if (mv.isNotEmpty()) return mv to lines(doc.select(".mv-create-instructions li, .mv-create-instructions p"))
        return null
    }

    private enum class Kind { HEADING, ITEM, LINE }

    /** Et stykke tekst på siden i læserækkefølge. [major] er h1–h2. */
    private data class Block(val kind: Kind, val text: String, val major: Boolean = false)

    private fun normalize(text: String) =
        text.lowercase().trim().trimEnd(':', '.', ' ').replace(Regex("\\s+"), " ")

    private fun isMethodHeading(text: String) = normalize(text) in methodHeadings
    private fun isIngredientHeading(text: String) = normalize(text) in ingredientHeadings

    /** Siden som en flad liste af overskrifter, listepunkter og tekstlinjer. */
    private fun blocks(doc: Document): List<Block> {
        val root = doc.selectFirst("article, main, [class*=entry-content], [class*=post-content]") ?: doc.body() ?: return emptyList()
        val copy = root.clone()
        copy.select(SKIP).remove()
        val out = mutableListOf<Block>()
        fun visit(el: Element) {
            val tag = el.tagName()
            when {
                tag.matches(Regex("h[1-6]")) -> {
                    val t = el.text().trim()
                    if (t.isNotEmpty()) out += Block(Kind.HEADING, t, major = tag == "h1" || tag == "h2")
                }
                tag == "li" && el.select("li").size == 1 -> {
                    val t = el.text().trim()
                    if (t.isNotEmpty()) out += Block(Kind.ITEM, t)
                }
                tag == "p" || (tag == "div" && el.children().none { it.isBlock }) -> {
                    // Et afsnit med kun fed tekst er en mellemoverskrift.
                    val strong = el.select("strong, b").text().trim()
                    val all = el.text().trim()
                    if (all.isEmpty()) return
                    if (strong.isNotEmpty() && strong == all && all.length < 60) {
                        out += Block(Kind.HEADING, all)
                        return
                    }
                    splitLines(el).forEach { line ->
                        val heading = isMethodHeading(line) || isIngredientHeading(line)
                        out += Block(if (heading) Kind.HEADING else Kind.LINE, line)
                    }
                }
                else -> el.children().forEach { visit(it) }
            }
        }
        visit(copy)
        return out
    }

    /** Tekst delt ved <br>, så hver ingredienslinje bliver for sig. */
    private fun splitLines(el: Element): List<String> {
        val parts = mutableListOf(StringBuilder())
        fun walk(node: Node) {
            when (node) {
                is TextNode -> parts.last().append(node.text())
                is Element -> if (node.tagName() == "br") parts += StringBuilder() else node.childNodes().forEach { walk(it) }
            }
        }
        el.childNodes().forEach { walk(it) }
        return parts.map { it.toString().replace(' ', ' ').trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }
    }

    private fun fromText(doc: Document): Pair<List<String>, List<String>>? {
        val blocks = blocks(doc)
        val method = blocks.indexOfFirst { it.kind == Kind.HEADING && isMethodHeading(it.text) }
        if (method < 0) return null

        // Trinene: alt efter overskriften indtil en stor overskrift eller noget, der ikke er opskriften.
        val steps = mutableListOf<String>()
        for (b in blocks.drop(method + 1)) {
            if (b.kind == Kind.HEADING) {
                val t = normalize(b.text)
                if (b.major || stopWords.any { t.startsWith(it) } || isIngredientHeading(b.text)) break
                continue // mellemoverskrift i fremgangsmåden, fx "Kartoffelmos"
            }
            if (stopWords.any { normalize(b.text).startsWith(it) }) break
            if (b.text.length >= 8) steps += b.text
            if (steps.size >= 40) break
        }

        // Ingredienserne: efter en "Ingredienser"-overskrift, ellers baglæns fra fremgangsmåden
        // indtil sidens almindelige tekst (lange afsnit) begynder.
        val start = blocks.subList(0, method).indexOfLast { it.kind == Kind.HEADING && isIngredientHeading(it.text) }
        val ingredients = if (start >= 0) {
            blocks.subList(start + 1, method).filter { it.kind != Kind.HEADING && looksLikeIngredient(it.text) }.map { it.text }
        } else {
            val found = mutableListOf<String>()
            for (b in blocks.subList(0, method).asReversed()) {
                if (b.kind == Kind.HEADING) {
                    if (b.major) break else continue
                }
                if (!looksLikeIngredient(b.text)) {
                    if (found.isNotEmpty()) break else continue
                }
                found += b.text
            }
            found.reversed()
        }
        if (ingredients.isEmpty() && steps.isEmpty()) return null
        return ingredients to steps
    }

    private val junk = listOf("klik", "bedøm", "print", "udskriv", "gem opskrift", "pin ", "se video", "hop til", "jump to", "annonce")

    /** Kort linje uden punktum til sidst, fx "400 g brune svampe" eller "Salt og peber". */
    private fun looksLikeIngredient(text: String): Boolean {
        if (text.length > 90) return false
        if (junk.any { normalize(text).startsWith(it) }) return false
        if (text.trimEnd().endsWith(".") && !Regex("""^\s*[\d½¼¾]""").containsMatchIn(text)) return false
        return text.split(' ').size <= 12
    }
}
