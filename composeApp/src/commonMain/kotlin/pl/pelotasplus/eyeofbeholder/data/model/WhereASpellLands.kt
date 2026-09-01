package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a spell does where it comes down.
 *
 * On the party's own square and nowhere else. A spell crossing the room is a
 * picture and nothing more: it does not touch what it passes over, and asking
 * it what it has hit before it arrives is asking too early.
 *
 * Whom it reaches is not the same question as whom a blow reaches. An arm
 * comes down on whoever is nearest the side it swings from; a spell starts
 * counting at a champion picked at random and works round the party from
 * there, so the front rank are no likelier to take it than the back.
 */
class WhereASpellLands(private val dice: Dice = Dice.random) {

    /** The world afterwards, and what has to be said about it. */
    data class Landed(
        val world: GameState,
        /** Whoever lost hit points, so the numbers go up on the portraits. */
        val hurt: List<PartySlot> = emptyList(),
        /** And whatever took hold, which is only what took: a save leaves nothing. */
        val left: List<Left> = emptyList(),
    )

    data class Left(val whose: PartySlot, val what: WhatABlowLeaves)

    fun of(spell: MonsterSpell, world: GameState): Landed = when (spell) {
        MonsterSpell.HOLD_PERSON, MonsterSpell.HOLD_MONSTER -> held(world)
        MonsterSpell.MONSTER_DEATH_SPELL -> struckDead(world)
        MonsterSpell.MONSTER_DISINTEGRATE -> disintegrated(world)
        MonsterSpell.MONSTER_CAUSE_CRITICAL_WOUNDS -> wounded(world)
        MonsterSpell.MONSTER_FLESH_TO_STONE -> turnedToStone(world)

        // The ones that only hurt, which want the damage table, and the ones
        // nothing in the game casts at the party.
        else -> Landed(world)
    }

    /**
     * A quarter of the party held where they stand, or as many of them as are
     * on their feet.
     *
     * The count is rolled before anybody is looked at, so a party with three
     * standing can be told to hold four and hold three.
     */
    private fun held(world: GameState): Landed {
        var after = world
        val left = mutableListOf<Left>()
        val many = dice.roll(1, HOW_MANY, 0)

        inTheOrderItFinds(world).take(many).forEach { whose ->
            after = after.championLeftWith(
                whose = whose,
                what = WhatABlowLeaves.PARALYSIS,
                dice = dice,
                against = SavingThrow.A_SPELL,
            )?.also { left += Left(whose, WhatABlowLeaves.PARALYSIS) } ?: after
        }

        return Landed(after, left = left)
    }

    /**
     * A quarter of the party killed outright, and only those with some way to
     * go: a champion of the eighth level or better is passed over, and passing
     * over one does not use up the spell's count.
     */
    private fun struckDead(world: GameState): Landed {
        var after = world
        val hurt = mutableListOf<PartySlot>()
        var many = dice.roll(1, HOW_MANY, 0)

        inTheOrderItFinds(world).forEach { whose ->
            if (many == 0) return@forEach
            val who = after.championIn(whose) ?: return@forEach
            if ((who.levels.firstOrNull()?.level ?: 1) >= TOO_SEASONED_TO_DIE) return@forEach

            after = after.championHurt(whose, ENOUGH_TO_KILL)
            hurt += whose
            many--
        }

        return Landed(after, hurt)
    }

    /**
     * The one champion it happens upon destroyed, unless they shrug it off.
     *
     * It asks only that one. A party who watch somebody go this way have no
     * reason to think the next of them was ever in danger.
     */
    private fun disintegrated(world: GameState): Landed {
        val whose = inTheOrderItFinds(world).firstOrNull() ?: return Landed(world)
        val who = world.championIn(whose) ?: return Landed(world)
        if (who.saves(SavingThrow.A_SPELL, dice)) return Landed(world)

        return Landed(world.championHurt(whose, ENOUGH_TO_KILL), listOf(whose))
    }

    /** One champion badly hurt, with nothing to be thrown against it. */
    private fun wounded(world: GameState): Landed {
        val whose = inTheOrderItFinds(world).firstOrNull() ?: return Landed(world)
        val taken = Damage(dice.roll(WOUNDS_TIMES, WOUNDS_PIPS, WOUNDS_BASE))

        return Landed(world.championHurt(whose, taken), listOf(whose))
    }

    /**
     * The first champion who fails the throw turned to stone.
     *
     * This one walks the party rather than asking a single champion, and stops
     * at the first it takes — so a party of six is not six chances to escape
     * it, only six chances for it to find somebody. Stone is on no clock and
     * no rest lifts it.
     */
    private fun turnedToStone(world: GameState): Landed {
        inTheOrderItFinds(world).forEach { whose ->
            val after = world.championLeftWith(
                whose = whose,
                what = WhatABlowLeaves.PETRIFICATION,
                dice = dice,
                against = SavingThrow.A_SPELL,
            ) ?: return@forEach

            return Landed(after, left = listOf(Left(whose, WhatABlowLeaves.PETRIFICATION)))
        }

        return Landed(world)
    }

    /**
     * The party in the order a spell finds them: from a champion picked at
     * random, round to the left, skipping whoever is past being hurt.
     */
    private fun inTheOrderItFinds(world: GameState): List<PartySlot> {
        val from = dice.roll(1, ALL_OF_THEM, -1)

        return (0 until ALL_OF_THEM)
            .map { PartySlot((from + it) % ALL_OF_THEM) }
            .filter { world.championIn(it)?.canBeHurt == true }
    }

    private companion object {
        /** How many champions there are to work round. */
        const val ALL_OF_THEM = 6

        /** How many a spell that takes several of them takes: one die of four. */
        const val HOW_MANY = 4

        /** What the death spell passes over, being no challenge to it. */
        const val TOO_SEASONED_TO_DIE = 8

        /** Three hundred, which is more than anybody has. */
        val ENOUGH_TO_KILL = Damage(300)

        const val WOUNDS_TIMES = 3
        const val WOUNDS_PIPS = 8
        const val WOUNDS_BASE = 3
    }
}
