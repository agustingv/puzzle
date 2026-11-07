package com.butaca13.puzzles.data

import java.text.Normalizer

/**
 * Where an accessory goes on the avatar. One accessory per place: a free one in the corner, and the
 * prizes on the head, the face, at the side or pinned on the other side, so that they can be worn
 * together.
 */
enum class AccessorySlot { CORNER, HEAD, FACE, HAND, CHEST }

/**
 * One piece of an avatar. [id] is what the server stores: lowercase letters only, never changed once
 * released, or players would lose the piece. A [prize] piece has to be won before it can be used. An
 * accessory has its [slot].
 */
data class AvatarPiece(
    val id: String,
    val label: String,
    val emoji: String = "",
    val prize: Boolean = false,
    val slot: AccessorySlot? = null,
)

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

    /** A few to start with, and the prizes, won by playing (AvatarPrizes). */
    val accessories = listOf(
        AvatarPiece("lazo", "Lazo", "🎀", slot = AccessorySlot.CORNER),
        AvatarPiece("flor", "Flor", "🌸", slot = AccessorySlot.CORNER),
        AvatarPiece("estrella", "Estrella", "⭐", slot = AccessorySlot.CORNER),
        AvatarPiece("corona", "Corona", "👑", prize = true, slot = AccessorySlot.HEAD),
        AvatarPiece("chistera", "Chistera", "🎩", prize = true, slot = AccessorySlot.HEAD),
        AvatarPiece("gafas", "Gafas", "🕶️", prize = true, slot = AccessorySlot.FACE),
        AvatarPiece("trofeo", "Trofeo", "🏆", prize = true, slot = AccessorySlot.HAND),
        AvatarPiece("birrete", "Birrete", "🎓", prize = true, slot = AccessorySlot.HEAD),
        AvatarPiece("medalla", "Medalla", "🎖️", prize = true, slot = AccessorySlot.CHEST),
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

/**
 * An avatar as the player put it together: an animal on a coloured tile, with up to one accessory in
 * each [AccessorySlot]. [accessories] are kept in the catalog's order, whatever order they were put on.
 */
class Avatar(val animal: AvatarPiece, val color: AvatarPiece, accessories: List<AvatarPiece> = emptyList()) {

    val accessories: List<AvatarPiece> = accessories.distinct().sortedBy(AvatarCatalog.accessories::indexOf)

    init {
        require(accessories.all { it.slot != null }) { "not an accessory: $accessories" }
        require(this.accessories.groupBy { it.slot }.all { it.value.size == 1 }) { "two in a slot: $accessories" }
    }

    fun wears(piece: AvatarPiece): Boolean = piece in accessories

    /** The accessory in [slot], if any. */
    fun accessoryIn(slot: AccessorySlot): AvatarPiece? = accessories.find { it.slot == slot }

    /** Wearing [piece] too, in place of whatever was in its slot. */
    fun wear(piece: AvatarPiece): Avatar = copy(accessories = accessories.filter { it.slot != piece.slot } + piece)

    fun takeOff(piece: AvatarPiece): Avatar = copy(accessories = accessories - piece)

    fun copy(
        animal: AvatarPiece = this.animal,
        color: AvatarPiece = this.color,
        accessories: List<AvatarPiece> = this.accessories,
    ) = Avatar(animal, color, accessories)

    /** The form the server keeps: `animal:color`, then each accessory, as `animal:color:accessory:accessory`. */
    fun encode(): String = (listOf(animal.id, color.id) + accessories.map { it.id }).joinToString(":")

    override fun equals(other: Any?): Boolean =
        other is Avatar && animal == other.animal && color == other.color && accessories == other.accessories

    override fun hashCode(): Int = encode().hashCode()

    override fun toString(): String = "Avatar(${encode()})"

    companion object {
        /** Null for anything that is not an avatar of the pieces this version knows. */
        fun decode(text: String): Avatar? {
            val parts = text.split(":")
            if (parts.size < 2 || parts.size > 2 + AccessorySlot.entries.size) return null
            val animal = AvatarCatalog.animal(parts[0]) ?: return null
            val color = AvatarCatalog.color(parts[1]) ?: return null
            val accessories = parts.drop(2).map { AvatarCatalog.accessory(it) ?: return null }
            // Each in its own slot, and once.
            if (accessories.map { it.slot }.toSet().size != accessories.size) return null
            return Avatar(animal, color, accessories)
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
