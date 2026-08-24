package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable

/**
 * The 320×200 game screen: PLAYFLD.CPS with the rendered 3D view blitted into
 * its top-left window and the compass needle overlaid from DECORATE.CPS.
 *
 * All coordinates are transcribed: the view is copied to (0,0) at 176×120,
 * and the compass is three shapes drawn at [COMPASS_TARGETS].
 */
class PlayField(
    private val background: Cps,
    private val decorations: Cps,
    private val palette: Palette,
    private val font: Font? = null,
    private val menuFont: Font? = null,
    /** INVENT.CPS, which the panel of an open [CharacterSheet] is cut from. */
    private val invent: Cps? = null,
    /** ITEMICN.CPS, where an item being carried is drawn from. */
    private val itemIcons: Cps? = null,
    /** What each kind of item is, which says whose hand it is any use in. */
    private val itemTypes: ItemTypes? = null,
    /** THROWN.CPS, which the splash a blow is reported on is cut from. */
    private val thrown: Cps? = null,
    private val preferences: Preferences = Preferences(),
) {
    private val pixels = MutableList(WIDTH * HEIGHT) { RGB(0, 0, 0, true) }

    fun render(
        viewPort: ViewPort,
        direction: Direction,
        dialogue: DialogueScene? = null,
        messages: List<Message> = emptyList(),
        party: List<Champion> = emptyList(),
        portraits: Cps? = null,
        /** The faces of the people the dungeon holds, who have no other sheet. */
        metPortraits: Cps? = null,
        menu: CampMenu? = null,
        sheet: OpenSheet? = null,
        carrying: (ItemIndex) -> Item? = { null },
        /** Whether that hand has yet to come back to rest from a swing. */
        recovering: (PartySlot, CarrySlot) -> Boolean = { _, _ -> false },
        /** What that hand's last swing came to, while the slot is still saying. */
        reporting: (PartySlot, CarrySlot) -> WhatTheBlowCameTo? = { _, _ -> null },
        /** What that champion has just been hit for, while it is still showing. */
        hurt: (PartySlot) -> Int? = { null },
    ): PlayField {
        // Something held up over the view is read off a champion's own page,
        // that being the one place a thing being carried can be clicked, so the
        // page is drawn last and stays in reach — a map's frame is eight pixels
        // wider than the view and would otherwise bite into it. A script
        // speaking in the strip below is the other way round: it takes the full
        // width of the screen, that side included.
        val underThePage = dialogue != null &&
            dialogue.readOff != DialogueScene.ReadOff.TheStripBelow

        drawBackground()
        // a champion's own page takes the six boxes' side of the screen
        if (sheet == null) {
            drawParty(party, portraits, metPortraits, carrying, recovering, reporting, hurt)
        } else if (!underThePage) {
            drawSheet(sheet, portraits, metPortraits)
        }

        drawViewPort(viewPort)
        drawCompass(direction)
        drawMessages(messages)
        dialogue?.let(::drawDialogue)

        if (sheet != null && underThePage) drawSheet(sheet, portraits, metPortraits)
        menu?.let(::drawMenu)
        return this
    }

    /**
     * A champion's page, over the side of the screen the party boxes are on.
     *
     * The panel's boxes are drawn into INVENT.CPS already, so what is put on
     * top of it is only what changes: who this is, how they are, and what they
     * have in each of their slots.
     */
    private fun drawSheet(sheet: OpenSheet, portraits: Cps?, metPortraits: Cps?) {
        val invent = invent ?: return

        copy(
            from = invent,
            sourceLeft = CharacterSheet.LEFT,
            sourceTop = CharacterSheet.TOP,
            width = CharacterSheet.WIDTH,
            height = CharacterSheet.HEIGHT,
            left = CharacterSheet.LEFT,
            top = CharacterSheet.TOP,
        )

        portraits?.let { faces ->
            val face = faceOf(sheet.champion.portrait, faces, metPortraits) ?: return@let
            val colours = (if (sheet.champion.portrait.value < 0) metPortraits else faces)
                ?.palette ?: palette
            for (y in 0 until face.h) {
                for (x in 0 until face.w) {
                    val index = face.pixels[y * face.w + x]
                    draw(
                        CharacterSheet.PORTRAIT_LEFT + x,
                        CharacterSheet.PORTRAIT_TOP + y,
                        colours.colors[index.value],
                    )
                }
            }
        }

        font?.let { font ->
            write(
                text = sheet.champion.name,
                font = font,
                left = CharacterSheet.NAME_LEFT,
                top = CharacterSheet.NAME_TOP,
                colour = if (sheet.champion.inTrouble) NAME_IN_TROUBLE else NAME_COLOUR,
            )
        }

        drawSheetBars(sheet.champion)

        when (sheet.page) {
            CharacterSheet.Page.BELONGINGS -> drawCarried(sheet.carrying, sheet.arrows)
            CharacterSheet.Page.STATS -> drawStats(sheet.champion)
        }
    }

    /**
     * The second page: what the champion is, written over the figure and the
     * slots of the first, which are painted out to make room.
     */
    private fun drawStats(champion: Champion) {
        val font = font ?: return

        StatsPage.BLANKED.forEach { blanked ->
            for (y in blanked.top..blanked.bottom) {
                for (x in blanked.left..blanked.right) {
                    draw(x, y, palette.colors[StatsPage.BLANK_COLOUR.value])
                }
            }
        }

        write(
            text = StatsPage.HEADLINE,
            font = font,
            left = StatsPage.HEADLINE_LEFT,
            top = StatsPage.HEADLINE_TOP,
            colour = StatsPage.HEADLINE_COLOUR,
        )

        listOf(champion.className, champion.alignmentName, champion.raceAndSexName)
            .forEachIndexed { line, description ->
                write(
                    text = description,
                    font = font,
                    left = StatsPage.DESCRIPTION_LEFT,
                    top = StatsPage.DESCRIPTION_TOP + line * StatsPage.DESCRIPTION_LINE,
                    colour = StatsPage.LABEL_COLOUR,
                )
            }

        abilityNames.zip(champion.abilities.asListed())
            .forEachIndexed { line, (name, score) ->
                val top = StatsPage.ABILITY_TOP + line * StatsPage.ABILITY_LINE
                write(name, font, StatsPage.ABILITY_LEFT, top, StatsPage.LABEL_COLOUR)
                write(score, font, StatsPage.ABILITY_VALUE_LEFT, top, StatsPage.VALUE_COLOUR)
            }

        write(
            text = StatsPage.ARMOUR_CLASS,
            font = font,
            left = StatsPage.ARMOUR_CLASS_LEFT,
            top = StatsPage.ARMOUR_CLASS_TOP,
            colour = StatsPage.LABEL_COLOUR,
        )
        write(
            text = "${champion.armorClass.value}",
            font = font,
            left = StatsPage.ARMOUR_CLASS_VALUE_LEFT,
            top = StatsPage.ARMOUR_CLASS_TOP,
            colour = StatsPage.VALUE_COLOUR,
        )

        write(
            text = StatsPage.EXPERIENCE,
            font = font,
            left = StatsPage.EXPERIENCE_LEFT,
            top = StatsPage.EXPERIENCE_TOP,
            colour = StatsPage.LABEL_COLOUR,
        )
        write(
            text = StatsPage.LEVEL,
            font = font,
            left = StatsPage.LEVEL_LEFT,
            top = StatsPage.LEVEL_TOP,
            colour = StatsPage.LABEL_COLOUR,
        )

        // one line per class, so a multi-class has its two one under the other
        val classes = champion.levelledClassNames
        champion.levels.forEachIndexed { line, career ->
            val top = StatsPage.CAREER_TOP + line * StatsPage.CAREER_LINE

            write(
                text = classes.getOrElse(line) { "" },
                font = font,
                left = StatsPage.CAREER_LEFT,
                top = top,
                colour = StatsPage.LABEL_COLOUR,
            )
            writeCentred("${career.experience}", font, StatsPage.EXPERIENCE_MIDDLE, top)
            writeCentred("${career.level}", font, StatsPage.LEVEL_MIDDLE, top)
        }
    }

    /** Both numbers on a career's line are written about their column, not from it. */
    private fun writeCentred(text: String, font: Font, middle: Int, top: Int) = write(
        text = text,
        font = font,
        left = middle - text.length * font.width / 2,
        top = top,
        colour = StatsPage.VALUE_COLOUR,
    )

    /**
     * How hurt they are and how hungry. Only the first of the two answers to
     * the bar graphs setting — how full a champion is has no numbers to be
     * written as, so it is a bar either way.
     */
    private fun drawSheetBars(champion: Champion) {
        val width = CharacterSheet.BAR_WIDTH

        if (preferences.barGraphs) {
            drawSheetBar(CharacterSheet.HIT_POINT_BAR_TOP, hitPointBar(champion.hitPoints, width))
        } else {
            font?.let { font ->
                write(
                    text = hitPointsWritten(champion.hitPoints),
                    font = font,
                    left = CharacterSheet.BAR_LEFT,
                    top = CharacterSheet.HIT_POINT_BAR_TOP,
                    colour = NAME_COLOUR,
                )
            }
        }

        drawSheetBar(CharacterSheet.FOOD_BAR_TOP, foodBar(champion.food, width))
    }

    private fun drawSheetBar(top: Int, bar: BarFill) {
        drawBox(
            left = CharacterSheet.BAR_LEFT - 1,
            top = top - 1,
            width = CharacterSheet.BAR_WIDTH + 2,
            height = CharacterSheet.BAR_HEIGHT + 2,
            topRight = EDGE_SHADED,
            bottomLeft = EDGE_LIT,
            fill = null,
        )

        for (y in 0 until CharacterSheet.BAR_HEIGHT) {
            for (x in 0 until CharacterSheet.BAR_WIDTH) {
                val ink = if (x < bar.filled) bar.colour else BAR_EMPTY
                draw(CharacterSheet.BAR_LEFT + x, top + y, palette.colors[ink.value])
            }
        }
    }

    private fun drawCarried(carrying: List<Item?>, arrows: Int) {
        inventorySlotPositions.forEach { slot ->
            if (slot.isQuiver) {
                drawTally(slot, arrows)
                return@forEach
            }

            val icons = itemIcons ?: return@forEach
            val item = carrying.getOrNull(slot.slot.index) ?: return@forEach

            drawIcon(
                icon = icons.itemIcon(item.icon),
                colours = icons.palette ?: palette,
                left = slot.iconLeft,
                top = slot.iconTop,
            )
        }
    }

    private fun drawIcon(icon: Cps.ItemIcon, colours: Palette, left: Int, top: Int) {
        for (y in 0 until icon.h) {
            for (x in 0 until icon.w) {
                val index = icon.pixels[y * icon.w + x]
                if (index.isTransparent) continue
                draw(left + x, top + y, colours.colors[index.value])
            }
        }
    }

    /**
     * How many arrows the quiver holds, over a strip wiped clean first so that
     * one figure never leaves the tail of two behind it.
     */
    private fun drawTally(slot: InventorySlot, count: Int) {
        val font = font ?: return

        for (y in 0 until InventorySlot.TALLY_HEIGHT) {
            for (x in 0 until InventorySlot.TALLY_WIDTH) {
                draw(slot.tallyLeft + x, slot.tallyTop + y, palette.colors[TALLY_BACKING.value])
            }
        }

        val figures = count.toString()
        write(
            text = figures,
            font = font,
            left = slot.tallyStart(figures.length),
            top = slot.tallyTop,
            colour = TALLY_COLOUR,
        )
    }

    /**
     * A camp menu, over the view rather than beside it.
     *
     * Each line is the interface's raised box drawn twice, one inside the
     * other — which is what gives the menus their heavier edge than the
     * dialogue strips have.
     */
    private fun drawMenu(menu: CampMenu) {
        val font = menuFont ?: font ?: return

        drawMenuBox(CampMenu.LEFT, CampMenu.TOP, CampMenu.WIDTH, CampMenu.HEIGHT)
        write(
            text = menu.title,
            font = font,
            left = CampMenu.LEFT + menu.titleLeft,
            top = CampMenu.TOP + CampMenu.TITLE_TOP,
            colour = MENU_TITLE,
        )

        menu.entries.forEachIndexed { row, entry ->
            drawMenuBox(
                left = CampMenu.LEFT + entry.left,
                top = CampMenu.TOP + entry.top,
                width = entry.width,
                height = entry.height,
            )

            val naming = menu.naming?.takeIf { it.slot == row }
            write(
                text = naming?.typed ?: entry.label,
                font = font,
                left = CampMenu.LEFT + entry.labelLeft,
                top = CampMenu.TOP + entry.labelTop,
                colour = if (naming == null) MENU_LABEL else BEING_TYPED,
            )

            // the caret sits where the next letter will land
            if (naming != null) {
                val caret = CampMenu.LEFT + entry.labelLeft + font.widthOf(naming.typed)
                for (y in 0 until font.height) {
                    for (x in 0 until font.width) {
                        draw(caret + x, CampMenu.TOP + entry.labelTop + y, palette.colors[CARET.value])
                    }
                }
            }
        }
    }

    /** The interface's box with a second one just inside it. */
    private fun drawMenuBox(left: Int, top: Int, width: Int, height: Int) {
        drawBox(left, top, width, height, fill = null)
        drawBox(left + 1, top + 1, width - 2, height - 2)
    }

    /**
     * A box down the right for each champion, and nothing at all for a slot
     * nobody fills.
     *
     * An empty slot is not an empty box: a slot is drawn only where somebody
     * is in it, so a party of four leaves the bottom of the panel as
     * bare wall.
     */
    private fun drawParty(
        party: List<Champion>,
        portraits: Cps?,
        metPortraits: Cps?,
        carrying: (ItemIndex) -> Item?,
        recovering: (PartySlot, CarrySlot) -> Boolean,
        reporting: (PartySlot, CarrySlot) -> WhatTheBlowCameTo?,
        hurt: (PartySlot) -> Int?,
    ) {
        championBoxes.forEachIndexed { slot, box ->
            val champion = party.getOrNull(slot)?.takeIf { it.inTheParty } ?: return@forEachIndexed

            copy(
                from = background,
                sourceLeft = boxInTheArt.left,
                sourceTop = boxInTheArt.top,
                width = ChampionBox.WIDTH,
                height = ChampionBox.HEIGHT,
                left = box.left,
                top = box.top,
            )
            drawChampion(
                champion = champion,
                metPortraits = metPortraits,
                box = box,
                portraits = portraits,
                carrying = carrying,
                recovering = { hand -> recovering(PartySlot(slot), CarrySlot(hand)) },
                reporting = { hand -> reporting(PartySlot(slot), CarrySlot(hand)) },
                hurt = hurt(PartySlot(slot)),
            )
        }
    }

    private fun drawChampion(
        champion: Champion,
        metPortraits: Cps?,
        box: ChampionBox,
        portraits: Cps?,
        carrying: (ItemIndex) -> Item?,
        recovering: (Int) -> Boolean,
        reporting: (Int) -> WhatTheBlowCameTo?,
        hurt: Int?,
    ) {
        portraits?.let { sheet ->
            val face = faceOf(champion.portrait, sheet, metPortraits) ?: return@let
            val colours = (if (champion.portrait.value < 0) metPortraits else sheet)
                ?.palette ?: palette
            for (y in 0 until face.h) {
                for (x in 0 until face.w) {
                    val index = face.pixels[y * face.w + x]
                    draw(box.portraitLeft + x, box.portraitTop + y, colours.colors[index.value])
                }
            }
        }

        font?.let { font ->
            write(
                text = champion.name,
                font = font,
                left = box.nameLeft,
                top = box.nameTop,
                colour = if (champion.inTrouble) NAME_IN_TROUBLE else NAME_COLOUR,
            )
        }

        drawHands(champion, box, carrying, recovering, reporting)
        drawHitPointBar(champion, box)

        // Last of all, over the face and the bars alike: it is meant to be the
        // thing a player sees rather than something tucked behind the rest.
        hurt?.let { drawTheDamage(it, box) }
    }

    /**
     * What the champion has in each hand, in the two slots beside their face.
     * An empty hand is not left blank — the hand itself is drawn there.
     *
     * A hand its champion cannot strike with is drawn over with a grid: the
     * item is still shown, and shown to be no use. A hand that has just swung
     * gets the same grid — the two are not told apart, so a hand recovering
     * looks exactly like one holding the wrong thing.
     */
    private fun drawHands(
        champion: Champion,
        box: ChampionBox,
        carrying: (ItemIndex) -> Item?,
        recovering: (Int) -> Boolean,
        reporting: (Int) -> WhatTheBlowCameTo?,
    ) {
        val icons = itemIcons ?: return

        repeat(Champion.HANDS) { hand ->
            // What the last swing came to takes the slot over while it is
            // still news, so the weapon is not drawn under it
            reporting(hand)?.let { came ->
                drawTheBlow(came, box, hand)
                return@repeat
            }

            val held = champion.carrying.getOrNull(hand)?.let(carrying)
            drawIcon(
                icon = icons.itemIcon(held?.icon ?: emptyHandIcon(hand)),
                colours = icons.palette ?: palette,
                left = box.handLeft,
                top = box.handTop(hand),
            )

            if (recovering(hand) || !canStrikeWith(champion, hand, carrying)) {
                drawIcon(
                    icon = decorations.weaponSlotGrid(),
                    colours = decorations.palette ?: palette,
                    left = box.handSlotLeft,
                    top = box.handTop(hand),
                )
            }
        }
    }

    /**
     * What the swing came to, written where the weapon's icon goes.
     *
     * A blow that got somewhere is written on a splash of blood; the two that
     * report the arm never going anywhere get a box in the colour the
     * interface warns in. One line sits in the middle of the slot and two
     * straddle it, and both are centred by the same rule — six pixels a
     * letter, taken off the middle.
     */
    /**
     * A blow a champion has just taken, splashed over their portrait with the
     * number on it.
     *
     * The one thing that says they were hit at all. Hit points move too, but a
     * bar creeping down is not something anybody notices mid-fight.
     */
    private fun drawTheDamage(damage: Int, box: ChampionBox) {
        val font = font ?: return
        val thrown = thrown ?: return

        drawIcon(
            icon = thrown.redSplat(),
            colours = thrown.palette ?: palette,
            left = box.splatLeft,
            top = box.splatTop,
        )

        val shown = "$damage"
        write(
            text = shown,
            font = font,
            left = box.damageLeft(shown.length),
            top = box.damageTop,
            colour = TEXT_COLOUR,
        )
    }

    private fun drawTheBlow(came: WhatTheBlowCameTo, box: ChampionBox, hand: Int) {
        val font = font ?: return
        val top = box.handTop(hand)

        val thrown = thrown
        if (came.onABloodySplash && thrown != null) {
            drawIcon(
                icon = thrown.greenSplat(),
                colours = thrown.palette ?: palette,
                left = box.handSlotLeft - SPLAT_OVERHANG,
                top = top,
            )
        } else if (!came.onABloodySplash) {
            drawBox(
                left = box.handSlotLeft,
                top = top,
                width = ChampionBox.HAND_SLOT_WIDTH,
                height = ChampionBox.HAND_SLOT_HEIGHT,
                topRight = WARNING_EDGE_LIT,
                bottomLeft = WARNING_EDGE_SHADED,
                fill = WARNING_FILL,
            )
        }

        val lineTops = if (came.lines.size > 1) TWO_LINES else ONE_LINE
        came.lines.forEachIndexed { line, text ->
            write(
                text = text,
                font = font,
                left = box.handSlotLeft + BLOW_MIDDLE - text.length * BLOW_LETTER,
                top = top + lineTops[line],
                colour = TEXT_COLOUR,
            )
        }
    }

    /**
     * Whether the hand is any use. Being down or held stops both hands
     * whatever is in them; the rest is a question about the items.
     */
    private fun canStrikeWith(
        champion: Champion,
        hand: Int,
        carrying: (ItemIndex) -> Item?,
    ): Boolean = when {
        champion.dead || champion.heldFast -> false
        itemTypes == null -> true
        else -> itemTypes.canStrikeWith(champion, hand, carrying)
    }

    /** The hit point bar, sunk into the strip it sits on, with HP written beside it. */
    private fun drawHitPointBar(champion: Champion, box: ChampionBox) {
        val font = font

        // The numbers stand where the label would, because they say the same
        // thing at greater length: with the bar there it needs naming, and
        // without it there is nothing left to name.
        if (!preferences.barGraphs) {
            if (font != null) {
                write(
                    text = hitPointsWritten(champion.hitPoints),
                    font = font,
                    left = box.barLabelLeft,
                    top = box.barLabelTop,
                    colour = NAME_COLOUR,
                )
            }
            return
        }

        val bar = hitPointBar(champion.hitPoints, ChampionBox.BAR_WIDTH)

        drawBox(
            left = box.barLeft - 1,
            top = box.barTop - 1,
            width = ChampionBox.BAR_WIDTH + 2,
            height = ChampionBox.BAR_HEIGHT + 2,
            topRight = EDGE_SHADED,
            bottomLeft = EDGE_LIT,
            fill = null,
        )

        for (y in 0 until ChampionBox.BAR_HEIGHT) {
            for (x in 0 until ChampionBox.BAR_WIDTH) {
                val ink = if (x < bar.filled) bar.colour else BAR_EMPTY
                draw(box.barLeft + x, box.barTop + y, palette.colors[ink.value])
            }
        }

        font?.let { font ->
            write(
                text = ChampionBox.BAR_LABEL,
                font = font,
                left = box.barLabelLeft,
                top = box.barLabelTop,
                colour = NAME_COLOUR,
            )
        }
    }

    /**
     * Hit points as they are written where bars are not drawn: what is left
     * of them ranged right, out of what there is ranged left, so a
     * column of them lines up on the word between.
     */
    private fun hitPointsWritten(hitPoints: HitPoints) =
        "${hitPoints.current.toString().padStart(HIT_POINT_FIGURES)} of " +
            hitPoints.max.toString().padEnd(HIT_POINT_FIGURES)

    /**
     * The bar along the bottom, beside the camp button, where a script writes
     * when it has no dialogue box open.
     *
     * Nothing takes a line off it. It is a fixed height with a fixed font, so
     * once full it scrolls: the newest line is at the bottom and the oldest
     * falls off the top. Each line keeps the colour it was written in, since
     * what scrolls is the pixels.
     */
    private fun drawMessages(messages: List<Message>) {
        val font = font ?: return

        messages
            .flatMap { message -> font.wrap(message.text, MESSAGE_WIDTH).map { it to message.colour } }
            .takeLast(MESSAGE_HEIGHT / font.height)
            .forEachIndexed { line, (text, colour) ->
                write(
                    text = text,
                    font = font,
                    left = MESSAGE_LEFT,
                    top = MESSAGE_TOP + line * font.height,
                    colour = colour,
                )
            }
    }

    /** A line on the bar along the bottom, in the colour the script asked for. */
    @Serializable
    data class Message(val text: String, val colour: PaletteIndex)

    /**
     * A conversation, drawn over the view the way the script asked for it.
     *
     * Scripts build one out of drawing instructions — clear the view, draw who
     * is talking, draw the frame — so this belongs on the play field itself
     * rather than floating above it. The frame goes down first and hides the 3D
     * view; the speaker sits inside it, and the speech and buttons below.
     */
    private fun drawDialogue(dialogue: DialogueScene) {
        dialogue.frame?.let { frame ->
            copy(
                from = frame,
                sourceLeft = 0,
                sourceTop = 0,
                width = DialogueScene.FRAME_WIDTH,
                height = DialogueScene.FRAME_HEIGHT,
                left = 0,
                top = 0,
            )
        }

        dialogue.portrait?.let { portrait ->
            copy(
                from = portrait.cps,
                sourceLeft = portrait.sourceLeft,
                sourceTop = portrait.sourceTop,
                width = portrait.goes.width,
                height = portrait.goes.height,
                left = portrait.goes.left,
                top = portrait.goes.top,
                cutOut = portrait.goes.cutOut,
            )
        }

        // a picture is the whole of what is shown, so nothing is cleared for
        // words there are none of
        val readOff = dialogue.readOff as? DialogueScene.ReadOff.Written ?: return

        // one box over what the words go on, so what is behind them — the
        // party's boxes and the compass, or the dungeon — does not show through
        drawBox(
            left = readOff.panelLeft,
            top = readOff.panelTop,
            width = readOff.panelWidth,
            height = readOff.panelHeight,
        )

        val font = font ?: return

        dialogue.lines.forEachIndexed { line, text ->
            write(
                text = text,
                font = font,
                left = readOff.textLeft,
                top = readOff.textTop + line * font.height,
                colour = TEXT_COLOUR,
            )
        }

        dialogue.buttons.forEachIndexed { index, button ->
            drawButton(button, font, highlighted = index == 0)
        }
    }

    /**
     * The one box the interface is made of: two edges in one colour, two in
     * the other, and an optional flat fill.
     *
     * Lit along the top and right it stands proud of what is behind it, which
     * is how a conversation strip and its buttons are drawn. Handed the two
     * colours the other way round it becomes a channel cut into the panel,
     * which is how the hit point bars sit in their strip.
     */
    private fun drawBox(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        topRight: PaletteIndex = EDGE_LIT,
        bottomLeft: PaletteIndex = EDGE_SHADED,
        fill: PaletteIndex? = FILL,
    ) {
        val right = left + width - 1
        val bottom = top + height - 1

        if (fill != null) {
            for (y in top + 1 until bottom) {
                for (x in left + 1 until right) {
                    draw(x, y, palette.colors[fill.value])
                }
            }
        }

        for (x in left + 1..right) draw(x, top, palette.colors[topRight.value])
        for (y in top until bottom) draw(right, y, palette.colors[topRight.value])
        for (y in top..bottom) draw(left, y, palette.colors[bottomLeft.value])
        for (x in left..right) draw(x, bottom, palette.colors[bottomLeft.value])
    }

    private fun drawButton(button: DialogueScene.Button, font: Font, highlighted: Boolean) {
        drawBox(
            left = button.left,
            top = button.top,
            width = DialogueScene.Button.WIDTH,
            height = DialogueScene.Button.HEIGHT,
        )

        write(
            text = button.label,
            font = font,
            left = button.left + DialogueScene.Button.WIDTH / 2 - font.widthOf(button.label) / 2,
            top = button.top + BUTTON_LABEL_OFFSET_Y,
            colour = if (highlighted) BUTTON_LABEL_HIGHLIGHTED else BUTTON_LABEL_COLOUR,
        )
    }

    private fun write(
        text: String,
        font: Font,
        left: Int,
        top: Int,
        colour: PaletteIndex,
    ) {
        text.forEachIndexed { position, character ->
            val glyph = font.glyphFor(character) ?: return@forEachIndexed
            for (y in 0 until font.height) {
                for (x in 0 until font.width) {
                    if (!glyph.isInk(x, y)) continue
                    draw(left + position * font.width + x, top + y, palette.colors[colour.value])
                }
            }
        }
    }

    /**
     * @param cutOut whether the sheet's own background is part of what is
     *   being copied. A picture filling a frame is a rectangle of the file;
     *   somebody standing over the view is a shape cut out of it.
     */
    private fun copy(
        from: Cps,
        sourceLeft: Int,
        sourceTop: Int,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        cutOut: Boolean = false,
    ) {
        val colours = from.palette ?: palette

        for (y in 0 until height) {
            for (x in 0 until width) {
                val sourceX = sourceLeft + x
                val sourceY = sourceTop + y
                if (sourceX !in 0 until from.width || sourceY !in 0 until from.height) continue

                val index = from.pixels[sourceY * from.width + sourceX]
                if (cutOut && index.isTransparent) continue

                draw(left + x, top + y, colours.colors[index.value])
            }
        }
    }

    fun getRows(): List<List<RGB>> = pixels.chunked(WIDTH)

    private fun draw(x: Int, y: Int, rgb: RGB) {
        if (x !in 0 until WIDTH || y !in 0 until HEIGHT) return
        pixels[y * WIDTH + x] = rgb
    }

    private fun drawBackground() {
        for (y in 0 until minOf(HEIGHT, background.height)) {
            for (x in 0 until minOf(WIDTH, background.width)) {
                val index = background.pixels[y * background.width + x]
                // the frame is opaque: index 0 is a real color here, not a hole
                draw(x, y, palette.colors[index.value])
            }
        }
    }

    private fun drawViewPort(viewPort: ViewPort) {
        viewPort.getRows().forEachIndexed { y, row ->
            row.forEachIndexed { x, rgb ->
                if (!rgb.transparent) draw(VIEW_X + x, VIEW_Y + y, rgb)
            }
        }
    }

    private fun drawCompass(direction: Direction) {
        COMPASS_TARGETS.forEachIndexed { part, target ->
            val srcX = direction.compassColumn * COMPASS_WIDTH
            val srcY = COMPASS_SOURCE_Y[part]
            val height = COMPASS_HEIGHT[part]

            for (y in 0 until height) {
                for (x in 0 until COMPASS_WIDTH) {
                    val index = decorations.pixels[(srcY + y) * decorations.width + srcX + x]
                    if (index.isTransparent) continue
                    draw(target.first + x, target.second + y, palette.colors[index.value])
                }
            }
        }
    }

    companion object {
        const val WIDTH = 320
        const val HEIGHT = 200

        /** Dialogue colours. */
        private val FILL = PaletteIndex(183)
        private val EDGE_LIT = PaletteIndex(181)
        private val EDGE_SHADED = PaletteIndex(186)
        private val TEXT_COLOUR = PaletteIndex(15)
        private val BUTTON_LABEL_COLOUR = PaletteIndex(15)
        private val BUTTON_LABEL_HIGHLIGHTED = PaletteIndex(9)
        private const val BUTTON_LABEL_OFFSET_Y = 2

        /** The box the interface warns in, which is the dialogue box's colours reddened. */
        private val WARNING_EDGE_LIT = PaletteIndex(23)
        private val WARNING_EDGE_SHADED = PaletteIndex(17)
        private val WARNING_FILL = PaletteIndex(20)

        /**
         * Where what a blow came to is written in the slot: one line in the
         * middle, two straddling it, and both centred six pixels a letter off
         * the middle of the slot's sixteen.
         */
        private val ONE_LINE = listOf(5)
        private val TWO_LINES = listOf(2, 9)
        private const val BLOW_MIDDLE = 16
        private const val BLOW_LETTER = 3

        /** The splash is a pixel wider on its left than the slot it covers. */
        private const val SPLAT_OVERHANG = 1

        /** Party panel colours. */
        private val NAME_COLOUR = PaletteIndex(12)
        private val NAME_IN_TROUBLE = PaletteIndex(8)
        private val BAR_EMPTY = PaletteIndex(184)

        /** Room for three figures either side of the word between them. */
        private const val HIT_POINT_FIGURES = 3

        private val TALLY_BACKING = PaletteIndex(12)
        private val TALLY_COLOUR = PaletteIndex(15)

        /** Camp menu colours. */
        private val MENU_TITLE = PaletteIndex(9)
        private val MENU_LABEL = PaletteIndex(15)
        private val BEING_TYPED = PaletteIndex(2)
        private val CARET = PaletteIndex(8)

        /**
         * The message line along the bottom, beside the camp button. The band
         * itself is painted into the play field art; only the words are drawn.
         */
        private const val MESSAGE_LEFT = 8
        private const val MESSAGE_TOP = 180
        private const val MESSAGE_WIDTH = 272
        private const val MESSAGE_HEIGHT = 18

        /** Where the 3D view is copied into the frame. */
        const val VIEW_X = 0
        const val VIEW_Y = 0

        /**
         * Shape sources in DECORATE.CPS. Shapes are cut in 8-pixel units, so
         * the compass columns are 3 units = 24px wide and start at
         * `direction * 24`.
         */
        private const val COMPASS_WIDTH = 24
        private val COMPASS_SOURCE_Y = listOf(120, 137, 147)
        private val COMPASS_HEIGHT = listOf(17, 10, 10)

        /** EoB2 shpX/shpY from gui_drawCompass. */
        private val COMPASS_TARGETS = listOf(114 to 131, 79 to 158, 151 to 158)
    }
}

/**
 * Column of this direction's shapes in DECORATE.CPS, which are kept in the
 * order the facings are counted in: north, east, south, west.
 */
private val Direction.compassColumn: Int
    get() = when (this) {
        Direction.NORTH -> 0
        Direction.EAST -> 1
        Direction.SOUTH -> 2
        Direction.WEST -> 3
    }
