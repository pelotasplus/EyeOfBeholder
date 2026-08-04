package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import pl.pelotasplus.eyeofbeholder.data.model.CampMenu
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.CharacterSheet
import pl.pelotasplus.eyeofbeholder.data.model.OpenSheet
import pl.pelotasplus.eyeofbeholder.data.model.SheetChoice
import pl.pelotasplus.eyeofbeholder.data.model.championBoxes
import pl.pelotasplus.eyeofbeholder.data.model.MenuChoice
import pl.pelotasplus.eyeofbeholder.data.model.Naming
import pl.pelotasplus.eyeofbeholder.data.model.Typing
import pl.pelotasplus.eyeofbeholder.data.model.canTypeIntoTheGame
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene.Companion.MORE
import pl.pelotasplus.eyeofbeholder.data.model.DialogueText
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.FloorReach
import pl.pelotasplus.eyeofbeholder.data.model.Font
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.InventorySlot
import pl.pelotasplus.eyeofbeholder.data.model.inventorySlotAt
import pl.pelotasplus.eyeofbeholder.data.model.inventorySlotPositions
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.IN_A_NICHE
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.WallAction
import pl.pelotasplus.eyeofbeholder.data.model.doesWhenClicked
import pl.pelotasplus.eyeofbeholder.data.model.ItemMessages
import pl.pelotasplus.eyeofbeholder.data.model.ItemNames
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.OriginalSave
import pl.pelotasplus.eyeofbeholder.data.model.ClickedWall
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Palette
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PlayField
import pl.pelotasplus.eyeofbeholder.data.model.Preferences
import pl.pelotasplus.eyeofbeholder.data.model.SavedGame
import pl.pelotasplus.eyeofbeholder.data.model.rightNow
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptQuestion
import pl.pelotasplus.eyeofbeholder.data.model.ScriptSpeech
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStage
import pl.pelotasplus.eyeofbeholder.data.model.TELEPORTER_PULSE
import pl.pelotasplus.eyeofbeholder.data.model.TeleporterPulse
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.levelNumber
import pl.pelotasplus.eyeofbeholder.data.model.canBeReachedOnto
import pl.pelotasplus.eyeofbeholder.data.model.speakerFrom
import pl.pelotasplus.eyeofbeholder.data.model.spokenBy
import pl.pelotasplus.eyeofbeholder.data.model.teleportersInView
import pl.pelotasplus.eyeofbeholder.data.model.wallsInSight
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
import pl.pelotasplus.eyeofbeholder.data.model.itemIcon
import pl.pelotasplus.eyeofbeholder.data.model.toImageBitmap
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepository
import pl.pelotasplus.eyeofbeholder.data.repository.DialogueTextRepository
import pl.pelotasplus.eyeofbeholder.data.repository.FontRepository
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepository
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepository
import pl.pelotasplus.eyeofbeholder.data.repository.SaveSlot
import pl.pelotasplus.eyeofbeholder.data.repository.SavedGameRepository
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepository
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepository

@Stable
class ViewConeDebugViewModel(
    private val viewConeRepository: ViewConeRepository,
    private val cpsRepository: CpsRepository,
    private val dialogueTextRepository: DialogueTextRepository,
    private val fontRepository: FontRepository,
    private val originalSaveRepository: OriginalSaveRepository,
    private val savedGames: SavedGameRepository,
    private val itemTypesRepository: ItemTypesRepository,
    private val itemsRepository: ItemsRepository,
) : ViewModel() {

    private var playFieldBackground: Cps? = null
    private var decorations: Cps? = null
    private var dialogueFrame: Cps? = null
    private var portraits: Cps? = null
    private var invent: Cps? = null
    private var carriedItemIcons: Cps? = null
    private var itemTypes: ItemTypes? = null

    /** What every item in the game is called, which no save carries. */
    private var itemNames: ItemNames? = null

    /** Who the party are, as against [party], which is where they stand. */
    private var roster: List<Champion> = emptyList()
    private var font: Font? = null
    private var menuFont: Font? = null
    private var scriptRunner: LevelScriptRunner? = null
    private var speaker: DialogueScene.Picture? = null

    /** The script holding the world, if one is running. */
    private var playing: Job? = null

    /** Waiting for the party to stand still before writing where they are. */
    private var autosaving: Job? = null

    /** Redrawing the view for a teleporter's flicker, while one is in sight. */
    private var flickering: Job? = null
    private var pulse = TeleporterPulse.AS_LAID_OUT

    /** The view as last drawn, which a script's words are written over. */
    private var drawn: ViewPort? = null

    /** What a script asked, waiting on the click that answers it. */
    private val awaiting = PendingQuestion()

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun onEvent(event: Event) {
        // A script moves the party itself and holds the world while it does,
        // so steering is ignored until it lets go. Answering is not steering —
        // it is what a script asking a question is waiting for.
        if (playing?.isActive == true && event !is Event.DialogAnswered) {
            Logger.d(TAG) { "Ignoring $event while a script is playing" }
            return
        }

        // an open menu owns the screen: the party do not walk about behind it
        val menu = _state.value.menu
        if (menu != null && event !is Event.Initialize) {
            when {
                event is Event.Typed -> viewModelScope.launch { onTyping(event.typing) }

                event is Event.ClickedTheView -> menu.clicked(event.x, event.y)?.let { choice ->
                    viewModelScope.launch { onMenuChoice(choice) }
                }

                // naming has the keyboard, so Camp cannot pull the menu away
                event is Event.Camp && menu.naming == null -> showMenu(null)
            }
            return
        }

        when (event) {
            is Event.Initialize -> onInitialize(
                level = event.level,
                startX = event.startX,
                startY = event.startY,
                startDirection = event.startDirection,
            )

            is Event.DialogAnswered -> onDialogAnswered(event.answer)
            is Event.OnLevelSelected -> onVmpSelected(event.name)
            is Event.ClickedTheView -> onClickedTheView(event.x, event.y)
            Event.Camp -> onCamped()
            Event.MoveForward -> onMoveForward()
            Event.MoveBackwards -> onMoveBackwards()
            Event.StrafeLeft -> onStrafe(left = true)
            Event.StrafeRight -> onStrafe(left = false)
            Event.RotateRight -> onRotateRight()
            Event.RotateLeft -> onRotateLeft()

            // there is nothing to type into with no menu open
            is Event.Typed -> Unit
        }
    }

    /**
     * [level] is the INF picked from the Levels screen, or null to open the
     * default level at its hardcoded start position. A start coordinate left
     * null keeps the one the party already stands on.
     */
    private fun onInitialize(
        level: String?,
        startX: Int? = null,
        startY: Int? = null,
        startDirection: Direction? = null,
    ) {
        viewModelScope.launch {
            cpsRepository.loadCps(PLAY_FIELD_CPS)
                .onSuccess { playFieldBackground = it }
                .onFailure { Logger.e(it) { "Error while loading $PLAY_FIELD_CPS" } }
            cpsRepository.loadCps(DECORATIONS_CPS)
                .onSuccess { decorations = it }
                .onFailure { Logger.e(it) { "Error while loading $DECORATIONS_CPS" } }
            cpsRepository.loadCps(DIALOGUE_FRAME_CPS)
                .onSuccess { dialogueFrame = it }
                .onFailure { Logger.e(it) { "Error while loading $DIALOGUE_FRAME_CPS" } }
            fontRepository.loadFont(DIALOGUE_FONT)
                .onSuccess { font = it }
                .onFailure { Logger.e(it) { "Error while loading $DIALOGUE_FONT" } }
            fontRepository.loadFont(MENU_FONT)
                .onSuccess { menuFont = it }
                .onFailure { Logger.e(it) { "Error while loading $MENU_FONT" } }
            cpsRepository.loadCps(PORTRAITS_CPS)
                .onSuccess { portraits = it }
                .onFailure { Logger.e(it) { "Error while loading $PORTRAITS_CPS" } }
            cpsRepository.loadCps(INVENTORY_CPS)
                .onSuccess { invent = it }
                .onFailure { Logger.e(it) { "Error while loading $INVENTORY_CPS" } }
            cpsRepository.loadCps(CARRIED_ITEM_ICONS_CPS)
                .onSuccess { carriedItemIcons = it }
                .onFailure { Logger.e(it) { "Error while loading $CARRIED_ITEM_ICONS_CPS" } }
            itemTypesRepository.loadItemTypes()
                .onSuccess { itemTypes = it }
                .onFailure { Logger.e(it) { "Error while loading the item types" } }
            itemsRepository.loadItems()
                .onSuccess { itemNames = it.names }
                .onFailure { Logger.e(it) { "Error while loading the item names" } }
            // a level picked from the Levels screen is an instruction, so it
            // wins over wherever the party were last left
            val resumed = if (level != null || !AUTOSAVES) {
                null
            } else {
                savedGames.load(SaveSlot.AUTOSAVE).getOrNull()
            }
            if (resumed == null) resumeNothing() else resume(resumed)

            onVmpSelected(
                name = resumed?.let { "LEVEL${it.level}.INF" } ?: level ?: DEFAULT_LEVEL,
                subLevel = resumed?.subLevel ?: 0,
                playerX = resumed?.world?.party?.position?.x ?: startX ?: DEFAULT_PLAYER_X,
                playerY = resumed?.world?.party?.position?.y ?: startY ?: DEFAULT_PLAYER_Y,
                direction = resumed?.world?.party?.facing ?: startDirection ?: DEFAULT_DIRECTION,
            )
        }
    }

    /** Picks the game up where the autosave left it. */
    private suspend fun resume(saved: SavedGame) {
        Logger.i(TAG) { "Resuming ${saved.description} on level ${saved.level}" }
        roster = saved.champions
        _state.update {
            it.copy(
                game = GameState.restoredFrom(saved.world, on = saved.level),
                messages = saved.messages,
            )
        }

        // a save written before the items were part of the world has none, and
        // its champions would be carrying nothing they could be asked about
        if (saved.world.items.isEmpty()) itemsFromTheQuickStart()
    }

    /**
     * No autosave to pick up, so the party are the ones the game ships with —
     * and so are their belongings. The quick start party's own gear lives past
     * the end of ITEM.DAT, in the save's table, which is why the items come
     * from there too and not from the file.
     */
    private suspend fun resumeNothing() {
        val save = quickStart() ?: return
        roster = save.party
        carry(save)
    }

    private suspend fun itemsFromTheQuickStart() {
        quickStart()?.let(::carry)
    }

    private suspend fun quickStart(): OriginalSave? =
        originalSaveRepository.loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START)
            .onFailure { Logger.e(it) { "Error while loading the quick start party" } }
            .getOrNull()

    private fun carry(save: OriginalSave) {
        _state.update {
            it.copy(game = it.game.copy(items = save.items, inHand = save.inHand))
        }
    }

    /**
     * Writes where the party have got to, so closing the tab and coming back
     * finds them there.
     *
     * Held down, an arrow key is one move as far as this is concerned: each
     * call cancels the last, so a run down a corridor writes once at the end
     * rather than once a square. A heavily played world is eighty kilobytes of
     * JSON, which is cheap once and not cheap thirty times a second.
     */
    private fun autosave() {
        if (!AUTOSAVES) return
        val inf = _state.value.inf ?: return

        autosaving?.cancel()
        autosaving = viewModelScope.launch {
            delay(AUTOSAVE_SETTLES.inMilliseconds)
            savedGames.save(
                slot = SaveSlot.AUTOSAVE,
                description = inf.name.removeSuffix(".INF"),
                savedAt = rightNow(),
                level = levelNumber(inf.name),
                subLevel = _state.value.subLevel,
                champions = roster,
                world = _state.value.game,
                messages = _state.value.messages,
            ).onFailure { Logger.e(it) { "Could not autosave" } }
        }
    }

    private fun onVmpSelected(
        name: String,
        subLevel: Int = 0,
        playerX: Int? = null,
        playerY: Int? = null,
        direction: Direction? = null
    ) {
        viewModelScope.launch {
            viewConeRepository
                .loadLevel(name = name)
                .onSuccess { inf ->
                    val arrivingAt = levelNumber(inf.name)
                    val showing = subLevel.coerceIn(inf.subLevels.indices)
                    scriptRunner = LevelScriptRunner(inf.script, arrivingAt, showing)
                    _state.update {
                        val was = it.game.party
                        val leftBehind =
                            it.inf?.let { open -> it.game.leaving(levelNumber(open.name)) }

                        it.copy(
                            inf = inf,
                            subLevel = showing,
                            game = (leftBehind ?: it.game)
                                .arrivingAt(
                                    level = arrivingAt,
                                    places = inf.monsterInstances,
                                    maz = inf.subLevels[showing].maz,
                                    subLevel = showing,
                                )
                                .copy(
                                    party = was.copy(
                                        position = Location(
                                            x = playerX ?: was.position.x,
                                            y = playerY ?: was.position.y,
                                        ),
                                        facing = direction ?: was.facing,
                                    ),
                                ),
                        )
                    }
                    renderViewPort()
                    autosave()
                }
                .onFailure {
                    Logger.e(it) { "Error while loading vmp: $name" }
                }
        }
    }

    private fun onPlayerPositionChanged(x: Int? = null, y: Int? = null) {
        check(x != null || y != null) {
            "Either x or y must be non-null"
        }
        val steppedTo = Location(
            x = (x ?: party.position.x).coerceAtLeast(0),
            y = (y ?: party.position.y).coerceAtLeast(0),
        )
        _state.update { it.copy(game = it.game.partyMovedTo(steppedTo)) }

        // A square with something to say draws for itself, and may draw
        // something other than the view — putting the new position up first
        // shows the party standing where the script is about to explain.
        if (!runTriggers()) {
            renderViewPort()
            autosave()
        }
    }

    private val party get() = _state.value.game.party

    /**
     * Whichever champion answers this time. Rolled per line, the way the
     * original does, so two remarks in a row need not come from one mouth.
     */
    private fun whoeverSpeaks(): Champion? =
        roster.speakerFrom(Random.nextInt(Champion.PARTY_SLOTS))

    /** Opens the camp menu, or shuts it if it is already open. */
    private fun onCamped() {
        val opening = _state.value.menu == null

        viewModelScope.launch {
            // camping puts a champion's page down and the party back up
            _state.update { it.copy(sheet = null) }
            showMenu(if (opening) CampMenu.camp() else null)
        }
    }

    private suspend fun onMenuChoice(choice: MenuChoice) {
        when (choice) {
            MenuChoice.Close -> showMenu(null)
            MenuChoice.OpenCamp -> showMenu(CampMenu.camp())
            MenuChoice.OpenGameOptions -> showMenu(CampMenu.gameOptions())
            MenuChoice.OpenPreferences -> showMenu(CampMenu.preferences(_state.value.preferences))

            // the line that was clicked is the setting, so the menu is put up
            // again for it to say the other thing
            is MenuChoice.Toggle -> {
                _state.update { it.copy(preferences = it.preferences.toggling(choice.setting)) }
                showMenu(CampMenu.preferences(_state.value.preferences))
            }

            is MenuChoice.OpenSlots -> showSlots(choice.saving)
            is MenuChoice.UseSlot ->
                if (!choice.saving) loadFrom(choice.slot)
                else if (canTypeIntoTheGame) startNaming(choice.slot) else saveTo(choice.slot, suggestedName())
            is MenuChoice.NotYet -> Logger.i(TAG) { "${choice.what} is not implemented" }
        }
    }

    private suspend fun showSlots(saving: Boolean) {
        val saved = savedGames.saved()
        showMenu(
            CampMenu.slots(saving) { slot ->
                saved[SaveSlot.numbered[slot]]?.description
            }
        )
    }

    private fun showMenu(menu: CampMenu?) {
        _state.update { it.copy(menu = menu) }
        drawWords()
    }

    /**
     * Puts the caret on a slot's row, starting from what is already there or,
     * for an empty one, from where the party stand. Pressing return alone is
     * then a sensible name rather than a refusal.
     */
    private suspend fun startNaming(slot: Int) {
        val existing = savedGames.saved()[SaveSlot.numbered[slot]]?.description

        _state.update {
            it.copy(
                menu = it.menu?.copy(
                    naming = Naming(slot, existing ?: suggestedName()),
                )
            )
        }
        drawWords()
    }

    private suspend fun onTyping(typing: Typing) {
        val naming = _state.value.menu?.naming ?: return

        when (typing) {
            Typing.Abandon -> showSlots(saving = true)
            Typing.Accept -> if (naming.nameable) saveTo(naming.slot, naming.typed) else Unit
            else -> {
                _state.update { it.copy(menu = it.menu?.copy(naming = naming.after(typing))) }
                drawWords()
            }
        }
    }

    /** Where the party are, which is what tells two saves of one level apart. */
    private fun suggestedName(): String {
        val inf = _state.value.inf ?: return "SAVE"
        return "${inf.name.removeSuffix(".INF")} ${party.position.x}x${party.position.y}"
    }

    private suspend fun saveTo(slot: Int, description: String) {
        val inf = _state.value.inf ?: return

        savedGames.save(
            slot = SaveSlot.numbered[slot],
            description = description,
            savedAt = rightNow(),
            level = levelNumber(inf.name),
            subLevel = _state.value.subLevel,
            champions = roster,
            world = _state.value.game,
            messages = _state.value.messages,
        ).onFailure { Logger.e(it) { "Could not save" } }

        showMenu(null)
    }

    private suspend fun loadFrom(slot: Int) {
        val saved = savedGames.load(SaveSlot.numbered[slot])
            .onFailure { Logger.e(it) { "Could not load slot $slot" } }
            .getOrNull() ?: return

        resume(saved)
        showMenu(null)
        onVmpSelected(
            name = "LEVEL${saved.level}.INF",
            subLevel = saved.subLevel,
            playerX = saved.world.party.position.x,
            playerY = saved.world.party.position.y,
            direction = saved.world.party.facing,
        )
    }

    /**
     * A click anywhere on the play field that no button claimed.
     *
     * An open page has first refusal, then the six faces — clicking one opens
     * its owner's page — and what is left is the 3D view.
     */
    private fun onClickedTheView(x: Int, y: Int) {
        val sheet = sheetOnShow

        if (sheet == null) {
            val hand = handAt(x, y)
            if (hand != null) {
                swapHandWith(hand.first, inventorySlotPositions[hand.second])
                return
            }

            val face = championBoxes.indexOfFirst { it.showsFaceAt(x, y) }
            if (face >= 0) {
                if (roster.getOrNull(face)?.inTheParty == true) showSheet(CharacterSheet(face))
                return
            }
        } else {
            sheet.clicked(x, y)?.let { choice ->
                onSheetChoice(sheet, choice)
                return
            }

            // an open page puts all twenty-seven of a champion's slots within
            // reach, the two hands among them
            if (sheet.page == CharacterSheet.Page.BELONGINGS) {
                inventorySlotAt(x, y)?.let { slot ->
                    swapHandWith(sheet.slot, slot)
                    return
                }
            }
        }

        if (x >= ViewPort.COLS || y >= ViewPort.ROWS) return

        // A piece of floor answers a click only when there is something to do
        // with it — otherwise the click is the wall's, so a lever low on one
        // stays clickable with an empty hand.
        FloorReach.at(x, y)?.let { reach ->
            if (reachedInto(reach)) return
        }

        onClickedTheWorld(x, y)
    }

    /** Which champion's which hand a click landed on, if it landed on one. */
    private fun handAt(x: Int, y: Int): Pair<Int, Int>? {
        championBoxes.forEachIndexed { slot, box ->
            if (roster.getOrNull(slot)?.inTheParty != true) return@forEachIndexed

            repeat(Champion.HANDS) { hand ->
                if (box.holdsHandAt(x, y, hand)) return slot to hand
            }
        }
        return null
    }

    /**
     * Swaps what is being held with what is in one of a champion's slots.
     *
     * With an empty hand that is taking what was there, and with an empty slot
     * it is putting something down; the original does not tell the three cases
     * apart, and neither does this.
     */
    private fun swapHandWith(champion: Int, slot: InventorySlot) {
        val who = roster.getOrNull(champion) ?: return
        val world = _state.value.game
        val inSlot = who.carrying.getOrNull(slot.slot) ?: return

        if (slot.isQuiver) {
            useQuiver(champion, slot, inSlot)
            return
        }

        val refused = itemTypes?.willSwap(
            champion = who,
            slot = slot,
            held = world.held,
            inSlot = world.item(inSlot),
        ) == false

        if (refused) {
            say(ItemMessages.WILL_NOT_GO_THERE)
            // Nothing moved, so nothing else will draw and the line would sit
            // unseen until something did. Only the words are new.
            drawWords()
            return
        }

        carried(champion, slot.slot, world.inHand)
        _state.update { it.copy(game = world.holding(inSlot)) }
        announceTaking(world.item(inSlot))
        renderViewPort()
    }

    /**
     * The quiver, which holds a stack rather than one thing. An arrow in the
     * hand joins the ones already in it and the tally goes up; an empty hand
     * takes one back out and the tally goes down.
     */
    private fun useQuiver(champion: Int, slot: InventorySlot, head: ItemIndex) {
        val world = _state.value.game

        val stacked = if (world.inHand.isSomething) {
            if (itemTypes?.mayGoIn(slot.takes, world.held) == false) {
                say(ItemMessages.WILL_NOT_GO_THERE)
                drawWords()
                return
            }
            world.stacking(head)
        } else {
            if (!head.isSomething) return
            announceTaking(world.item(head))
            world.unstacking(head)
        }

        carried(champion, slot.slot, stacked.head)
        _state.update { it.copy(game = stacked.world) }
        renderViewPort()
    }

    /** The same roster with one of a champion's slots holding something else. */
    private fun carried(champion: Int, slot: Int, item: ItemIndex) {
        val who = roster.getOrNull(champion) ?: return

        roster = roster.toMutableList().also {
            it[champion] = who.copy(
                carrying = who.carrying.toMutableList().also { carrying -> carrying[slot] = item },
            )
        }
    }

    /** Whatever comes into the hand says what it is, the way the original does. */
    private fun announceTaking(item: Item?) {
        val names = itemNames ?: return
        if (item == null) return

        say(ItemMessages.taken(names.of(item, itemTypes)))
    }

    private fun say(line: String) {
        _state.update {
            it.copy(
                messages = (it.messages + PlayField.Message(line, ScriptSpeech.DEFAULT_INK))
                    .takeLast(MESSAGES_KEPT),
            )
        }
    }

    /**
     * Puts down what is being held on a piece of floor, or picks up what is
     * lying there.
     *
     * @return false when there was nothing to do, so the click belongs to
     *   whatever is behind the floor.
     */
    private fun reachedInto(reach: FloorReach): Boolean {
        val inf = _state.value.inf ?: return false
        val level = levelNumber(inf.name)
        val world = _state.value.game

        val at = if (!reach.aheadOfTheParty) party.position else {
            val (dx, dy) = party.facing.transformCoordinates(0, -1)
            Location(party.position.x + dx, party.position.y + dy)
        }

        // nothing can be put through the wall a square turns towards the party
        if (reach.aheadOfTheParty && !canReachOnto(inf, level, at)) return false

        val quadrant = reach.quadrantFacing(party.facing)

        val changed = if (world.inHand.isSomething) {
            world.puttingDown(level, at, quadrant)
        } else {
            val lying = world.lyingAt(level, at, quadrant) ?: return false
            announceTaking(world.item(lying))
            world.takingUp(lying)
        }

        _state.update { it.copy(game = changed) }
        // A square with something to say about what was left on it draws for
        // itself, the way it does when the party step onto it.
        val told = runTriggersAt(
            at = at,
            event = if (world.inHand.isSomething) ScriptEvent.ITEM_PUT_DOWN
            else ScriptEvent.ITEM_TAKEN,
        )
        if (!told) renderViewPort()
        return true
    }

    /** Whether the party can reach onto the square in front to put a thing on it. */
    private fun canReachOnto(inf: Inf, level: Int, at: Location): Boolean {
        val facingUs = party.facing.transformWallSide(WallSide.SOUTH)
        val sublevel = inf.subLevels[_state.value.subLevel]
        return sublevel.canBeReachedOnto(_state.value.game.wall(level, at, facingUs))
    }

    private fun onSheetChoice(sheet: CharacterSheet, choice: SheetChoice) {
        showSheet(
            when (choice) {
                SheetChoice.Close -> null
                SheetChoice.TurnPage -> sheet.turnedOver
                is SheetChoice.Walk -> sheet.walked(choice.step, roster)
            }
        )
    }

    private fun showSheet(sheet: CharacterSheet?) {
        _state.update { it.copy(sheet = sheet) }
        drawWords()
    }

    /**
     * Whose page is on show. A page of a slot nobody fills is no page at all —
     * it would draw as the party panel while still swallowing every click, and
     * there would be no way back to a champion.
     */
    private val sheetOnShow: CharacterSheet?
        get() = _state.value.sheet?.takeIf { roster.getOrNull(it.slot)?.inTheParty == true }

    /** The champion's page as it is to be drawn, or null with none on show. */
    private fun openSheet(): OpenSheet? {
        val sheet = sheetOnShow ?: return null
        val champion = roster[sheet.slot]

        val world = _state.value.game

        return OpenSheet(
            page = sheet.page,
            champion = champion,
            carrying = champion.carrying.map { world.item(it) },
            arrows = champion.carrying.getOrNull(InventorySlot.QUIVER)
                ?.let { world.stackedIn(it) } ?: 0,
        )
    }

    /**
     * A click in the view means the wall of the square ahead that faces the
     * party, whatever part of the view it landed on. Where it landed decides
     * only whether it hit what hangs there.
     */
    private fun onClickedTheWorld(x: Int, y: Int) {
        val inf = _state.value.inf ?: return
        val sublevel = inf.subLevels[_state.value.subLevel]

        val (dx, dy) = party.facing.transformCoordinates(0, -1)
        val ahead = Location(party.position.x + dx, party.position.y + dy)
        val facingUs = party.facing.transformWallSide(WallSide.SOUTH)

        // the world's wall, not the file's: a wall a script has already opened
        // is no longer the one with the button on it
        val wall = _state.value.game.wall(levelNumber(inf.name), ahead, facingUs)
        if (wall !is Maz.WallType.Decoration) return

        val decoration = sublevel.decorations
            .firstOrNull { it.decorationWallIndex == wall.decorationWallIndex }
            ?: return

        val hanging = decoration.dec.decorations
            .firstOrNull { it.index == decoration.decorationID }

        val does = decoration.doesWhenClicked

        // Some walls have nothing to aim at and answer a click anywhere on
        // them; the rest want the thing hanging there hit.
        val hit = does.answersAnyClick ||
            (hanging != null && ClickedWall.hits(hanging, decoration.dec.rectangles, x, y))

        Logger.d(TAG) { "Clicked $ahead $facingUs is a $does, hit=$hit" }
        if (!hit) return

        // A niche is worked rather than merely triggered: what is shelved in
        // it is taken, or what is held is put on it.
        if (does == WallAction.NICHE) {
            reachedIntoNiche(levelNumber(inf.name), ahead)
            return
        }

        runTriggersAt(ahead, ScriptEvent.WALL_CLICKED)
    }

    /**
     * The shelf set into a wall: what is on it comes into an empty hand, and
     * what is held goes onto it — but only if it is one of the small shapes,
     * a niche having no room for anything bigger.
     */
    private fun reachedIntoNiche(level: Int, at: Location) {
        val world = _state.value.game

        val changed = if (!world.inHand.isSomething) {
            val shelved = world.lyingAt(level, at, IN_A_NICHE) ?: return
            announceTaking(world.item(shelved))
            world.takingUp(shelved)
        } else {
            if (Cps.shapeOf(world.held!!.icon) !is Cps.ShapeLocation.SmallItem) {
                say(ItemMessages.TOO_LARGE_TO_FIT)
                drawWords()
                return
            }
            world.puttingDown(level, at, IN_A_NICHE)
        }

        _state.update { it.copy(game = changed) }

        val told = runTriggersAt(
            at = at,
            event = if (world.inHand.isSomething) ScriptEvent.ITEM_PUT_DOWN
            else ScriptEvent.ITEM_TAKEN,
        )
        if (!told) renderViewPort()
    }

    /**
     * Plays the script of the square the party has stepped onto, which holds
     * the world for as long as it runs — it may walk the party about and wait
     * between steps, and nothing else may move meanwhile.
     *
     * @return false when there is no level to ask, so the caller still has the
     *   view to draw.
     */
    private fun runTriggers(): Boolean = runTriggersAt(party.position, ScriptEvent.PARTY_ENTERED)

    private fun runTriggersAt(at: Location, event: ScriptEvent): Boolean {
        val inf = _state.value.inf ?: return false
        val runner = scriptRunner ?: return false

        playing?.cancel()
        playing = viewModelScope.launch {
            val run = runner.onEvent(
                triggers = inf.triggers,
                event = event,
                state = _state.value.game,
                stage = stage,
                at = at,
            )
            // Whatever the script left on screen goes with it. Scripts end
            // without closing the box they last wrote in — the one that walks
            // the party downstairs says so and changes level on the next
            // instruction — and a box with nothing to click cannot be got rid
            // of by the player.
            _state.update { it.copy(game = run.state, dialog = null) }
            speaker = null

            val change = run.changeLevel
            if (change == null) {
                drawViewPort()
                autosave()
            } else {
                Logger.i(TAG) {
                    "Changing to level ${change.level} sublevel ${change.subLevel} " +
                        "at ${change.location}"
                }
                onVmpSelected(
                    name = "LEVEL${change.level}.INF",
                    subLevel = change.subLevel,
                    playerX = change.location.x,
                    playerY = change.location.y,
                    direction = change.direction,
                )
            }
        }
        return true
    }

    /**
     * The screen, from a running script's side.
     *
     * A script draws, waits and asks where it stands, so this is what it draws
     * on, what holds its pauses, and what its question is put up as.
     */
    private val stage = object : ScriptStage {

        override suspend fun show(world: GameState) {
            _state.update { it.copy(game = world) }
            drawViewPort()
        }

        /**
         * The box a question would be asked in, with no question in it: the
         * script has written a line and will hold the screen while it is read.
         */
        override suspend fun say(speech: ScriptSpeech) {
            val inf = _state.value.inf ?: return

            if (speech.isEmpty) {
                _state.update { it.copy(dialog = null) }
                speaker = null
                drawWords()
                return
            }

            val spoken = speech.said.mapNotNull { inf.message(it) }
                .filter { it.isNotBlank() }
                .joinToString("\n") { it.spokenBy(whoeverSpeaks()) }

            // With no box open the line belongs on the bar along the bottom,
            // which is where a script talks to the party when nobody is
            // speaking to them.
            if (speech.scene.isEmpty()) {
                _state.update {
                    it.copy(
                        dialog = null,
                        messages = (it.messages + PlayField.Message(spoken, speech.colour))
                            .takeLast(MESSAGES_KEPT),
                    )
                }
                speaker = null
                drawWords()
                return
            }

            _state.update {
                it.copy(
                    dialog = DialogPrompt(
                        scene = sceneFor(
                            scene = speech.scene,
                            text = spoken,
                            buttonLabels = emptyList(),
                            waitsToBeRead = false,
                        ),
                    )
                )
            }
            drawWords()
        }

        override suspend fun hold(ticks: Ticks) = delay(ticks.inMilliseconds)

        override suspend fun ask(question: ScriptQuestion): DialogAnswer {
            Logger.i(TAG) { "Script is showing text ${question.textId} with ${question.buttons}" }

            return awaiting.ask { showDialog(question) }
        }
    }

    /**
     * A script stopped to ask something. The speech comes from TEXT.DAT and
     * the button words from the level's own messages.
     */
    private suspend fun showDialog(question: ScriptQuestion) {
        val inf = _state.value.inf ?: return

        val speech = dialogueTextRepository.text(question.textId)
            .onFailure { Logger.e(it) { "No dialogue text ${question.textId}" } }
            .getOrNull()
            ?: DialogueText.EMPTY

        val labels = question.buttons.mapNotNull { inf.message(it) }
        val unread = speech.pages.drop(1)

        // the party's own line goes in the box above what it answers
        val spoken = (question.said.mapNotNull { inf.message(it) } + speech.first)
            .filter { it.isNotBlank() }
            .joinToString("\n") { it.spokenBy(whoeverSpeaks()) }

        _state.update {
            it.copy(
                dialog = DialogPrompt(
                    scene = sceneFor(
                        scene = question.scene,
                        text = spoken,
                        buttonLabels = if (unread.isEmpty()) labels else listOf(MORE),
                        waitsToBeRead = unread.isNotEmpty() || question.waitsToBeRead,
                    ),
                    unread = unread,
                    buttons = labels,
                    waitsToBeRead = question.waitsToBeRead,
                )
            )
        }
        drawWords()
    }

    /**
     * Turns the script's drawing instructions into something the play field can
     * put on screen.
     *
     * Speakers are packed four to a file, and the instruction's x and y name the
     * corner to cut out — x in units of eight pixels, as the original counts
     * them — and its rect says which of the two places it goes.
     */
    private suspend fun sceneFor(
        scene: List<Dialog>,
        text: String,
        buttonLabels: List<String>,
        waitsToBeRead: Boolean,
    ): DialogueScene {
        val font = font ?: return DialogueScene(null, null, emptyList(), emptyList())

        val instruction = scene.filterIsInstance<Dialog.DisplayPicture>().lastOrNull()
        val portrait = if (instruction == null) {
            // a reply draws no one: whoever is speaking stays up while they talk
            speaker
        } else {
            cpsRepository.loadCps("${instruction.pictureName.uppercase()}.CPS")
                .onFailure { error -> Logger.e(error) { "No picture ${instruction.pictureName}" } }
                .getOrNull()
                ?.let { cps ->
                    DialogueScene.Picture(
                        cps = cps,
                        sourceLeft = instruction.x * ViewPort.TILE_SIZE,
                        sourceTop = instruction.y,
                        goes = DialogueScene.PictureFrame.of(instruction.rect),
                    )
                }
        }
        speaker = portrait

        return DialogueScene.layout(
            frame = dialogueFrame,
            portrait = portrait,
            text = text,
            buttonLabels = buttonLabels,
            font = font,
            waitsToBeRead = waitsToBeRead,
        )
    }

    /** Puts the next part of a speech up, without letting the script move on. */
    private fun turnThePage(dialog: DialogPrompt) {
        val unread = dialog.unread.drop(1)

        viewModelScope.launch {
            _state.update {
                it.copy(
                    dialog = dialog.copy(
                        scene = sceneFor(
                            scene = emptyList(),
                            text = dialog.unread.first(),
                            buttonLabels = if (unread.isEmpty()) dialog.buttons else listOf(MORE),
                            waitsToBeRead = unread.isNotEmpty() || dialog.waitsToBeRead,
                        ),
                        unread = unread,
                    )
                )
            }
            renderViewPort()
        }
    }

    private fun onDialogAnswered(answer: DialogAnswer) {
        val dialog = _state.value.dialog ?: return

        if (dialog.unread.isNotEmpty()) {
            turnThePage(dialog)
            return
        }

        _state.update { it.copy(dialog = null) }
        awaiting.answer(answer)
    }

    private fun onDirectionChanged(direction: Direction) {
        _state.update { it.copy(game = it.game.partyTurnedTo(direction)) }
        renderViewPort()
        autosave()
    }

    private fun renderViewPort() {
        viewModelScope.launch { drawViewPort() }
    }

    /** Draws the view and waits for it, so a script can hold what it put up. */
    private suspend fun drawViewPort() {
        val inf = _state.value.inf ?: return
        val level = levelNumber(inf.name)
        val wallAt = { at: Location, side: WallSide -> _state.value.game.wall(level, at, side) }

        val sublevel = inf.subLevels[followTheWalls(inf, wallAt)]

        viewConeRepository.renderPosition(
            items = _state.value.game.items,
            monsters = _state.value.game.monsters,
            sublevel = sublevel,
            playerX = party.position.x,
            playerY = party.position.y,
            direction = party.facing,
            wallAt = wallAt,
            pulse = pulse,
        ).onSuccess { viewPort ->
            drawn = viewPort
            paint(viewPort, sublevel.palette)
        }.onFailure {
            Logger.e(it) { "Error while rendering position" }
        }

        keepFlickering(teleportersInView(party.position, party.facing, wallAt).isNotEmpty())
    }

    /**
     * Which sublevel to draw, having let the walls in sight correct it.
     *
     * Only walking through one can put the party somewhere no script sent
     * them, so this is a debugging affordance rather than the game's own rule
     * — but it costs a set comparison per frame and saves picking the sublevel
     * by hand every time.
     */
    private fun followTheWalls(inf: Inf, wallAt: (Location, WallSide) -> Maz.WallType): Int {
        val was = _state.value.subLevel
        val showing = inf.subLevelShowing(
            showing = was,
            sight = wallsInSight(party.position, party.facing, wallAt),
        )

        if (showing != was) {
            Logger.i(TAG) { "The walls at ${party.position} are sublevel $showing, not $was" }
            _state.update { it.copy(subLevel = showing) }
        }
        return showing
    }

    /**
     * A teleporter in sight is the one thing on screen that moves without the
     * party doing anything, so it needs a clock of its own — and must not have
     * one when there is none in sight, or the scene is redrawn for ever.
     */
    private fun keepFlickering(anyInSight: Boolean) {
        if (anyInSight == (flickering?.isActive == true)) return

        flickering?.cancel()
        flickering = if (!anyInSight) {
            null
        } else {
            viewModelScope.launch {
                while (true) {
                    delay(TELEPORTER_PULSE.inMilliseconds)
                    // a script owns the screen while it runs, and draws the
                    // frames it wants seen itself
                    if (playing?.isActive == true) continue

                    pulse = pulse.next
                    drawViewPort()
                }
            }
        }
    }

    /**
     * Puts a script's words up over the view already on screen.
     *
     * Writing does not redraw the world, and must not: a script writes as the
     * party arrive somewhere they are about to be taken out of, and the view
     * from a staircase they are walking into is not a view anyone is meant to
     * see. The original defers its redraw for the same reason.
     */
    private fun drawWords() {
        val inf = _state.value.inf ?: return
        val viewPort = drawn ?: return

        paint(viewPort, inf.subLevels[_state.value.subLevel].palette)
    }

    private fun paint(viewPort: ViewPort, palette: Palette) {
        val background = playFieldBackground
        val decorations = decorations

        val image = if (background != null && decorations != null) {
            PlayField(
                background = background,
                decorations = decorations,
                palette = palette,
                font = font,
                menuFont = menuFont,
                invent = invent,
                itemIcons = carriedItemIcons,
                itemTypes = itemTypes,
                preferences = _state.value.preferences,
            )
                .render(
                    viewPort = viewPort,
                    direction = party.facing,
                    dialogue = _state.value.dialog?.scene,
                    messages = _state.value.messages,
                    party = roster,
                    portraits = portraits,
                    menu = _state.value.menu,
                    sheet = openSheet(),
                    carrying = { _state.value.game.item(it) },
                )
                .toImageBitmap()
        } else {
            // the frame art failed to load; still show the raw view
            viewPort.toImageBitmap()
        }
        _state.update { it.copy(viewPort = image, held = heldIcon(palette)) }
    }

    /**
     * The icon of whatever is being held, which the screen draws under the
     * pointer. It is not part of the play field: it is over everything and
     * moves without anything else changing.
     */
    private fun heldIcon(palette: Palette): ImageBitmap? {
        val icons = carriedItemIcons ?: return null
        val held = _state.value.game.held ?: return null

        return icons.itemIcon(held.icon).toImageBitmap(icons.palette ?: palette)
    }

    private fun onStrafe(left: Boolean) {
        val direction = party.facing
        val sideways = if (left) {
            Direction.entries[(direction.ordinal + 3) % Direction.entries.size]
        } else {
            Direction.entries[(direction.ordinal + 1) % Direction.entries.size]
        }
        when (sideways) {
            Direction.NORTH -> onPlayerPositionChanged(y = party.position.y - 1)
            Direction.EAST -> onPlayerPositionChanged(x = party.position.x + 1)
            Direction.SOUTH -> onPlayerPositionChanged(y = party.position.y + 1)
            Direction.WEST -> onPlayerPositionChanged(x = party.position.x - 1)
        }
    }

    private fun onMoveForward() {
        when (party.facing) {
            Direction.NORTH -> {
                val newY = party.position.y - 1
                onPlayerPositionChanged(y = newY)
            }

            Direction.EAST -> {
                val newX = party.position.x + 1
                onPlayerPositionChanged(x = newX)
            }

            Direction.SOUTH -> {
                val newY = party.position.y + 1
                onPlayerPositionChanged(y = newY)
            }

            Direction.WEST -> {
                val newX = party.position.x - 1
                onPlayerPositionChanged(x = newX)
            }
        }
    }

    private fun onMoveBackwards() {
        when (party.facing) {
            Direction.NORTH -> {
                val newY = party.position.y + 1
                onPlayerPositionChanged(y = newY)
            }

            Direction.EAST -> {
                val newX = party.position.x - 1
                onPlayerPositionChanged(x = newX)
            }

            Direction.SOUTH -> {
                val newY = party.position.y - 1
                onPlayerPositionChanged(y = newY)
            }

            Direction.WEST -> {
                val newX = party.position.x + 1
                onPlayerPositionChanged(x = newX)
            }
        }
    }

    private fun onRotateRight() {
        val currentDirection = party.facing
        val directions = Direction.entries
        val currentIndex = directions.indexOf(currentDirection)
        val newIndex = (currentIndex + 1) % directions.size
        onDirectionChanged(directions[newIndex])
    }

    private fun onRotateLeft() {
        val currentDirection = party.facing
        val directions = Direction.entries
        val currentIndex = directions.indexOf(currentDirection)
        val newIndex = (currentIndex - 1 + directions.size) % directions.size
        onDirectionChanged(directions[newIndex])
    }

    sealed class Event {
        data class Initialize(
            val level: String?,
            val startX: Int? = null,
            val startY: Int? = null,
            val startDirection: Direction? = null,
        ) : Event()

        data class DialogAnswered(val answer: DialogAnswer) : Event()
        data class OnLevelSelected(val name: String) : Event()

        /** A click in the view, in its own 176 by 120 coordinates. */
        data class ClickedTheView(val x: Int, val y: Int) : Event()
        data object MoveForward : Event()
        data object MoveBackwards : Event()
        data object StrafeLeft : Event()
        data object StrafeRight : Event()
        data object RotateRight : Event()
        data object RotateLeft : Event()
        data object Camp : Event()

        /** A key press, while a save is being named. */
        data class Typed(val typing: Typing) : Event()
    }

    /** A question a script is waiting on, drawn as [scene] over the play field. */
    data class DialogPrompt(
        val scene: DialogueScene,
        /**
         * What the speaker has not said yet. While there is more, the button
         * turns the page instead of letting the script carry on.
         */
        val unread: List<String> = emptyList(),
        /** The real buttons, kept aside until the last page is on screen. */
        val buttons: List<String> = emptyList(),
        /** True while the speech is being read rather than answered. */
        val waitsToBeRead: Boolean = false,
    )

    data class State(
        val inf: Inf? = null,

        /**
         * Which of the level's sublevels the party are in. Nothing in the maze
         * says: a script puts them in one, and walking somewhere no script
         * sent them is settled by [Inf.subLevelShowing].
         */
        val subLevel: Int = 0,

        val dialog: DialogPrompt? = null,

        /** The camp menu, if it is open, which owns the screen while it is. */
        val menu: CampMenu? = null,

        /**
         * Whose page is open, if anyone's. It stands where the party boxes go
         * and leaves the rest of the screen alone, so the party can be walked
         * about with a champion's things on show.
         */
        val sheet: CharacterSheet? = null,

        /** What the player has chosen under Camp, which no save carries. */
        val preferences: Preferences = Preferences(),

        /**
         * The bar along the bottom, oldest first. A script writes here when it
         * has no box open, and nothing takes a line off again — the bar scrolls
         * as more arrive.
         */
        val messages: List<PlayField.Message> = emptyList(),

        val viewPort: ImageBitmap? = null,

        /** What is being held, drawn under the pointer rather than on the field. */
        val held: ImageBitmap? = null,

        /**
         * The world, as the scripts see it. There is one, and it is this: a
         * script is handed it and hands back what it changed.
         */
        val game: GameState = GameState(
            party = PartyState(
                position = Location(DEFAULT_PLAYER_X, DEFAULT_PLAYER_Y),
                facing = DEFAULT_DIRECTION,
            )
        ),
    )

    companion object {
        private const val TAG = "ViewConeDebugViewModel"

        /** More than the bar can show, so a long line still has its history. */
        private const val MESSAGES_KEPT = 8

        private const val PLAY_FIELD_CPS = "PLAYFLD.CPS"
        private const val DECORATIONS_CPS = "DECORATE.CPS"
        private const val DIALOGUE_FRAME_CPS = "BORDER.CPS"
        private const val DIALOGUE_FONT = "FONT6.FNT"

        /** The bigger one the interface is set in. */
        private const val MENU_FONT = "FONT8.FNT"
        private const val PORTRAITS_CPS = "CHARGENA.CPS"
        private const val INVENTORY_CPS = "INVENT.CPS"

        /** Carried items are drawn from a sheet of their own, not the floor's. */
        private const val CARRIED_ITEM_ICONS_CPS = "ITEMICN.CPS"

        /**
         * Whether the game writes where the party got to and picks it up again
         * next time.
         *
         * Off while the renderer is being worked on: an autosave puts the party
         * back where they were, and walking to a position someone has reported
         * wants the opposite. The named slots are unaffected.
         */
        private const val AUTOSAVES = false

        /** How long the party must stand still before where they are is written. */
        private val AUTOSAVE_SETTLES = Ticks(9)

        /** Where a new party begin. */
        private const val DEFAULT_LEVEL = "LEVEL4.INF"
        private const val DEFAULT_PLAYER_X = 11
        private const val DEFAULT_PLAYER_Y = 5
        private val DEFAULT_DIRECTION = Direction.SOUTH
    }
}
