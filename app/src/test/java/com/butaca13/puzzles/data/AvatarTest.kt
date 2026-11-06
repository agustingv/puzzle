package com.butaca13.puzzles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.random.Random

class AvatarTest {

    /** The server's rule for each part of an avatar (puzzles-api, Player::AVATAR_PATTERN). */
    private val serverPart = Regex("^[a-z]{1,16}$")

    @Test
    fun `every piece has an id the server takes, unique in its list`() {
        for (list in listOf(AvatarCatalog.animals, AvatarCatalog.colors, AvatarCatalog.accessories)) {
            assertEquals(list.size, list.map { it.id }.toSet().size)
            for (piece in list) assertTrue(piece.id, serverPart.matches(piece.id))
        }
    }

    @Test
    fun `animals and accessories have a picture, and no two animals the same one`() {
        for (piece in AvatarCatalog.animals + AvatarCatalog.accessories) assertTrue(piece.id, piece.emoji.isNotEmpty())
        assertEquals(AvatarCatalog.animals.size, AvatarCatalog.animals.map { it.emoji }.toSet().size)
    }

    @Test
    fun `there is a pastel for every colour`() {
        // ui/theme/Theme.kt has nine GardenBeds; AvatarTile picks the colour by its place in the list.
        assertEquals(9, AvatarCatalog.colors.size)
    }

    @Test
    fun `every made up name has its animal`() {
        for (name in PlayerNames.all()) assertNotNull(name, AvatarCatalog.animalInName(name))
        val animals = PlayerNames.all().map { AvatarCatalog.animalInName(it) }.toSet()
        assertEquals("every animal is in some name", AvatarCatalog.animals.toSet(), animals)
    }

    @Test
    fun `the animal in a name is found whatever the case and accents`() {
        assertEquals("buho", AvatarCatalog.animalInName("Búho Sabio 42")?.id)
        assertEquals("buho", AvatarCatalog.animalInName("buho")?.id)
        assertEquals("pinguino", AvatarCatalog.animalInName("  PINGÜINO feliz")?.id)
        assertNull(AvatarCatalog.animalInName("Ana"))
        assertNull(AvatarCatalog.animalInName("Lince Veloz 3"))
    }

    @Test
    fun `an avatar goes to the server and back unchanged`() {
        val plain = Avatar(AvatarCatalog.animals[1], AvatarCatalog.colors[2])
        val dressed = plain.copy(accessory = AvatarCatalog.accessories[0])

        assertEquals("buho:lila", plain.encode())
        assertEquals("buho:lila:lazo", dressed.encode())
        assertEquals(plain, Avatar.decode(plain.encode()))
        assertEquals(dressed, Avatar.decode(dressed.encode()))
    }

    @Test
    fun `anything else is not an avatar`() {
        for (bad in listOf("", "buho", "buho:", "buho:lila:", "buho:lila:lazo:extra", "lince:lila", "buho:negro", "buho:lila:capa", "Buho:lila")) {
            assertNull(bad, Avatar.decode(bad))
        }
    }

    @Test
    fun `a new player's avatar comes from their name and id, always the same, with no accessory`() {
        val id = UUID(Random(1).nextLong(), Random(2).nextLong()).toString()

        val avatar = Avatar.initial("Ardilla Astuta 7", id)

        assertEquals("ardilla", avatar.animal.id)
        assertNull(avatar.accessory)
        assertEquals(avatar, Avatar.initial("Ardilla Astuta 7", id))
    }

    @Test
    fun `a name without an animal still gets one, and ids spread over the colours and animals`() {
        val avatars = (0 until 500).map { Avatar.initial("Ana", UUID(Random(it).nextLong(), Random(it + 1000).nextLong()).toString()) }

        assertEquals(AvatarCatalog.colors.toSet(), avatars.map { it.color }.toSet())
        assertTrue(avatars.map { it.animal }.toSet().size > AvatarCatalog.animals.size / 2)
    }

    @Test
    fun `prize pieces wait to be won, and once won they stay`() {
        val store = object : KeyValueStore {
            val values = mutableMapOf<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String) {
                values[key] = value
            }
            override fun remove(key: String) {
                values.remove(key)
            }
        }
        val prizes = AvatarPrizes(store)
        val crown = AvatarCatalog.accessory("corona")!!
        val bow = AvatarCatalog.accessory("lazo")!!

        assertTrue(crown.prize)
        assertFalse(prizes.has(crown))
        assertTrue(prizes.has(bow))

        prizes.win(crown)
        prizes.win(crown)

        assertTrue(AvatarPrizes(store).has(crown))
        assertEquals("corona", store.values["avatar/won"])
    }

    @Test
    fun `there are free accessories to start with, and prizes to come`() {
        assertTrue(AvatarCatalog.accessories.any { !it.prize })
        assertTrue(AvatarCatalog.accessories.any { it.prize })
    }
}
