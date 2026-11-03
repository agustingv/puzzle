package com.agustingv.puzzles.data

import java.text.Normalizer

/**
 * One piece of an avatar. [id] is what the server stores: lowercase letters only, never changed once
 * released, or players would lose the piece. A [prize] piece has to be won before it can be used.
 */
data class AvatarPiece(val id: String, val label: String, val emoji: String = "", val prize: Boolean = false)

/**
 * Every piece an avatar can have. The animals are the ones in the made up names, so that a new player's
 * avatar can be the animal in their name. Pieces can be added at the end of a list; removing or
 * renaming one would break the avatars that use it.
 */
object AvatarCatalog {
    val animals = listOf(
        AvatarPiece("zorro", "Zorro", "🦊"), AvatarPiece("buho", "Búho", "🦉"),
        AvatarPiece("loro", "Loro", "🦜"), AvatarPiece("oso", "Oso", "🐻"),
        AvatarPiece("tigre", "Tigre", "🐯"), AvatarPiece("leon", "León", "🦁"),
        AvatarPiece("lobo", "Lobo", "🐺"), AvatarPiece("delfin", "Delfín", "🐬"),
        AvatarPiece("halcon", "Halcón", "🦅"), AvatarPiece("panda", "Panda", "🐼"),
        AvatarPiece("koala", "Koala", "🐨"), AvatarPiece("erizo", "Erizo", "🦔"),
        AvatarPiece("castor", "Castor", "🦫"), AvatarPiece("gato", "Gato", "🐱"),
        AvatarPiece("pinguino", "Pingüino", "🐧"), AvatarPiece("mapache", "Mapache", "🦝"),
        AvatarPiece("ciervo", "Ciervo", "🦌"), AvatarPiece("flamenco", "Flamenco", "🦩"),
        AvatarPiece("gorrion", "Gorrión", "🐦"), AvatarPiece("pulpo", "Pulpo", "🐙"),
        AvatarPiece("ardilla", "Ardilla", "🐿️"), AvatarPiece("nutria", "Nutria", "🦦"),
        AvatarPiece("tortuga", "Tortuga", "🐢"), AvatarPiece("jirafa", "Jirafa", "🦒"),
        AvatarPiece("cebra", "Cebra", "🦓"), AvatarPiece("ballena", "Ballena", "🐳"),
        AvatarPiece("abeja", "Abeja", "🐝"), AvatarPiece("hormiga", "Hormiga", "🐜"),
        AvatarPiece("rana", "Rana", "🐸"), AvatarPiece("foca", "Foca", "🦭"),
        AvatarPiece("liebre", "Liebre", "🐇"), AvatarPiece("oveja", "Oveja", "🐑"),
        AvatarPiece("mariposa", "Mariposa", "🦋"), AvatarPiece("pantera", "Pantera", "🐆"),
        AvatarPiece("gallina", "Gallina", "🐔"), AvatarPiece("llama", "Llama", "🦙"),
    )

    /** Background colours, in the order of the theme's pastels (ui/common/AvatarTile.kt). */
    val colors = listOf(
        AvatarPiece("menta", "Menta"), AvatarPiece("cielo", "Cielo"), AvatarPiece("lila", "Lila"),
        AvatarPiece("rosa", "Rosa"), AvatarPiece("melocoton", "Melocotón"), AvatarPiece("turquesa", "Turquesa"),
        AvatarPiece("arena", "Arena"), AvatarPiece("lima", "Lima"), AvatarPiece("coral", "Coral"),
    )

    /** A few to start with; the prizes are to be won once there is a way to win them. */
    val accessories = listOf(
        AvatarPiece("lazo", "Lazo", "🎀"), AvatarPiece("flor", "Flor", "🌸"),
        AvatarPiece("estrella", "Estrella", "⭐"),
        AvatarPiece("corona", "Corona", "👑", prize = true), AvatarPiece("chistera", "Chistera", "🎩", prize = true),
        AvatarPiece("gafas", "Gafas", "🕶️", prize = true), AvatarPiece("trofeo", "Trofeo", "🏆", prize = true),
        AvatarPiece("birrete", "Birrete", "🎓", prize = true),
    )

    fun animal(id: String) = animals.find { it.id == id }
    fun color(id: String) = colors.find { it.id == id }
    fun accessory(id: String) = accessories.find { it.id == id }

    /** The animal a name starts with, ignoring case and accents: "Búho Sabio 42" is the owl. */
    fun animalInName(name: String): AvatarPiece? {
        val first = plain(name.trim().substringBefore(' '))
        return animals.find { plain(it.label) == first }
    }

    private fun plain(text: String) =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}

/** An avatar as the player put it together: an animal on a coloured tile, maybe with an accessory. */
data class Avatar(val animal: AvatarPiece, val color: AvatarPiece, val accessory: AvatarPiece? = null) {

    /** The form the server keeps: `animal:color` or `animal:color:accessory`. */
    fun encode(): String = listOfNotNull(animal.id, color.id, accessory?.id).joinToString(":")

    companion object {
        /** Null for anything that is not an avatar of the pieces this version knows. */
        fun decode(text: String): Avatar? {
            val parts = text.split(":")
            if (parts.size !in 2..3) return null
            val animal = AvatarCatalog.animal(parts[0]) ?: return null
            val color = AvatarCatalog.color(parts[1]) ?: return null
            val accessory = parts.getOrNull(2)?.let { AvatarCatalog.accessory(it) ?: return null }
            return Avatar(animal, color, accessory)
        }

        /**
         * A new player's avatar: the animal in their name, on a colour picked by their id. A name with
         * no animal of the catalog, like one typed by the player, gets one picked by the id too.
         */
        fun initial(name: String, id: String): Avatar {
            val seed = id.hashCode().toLong() and 0xffffffffL
            val animal = AvatarCatalog.animalInName(name)
                ?: AvatarCatalog.animals[((seed / 7) % AvatarCatalog.animals.size).toInt()]
            return Avatar(animal, AvatarCatalog.colors[(seed % AvatarCatalog.colors.size).toInt()])
        }
    }
}

/**
 * The prize pieces this phone has won. Nothing wins them yet: this is where prizes will be recorded,
 * by [win], once the rules for them are decided.
 */
class AvatarPrizes(private val store: KeyValueStore) {

    private val won: Set<String>
        get() = store.get(KEY).orEmpty().split(",").filter { it.isNotEmpty() }.toSet()

    fun has(piece: AvatarPiece): Boolean = !piece.prize || piece.id in won

    fun win(piece: AvatarPiece) {
        if (!has(piece)) store.put(KEY, (won + piece.id).sorted().joinToString(","))
    }

    private companion object {
        const val KEY = "avatar/won"
    }
}
