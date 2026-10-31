package pl.pelotasplus.eyeofbeholder.data.model

/** What a spell that wounds does to the creature it reaches. */
sealed interface Wounding {

    /** Laid on by hand from the front rank, for [dice]: the three cause wounds. */
    data class ByTouch(val dice: DamageDice) : Wounding

    /** Laid on by hand, and leaving it a few hit points short of nothing: harm. */
    data object AllButAFew : Wounding

    /** Loosed at a creature up to two squares off, and killing it outright: slay living. */
    data object Slays : Wounding
}

/**
 * The cleric's spells that hurt: the three cause wounds and harm, laid on by
 * hand, and slay living, which reaches further and kills.
 *
 * A touch is a blow with an empty hand as far as reaching goes: only the
 * front rank can make it, it lands on the creature their own blow would, and
 * it rolls to hit as a blow does — a miss spends the spell and says nothing.
 * What it does once it lands is magic: a creature this magic passes by takes
 * none of it, one quick enough is out of its way, and one that shrugs off
 * weak weapons takes half. Its throw is made and changes nothing.
 */
class Wounds(
    private val kinds: List<MonsterProperty>,
    private val dice: Dice = Dice.random,
    // Where a slain monster's belongings land.
    private val level: Int = 0,
) {
    data class Dealt(val world: GameState, val came: CameTo)

    enum class CameTo {
        /** Cast from the back, which the caster is told. */
        NOT_IN_THE_FRONT_RANK,

        /** Nothing within reach, which a touch tells the caster and slay living does not. */
        NOTHING_THERE,

        /** Something there, and the spell came to nothing on it. Nothing is said. */
        CAME_TO_NOTHING,

        LANDED,
    }

    fun touchBy(world: GameState, whose: PartySlot, wounding: Wounding): Dealt {
        if (!whose.inTheFrontRank) return Dealt(world, CameTo.NOT_IN_THE_FRONT_RANK)
        val champion = world.championIn(whose) ?: return Dealt(world, CameTo.NOTHING_THERE)
        val target = world.theMonsterNearestAhead(whose) ?: return Dealt(world, CameTo.NOTHING_THERE)
        val roused = world.rousedBy(target.index)

        // Stone is not fought: whatever lands on it breaks it.
        if (target.stone) {
            return Dealt(roused.monsterShattered(target.index).paidForAndSearched(target, kinds, level, dice), CameTo.LANDED)
        }

        val kind = kinds.firstOrNull { it.id == target.type.value }
        if (kind != null && !kind.immunities.canBeHitBy(0)) return Dealt(roused, CameTo.CAME_TO_NOTHING)

        val needed = champion.needsToHit(kind?.armorClass ?: 0) -
            (champion.abilities.strengthToHitBonus + world.running.helpStriking(whose))
        if (dice.roll(1, 20, 0).coerceIn(1, 20) < needed) return Dealt(roused, CameTo.CAME_TO_NOTHING)

        val rolled = when (wounding) {
            is Wounding.ByTouch -> dice.roll(wounding.dice.times, wounding.dice.pips, wounding.dice.base)
            Wounding.AllButAFew -> target.hitPoints.max - dice.roll(1, A_FEW, 0)
            Wounding.Slays -> return Dealt(roused, CameTo.CAME_TO_NOTHING)
        }

        kind?.saves(SavingThrow.A_SPELL, dice)
        var dealt = kind?.immunities?.softened(Damage(rolled.coerceAtLeast(0)), DealtBy.Magic)
            ?: Damage(rolled.coerceAtLeast(0))
        if (kind != null && dice.roll(1, 100, 0) < kind.dmgModifierEvade) dealt = Damage(0)
        if (kind?.immunities?.untouchedByThisMagic == true) dealt = Damage(0)

        return Dealt(hurt(roused, target, dealt), CameTo.LANDED)
    }

    /**
     * Slay living: the nearest creature on the first two squares ahead that
     * the party can see onto, [reachable] saying which they can. One that gets
     * out of the way, or that this magic passes by, is untouched; one that
     * makes its throw takes [SLAIN_OR_THIS] instead of dying; the rest die,
     * but for a kind no spell kills outright.
     */
    fun slainBy(world: GameState, whose: PartySlot, reachable: (Location) -> Boolean): Dealt {
        val found = world.theMonsterNearestWithin(SLAY_REACH, whose, reachable)
            ?: return Dealt(world, CameTo.NOTHING_THERE)
        val roused = world.rousedBy(found.index)

        val kind = kinds.firstOrNull { it.id == found.type.value } ?: return Dealt(roused, CameTo.CAME_TO_NOTHING)
        if (dice.roll(1, 100, 0) < kind.dmgModifierEvade || kind.immunities.untouchedByThisMagic) {
            return Dealt(roused, CameTo.CAME_TO_NOTHING)
        }

        val dealt = when {
            kind.saves(SavingThrow.A_SPELL, dice) ->
                Damage(dice.roll(SLAIN_OR_THIS.times, SLAIN_OR_THIS.pips, SLAIN_OR_THIS.base))
            kind.immunities.notSlainByMagic -> return Dealt(roused, CameTo.CAME_TO_NOTHING)
            else -> OUTRIGHT
        }
        return Dealt(hurt(roused, found, dealt), CameTo.LANDED)
    }

    private fun hurt(world: GameState, target: MonsterInstance, dealt: Damage): GameState {
        if (dealt.points <= 0) return world
        val after = world.monsterHurt(target.index, dealt, kinds, null, dice)
        val left = after.monsters.firstOrNull { it.index == target.index }
        val killed = left == null || left.type != target.type
        return if (killed) after.paidForAndSearched(target, kinds, level, dice) else after
    }

    private companion object {
        /** The most hit points harm leaves a creature with. */
        const val A_FEW = 4

        const val SLAY_REACH = 2

        /** More than anything has, which is how a spell kills outright. */
        val OUTRIGHT = Damage(1000)

        /** What slay living does to a creature that makes its throw. */
        val SLAIN_OR_THIS = DamageDice(times = 2, pips = 8, base = 1)
    }
}
