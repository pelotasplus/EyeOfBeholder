package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import pl.pelotasplus.eyeofbeholder.data.model.CampMenu
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PaletteIndex
import pl.pelotasplus.eyeofbeholder.data.model.CharacterSheet
import pl.pelotasplus.eyeofbeholder.data.model.OpenSheet
import pl.pelotasplus.eyeofbeholder.data.model.SheetChoice
import pl.pelotasplus.eyeofbeholder.data.model.championBoxes
import pl.pelotasplus.eyeofbeholder.data.model.MenuChoice
import pl.pelotasplus.eyeofbeholder.data.model.Naming
import pl.pelotasplus.eyeofbeholder.data.model.Typing
import pl.pelotasplus.eyeofbeholder.data.model.canTypeIntoTheGame
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene.Companion.MORE
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene.Companion.OK
import pl.pelotasplus.eyeofbeholder.data.model.OnAParchment
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.DialogueText
import pl.pelotasplus.eyeofbeholder.data.model.DamageShown
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Blow
import pl.pelotasplus.eyeofbeholder.data.model.Burst
import pl.pelotasplus.eyeofbeholder.data.model.Fighting
import pl.pelotasplus.eyeofbeholder.data.model.THROWN_CPS
import pl.pelotasplus.eyeofbeholder.data.model.HandRecovering
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPose
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.NpcId
import pl.pelotasplus.eyeofbeholder.data.model.NpcMeeting
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPathing
import pl.pelotasplus.eyeofbeholder.data.model.MonsterStepping
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.DoorMessages
import pl.pelotasplus.eyeofbeholder.data.model.DoorSounds
import pl.pelotasplus.eyeofbeholder.data.model.FloorClocks
import pl.pelotasplus.eyeofbeholder.data.model.FloorReach
import pl.pelotasplus.eyeofbeholder.data.model.ForcingADoor
import pl.pelotasplus.eyeofbeholder.data.model.Font
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HandUse
import pl.pelotasplus.eyeofbeholder.data.model.ThePortal
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.InventorySlot
import pl.pelotasplus.eyeofbeholder.data.model.inventorySlotAt
import pl.pelotasplus.eyeofbeholder.data.model.inventorySlotPositions
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.WallAction
import pl.pelotasplus.eyeofbeholder.data.model.doesWhenClicked
import pl.pelotasplus.eyeofbeholder.data.model.inTheFrontRank
import pl.pelotasplus.eyeofbeholder.data.model.ChampionMessages
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.ItemMessages
import pl.pelotasplus.eyeofbeholder.data.model.Potion
import pl.pelotasplus.eyeofbeholder.data.model.ItemNames
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.OriginalSave
import pl.pelotasplus.eyeofbeholder.data.model.ClickedWall
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.worthNoticing
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.WhatABlowLeaves
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.OnAPlate
import pl.pelotasplus.eyeofbeholder.data.model.Palette
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PlayField
import pl.pelotasplus.eyeofbeholder.data.model.Debugging
import pl.pelotasplus.eyeofbeholder.data.model.Preferences
import pl.pelotasplus.eyeofbeholder.data.model.Rest
import pl.pelotasplus.eyeofbeholder.data.model.Resting
import pl.pelotasplus.eyeofbeholder.data.model.anybodyCanStillMend
import pl.pelotasplus.eyeofbeholder.data.model.anybodyStillHurt
import pl.pelotasplus.eyeofbeholder.data.model.anybodyStarving
import pl.pelotasplus.eyeofbeholder.data.model.starvedADay
import pl.pelotasplus.eyeofbeholder.data.model.somebodyRanOutOfFood
import pl.pelotasplus.eyeofbeholder.data.model.HOURS_A_STARVED_POINT
import pl.pelotasplus.eyeofbeholder.data.model.HOURS_A_MENDED_POINT
import pl.pelotasplus.eyeofbeholder.data.model.sleptAnHour
import pl.pelotasplus.eyeofbeholder.data.model.SoundBank
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.model.Volume
import kotlin.math.abs
import pl.pelotasplus.eyeofbeholder.data.model.SavedGame
import pl.pelotasplus.eyeofbeholder.data.model.rightNow
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptTimer
import pl.pelotasplus.eyeofbeholder.data.model.ScriptQuestion
import pl.pelotasplus.eyeofbeholder.data.model.ScriptSpeech
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStage
import pl.pelotasplus.eyeofbeholder.data.model.TELEPORTER_PULSE
import pl.pelotasplus.eyeofbeholder.data.model.TeleporterPulse
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.levelNumber
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.canBeReachedOnto
import pl.pelotasplus.eyeofbeholder.data.model.canBeWalkedOnto
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
import pl.pelotasplus.eyeofbeholder.data.repository.AudioSink
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepository
import pl.pelotasplus.eyeofbeholder.data.repository.PlayingSound
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepository
import pl.pelotasplus.eyeofbeholder.data.repository.SaveSlot
import pl.pelotasplus.eyeofbeholder.data.repository.SavedGameRepository
import pl.pelotasplus.eyeofbeholder.data.repository.SoundRepository
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
    private val soundRepository: SoundRepository,
    private val audioSink: AudioSink,
    private val debugging: Debugging,
) : ViewModel() {

    private var playFieldBackground: Cps? = null
    private var decorations: Cps? = null
    private var dialogueFrame: Cps? = null
    private var portraits: Cps? = null
    private var metPortraits: Cps? = null
    private var invent: Cps? = null
    private var carriedItemIcons: Cps? = null

    /** The sheet the splash a blow is reported on is cut from. */
    private var thrownShapes: Cps? = null
    private var itemTypes: ItemTypes? = null

    /** What every item in the game is called, which no save carries. */
    private var itemNames: ItemNames? = null

    private var font: Font? = null
    private var menuFont: Font? = null
    private var scriptRunner: LevelScriptRunner? = null
    private var speaker: DialogueScene.Picture? = null

    private var drawnFrom: Pair<String, Cps>? = null

    /**
     * The speeches standing in the dialogue box, which stay in it until the
     * script draws the box again.
     *
     * Saying a thing and waiting for it to be acknowledged are two
     * instructions, and the second of them usually has nothing of its own to
     * say — so without this the words would go up and then be wiped by the
     * button that answers them.
     */
    private var standingInTheBox = emptyList<String>()

    /** The archway standing over the view, while one is opening. */
    private var portalShowing: ThePortal.Showing? = null

    /** The script holding the world, if one is running. */
    private var playing: Job? = null

    /**
     * Whether the script running has taken the party over.
     *
     * A script owns the world it is changing, but not the party standing in
     * it — they can walk away from a door it is opening. Once it moves or
     * turns them the two cannot both steer, and it wins until it ends.
     */
    private var scriptHasTheParty = false

    /** Waiting for the party to stand still before writing where they are. */
    private var autosaving: Job? = null

    /** Redrawing the view for a teleporter's flicker, while one is in sight. */
    private var flickering: Job? = null

    /** Moving whatever doors are on their way somewhere. */
    private var swingingDoors: Job? = null

    /** Flickering the name of whoever is waiting to change places. */
    private var flickeringName: Job? = null

    /** Counting whatever hands have swung back to rest. */
    private var recoveringHands: Job? = null
    private var fadingDamage: Job? = null
    private var burning: Job? = null

    /** Taking the silhouette off whatever was struck. */
    private var fading: Job? = null

    /** The clock the party's step and the monsters' turns are wound by. */
    private var fighting: Job? = null

    /**
     * How much of the party's own step is still to run.
     *
     * Not in the world, for all that it is the world it holds up: a script is
     * handed the world and hands one back, so a number the party's clock wrote
     * into it would be restored to whatever it was when the script began — and
     * a step that never finishes is a party that can never move again.
     */
    private var stepStillRunning = 0

    /** How long until the venom takes its next bite out of everyone it has. */
    private var untilPoisonBites = 0
    private var pulse = TeleporterPulse.AS_LAID_OUT
    private var tickNow = 0

    /** The last thing said about each monster's turn, so it is not said twice. */
    private val lastTurnLine = mutableMapOf<MonsterSlot, String>()
    private var stepsTaken = 0
    private var goingRight = true

    /** The view as last drawn, which a script's words are written over. */
    private var drawn: ViewPort? = null

    /** The clock the party sleep on, while they are asleep. */
    private var sleeping: Job? = null

    /** How long they have slept this rest, so waking early can say so. */
    private var hoursSlept = 0

    /** The starving question, while one is waiting to be answered. */
    private var sleepingOnHungry: CompletableDeferred<Boolean>? = null

    /** Where the party were standing when the view was last drawn. */
    private var wasStandingOn: Location? = null

    /** Squares covered since the party last ate into their stores. */
    private var stepsSinceAMeal = 0

    /** Whatever is being heard, so that the next thing can take its place. */
    private var sounding: PlayingSound? = null

    /** What a script asked, waiting on the click that answers it. */
    private val awaiting = PendingQuestion()

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun onEvent(event: Event) {
        // Every event but the one that starts the game is the player doing
        // something, and some speakers may only be started from inside one of
        // those. It goes before anything that can turn an event away: a step
        // refused by a wall is still a key that was pressed, and it is the
        // pressing that the speaker is waiting for.
        if (event !is Event.Initialize) audioSink.wake()

        // A script running is not a reason to stand still: a door swinging
        // somewhere can be watched, or turned away from, while it swings. Only
        // a script that has taken the party — walked them somewhere, turned
        // them to face something — steers them, and then they are its until it
        // is done. Answering is never steering; it is what a script asking a
        // question waits for.
        if (scriptHasTheParty && event !is Event.DialogAnswered) {
            Logger.d(TAG) { "Ignoring $event while a script is moving the party" }
            return
        }

        // Anything at all wakes a sleeping party — there is no way out drawn on
        // the box because everything is one. A question put to them while they
        // sleep is its own box and answers for itself, so it is left alone.
        if (sleeping?.isActive == true && sleepingOnHungry == null &&
            event.isTheirOwnDoing && event !is Event.Initialize
        ) {
            viewModelScope.launch { wakeUp(hoursSlept) }
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

        // A dialogue owns the screen the same way, and goes the same two ways
        // it came: one of its own words is clicked, or the script that put it
        // up finishes. A key pressed under it does nothing — the party do not
        // walk out from under a speech, and a step that took them out of one
        // was a step that answered nothing.
        if (_state.value.dialog != null && event.isTheirOwnDoing) {
            Logger.d(TAG) { "Ignoring $event while a dialogue is up" }
            return
        }

        // Nothing the party do gets done while an arm is coming down at them.
        // Only the party are held: the doors and the other monsters carry on
        // underneath, which keeps them from hitching.
        if (event.isTheirOwnDoing && _state.value.game.pinnedByASwing) {
            Logger.d(TAG) { "Ignoring $event while the party are being swung at" }
            return
        }

        // A party do not move as fast as a key repeats. Four ticks a step is
        // transcribed rather than tuned, and it is what makes stepping round a
        // monster a matter of timing rather than of holding a key down.
        if (event.isAStep && stepStillRunning > 0) return
        if (event.isAStep) {
            stepStillRunning = GameState.A_STEP.value
            keepTheFightGoing()
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
            is Event.UsedWhatIsAt -> onUsedWhatIsAt(event.x, event.y)
            Event.Camp -> onCamped()
            Event.MoveForward -> onMoveForward()
            Event.MoveBackwards -> onMoveBackwards()
            Event.StrafeLeft -> onStrafe(left = true)
            Event.StrafeRight -> onStrafe(left = false)
            Event.RotateRight -> onRotateRight()
            Event.RotateLeft -> onRotateLeft()
            Event.FrontRankStrikes -> theFrontRankStrikes()

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

            cpsRepository.loadCps(MET_PORTRAITS_CPS)
                .onSuccess { metPortraits = it }
                .onFailure { Logger.e(it) { "Error while loading $MET_PORTRAITS_CPS" } }
            cpsRepository.loadCps(INVENTORY_CPS)
                .onSuccess { invent = it }
                .onFailure { Logger.e(it) { "Error while loading $INVENTORY_CPS" } }
            cpsRepository.loadCps(CARRIED_ITEM_ICONS_CPS)
                .onSuccess { carriedItemIcons = it }
                .onFailure { Logger.e(it) { "Error while loading $CARRIED_ITEM_ICONS_CPS" } }
            cpsRepository.loadCps(THROWN_CPS)
                .onSuccess { thrownShapes = it }
                .onFailure { Logger.e(it) { "Error while loading $THROWN_CPS" } }
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
        // The level goes with the world. What is loaded already remembers
        // which floor its monsters were on, and leaving a level files them
        // under whichever one is open — so an open level left over from before
        // the load would take the saved floor's monsters for its own, and the
        // floor they belong to would never people itself from its file again.
        _state.update {
            it.copy(
                inf = null,
                game = GameState.restoredFrom(saved.world, on = saved.level)
                    .copy(champions = saved.champions),
                messages = saved.messages,
                preferences = saved.preferences,
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
        _state.update { it.copy(game = it.game.copy(champions = save.party)) }
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
                preferences = _state.value.preferences,
            ).onFailure { Logger.e(it) { "Could not autosave" } }
        }
    }

    private fun onVmpSelected(
        name: String,
        subLevel: Int = 0,
        playerX: Int? = null,
        playerY: Int? = null,
        direction: Direction? = null,
        /**
         * Whether the party walked in, as against the floor merely being
         * opened.
         *
         * A party who walked in are standing on a square, and the square is
         * entitled to notice — the welcome on the seventh floor is a script on
         * the square the stairs come up on, and there is nothing else to set
         * it off. A floor opened from the menu, or picked up out of a save, is
         * nobody arriving anywhere.
         */
        walkingIn: Boolean = false,
    ) {
        viewModelScope.launch {
            viewConeRepository
                .loadLevel(name = name)
                .onSuccess { inf ->
                    val arrivingAt = levelNumber(inf.name)
                    val showing = subLevel.coerceIn(inf.subLevels.indices)

                    // Which floor's monsters the party arrive among, and where
                    // they came from: the file, or what was left here last
                    // time. A floor that people themselves from the wrong list
                    // is invisible otherwise — its own monsters simply are not
                    // there, and somebody else's are.
                    val leaving = _state.value.inf?.let { levelNumber(it.name) }
                    val remembered = _state.value.game.remembersOn(arrivingAt)
                    Logger.i(TAG) {
                        "Arriving on $arrivingAt.$showing from ${leaving ?: "nowhere"}, " +
                            "taking ${remembered ?: inf.monsterInstances.size} monsters from " +
                            (if (remembered != null) "what was left there" else "the file") +
                            (leaving?.let { ", filing ${_state.value.game.monsters.size} under $it" }
                                ?: "")
                    }

                    // What a remembered floor is remembered as holding, beside
                    // what its file puts there. Two floors can share a type
                    // number and mean different creatures by it, so the kinds
                    // alone cannot say whose list this is — where they stand
                    // can.
                    _state.value.game
                        .remembersOn(arrivingAt, inf.subLevels[showing].monsters)
                        ?.let { held ->
                            val standing = held.map { "${it.location.x}x${it.location.y}" }
                            val onFile = inf.monsterInstances
                                .map { "${it.location.x}x${it.location.y}" }
                            Logger.i(TAG) {
                                "  remembered: ${standing.sorted().joinToString(" ")}"
                            }
                            Logger.i(TAG) {
                                "  on file:    ${onFile.sorted().joinToString(" ")}"
                            }
                        }
                    scriptRunner = LevelScriptRunner(
                        script = inf.script,
                        level = arrivingAt,
                        subLevel = showing,
                        kinds = inf.subLevels[showing].monsters,
                        itemTypes = itemTypes,
                    )
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
                                    kinds = inf.subLevels[showing].monsters,
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
                    if (walkingIn) runTriggers()
                }
                .onFailure {
                    Logger.e(it) { "Error while loading vmp: $name" }
                }
        }
    }

    /**
     * A step the party take themselves, which a wall may refuse.
     *
     * Only their own steps are asked: a script that moves them is not walking
     * and is not stopped by anything, which is how a teleporter puts a party
     * inside a sealed room.
     */
    private fun onWalked(x: Int? = null, y: Int? = null) {
        if (debugging.wallsArePassable.value) {
            onPlayerPositionChanged(x, y)
            return
        }

        val to = Location(
            x = (x ?: party.position.x).coerceAtLeast(0),
            y = (y ?: party.position.y).coerceAtLeast(0),
        )

        // A monster stops them as surely as a wall, and is refused the same
        // way: the party neither walk through one nor swap places with it.
        val standing = _state.value.game.anythingStandingOn(to, _state.value.subLevel)
        if (wallBetweenPartyAnd(to) || standing) {
            bumpedIntoAWall()
            return
        }

        onPlayerPositionChanged(x, y)
    }

    /**
     * Whether something stands between the party and where they are stepping.
     *
     * It is the wall on the far square turning back towards them that decides,
     * not the one on the square they are leaving: a doorway carries its door
     * on the side it is entered from.
     */
    private fun wallBetweenPartyAnd(to: Location): Boolean {
        val inf = _state.value.inf ?: return false
        val sublevel = inf.subLevels.getOrNull(_state.value.subLevel) ?: return false
        val from = party.position

        val facingUs = when {
            to.y < from.y -> WallSide.SOUTH
            to.y > from.y -> WallSide.NORTH
            to.x > from.x -> WallSide.WEST
            to.x < from.x -> WallSide.EAST
            else -> return false
        }

        val wall = _state.value.game.wall(levelNumber(inf.name), to, facingUs)
        return !sublevel.canBeWalkedOnto(wall)
    }

    /** What a refused step comes to: a line, and a thud. */
    private fun bumpedIntoAWall() {
        viewModelScope.launch { playTrack(WALL_BUMP) }
    }

    /**
     * Cuts whatever a dialogue was saying when the dialogue goes.
     *
     * It is cut at both ends: before a dialogue goes up, and on taking one
     * down, where what is played is the track the banks keep empty — a program
     * that makes no noise, whose whole purpose is to end the one already
     * playing. A speech is worth hearing while its speaker is on
     * screen and not after the player has dismissed them.
     */
    private fun silenceEffects() {
        sounding?.stop()
        sounding = null
    }

    private fun onPlayerPositionChanged(x: Int? = null, y: Int? = null) {
        check(x != null || y != null) {
            "Either x or y must be non-null"
        }
        val steppedOff = party.position
        val steppedTo = Location(
            x = (x ?: party.position.x).coerceAtLeast(0),
            y = (y ?: party.position.y).coerceAtLeast(0),
        )
        _state.update { it.copy(game = it.game.partyMovedTo(steppedTo)) }

        // The square being left is told after the party have gone, not before:
        // it is what holds a plate open while it is stood on, and what it does
        // about being stepped off is decided with the party already elsewhere.
        val toldLeaving = runTriggersAt(steppedOff, ScriptEvent.PARTY_LEFT)

        // A square with something to say draws for itself, and may draw
        // something other than the view — putting the new position up first
        // shows the party standing where the script is about to explain.
        if (!runTriggers() && !toldLeaving) {
            renderViewPort()
            autosave()
        }
    }

    private val party get() = _state.value.game.party

    /** Who the party are, as against [party], which is where they stand. */
    private val roster get() = _state.value.game.champions


    /**
     * Whichever champion answers this time. Rolled per line, so two remarks
     * in a row need not come from one mouth.
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

    /** Whatever is held, offered to a champion on the plate. */
    private fun plateOffered(whose: PartySlot) {
        val champion = _state.value.game.championIn(whose) ?: return
        val held = _state.value.game.item(_state.value.game.inHand)

        when (val answer = itemTypes?.offeredOnAPlate(champion, held) ?: return) {
            is OnAPlate.Eaten -> eatFromHand(whose, answer.food)
            is OnAPlate.Refused -> {
                say(answer.says)
                drawWords()
            }

            OnAPlate.NothingOffered -> Unit
        }
    }

    /** Food offered on the plate, which is food being carried. */
    private fun eatFromHand(whose: PartySlot, food: Item) =
        eat(whose, food) { it.handEmptied() }

    /**
     * Food used where it sits, which is the game's own way: rations already
     * put away are eaten by the champion whose pocket they came out of.
     */
    private fun eatFromSlot(whose: PartySlot, slot: CarrySlot, food: Item) =
        eat(whose, food) { it.slotEmptied(whose, slot) }

    /**
     * A potion drunk out of the pocket it is in, by whoever's pocket it is.
     *
     * The bottle goes whatever was in it, and the champion is told what they
     * feel — the game says that much for every one of the eight, including the
     * ones whose effect is nothing they would notice.
     */
    private fun drink(whose: PartySlot, slot: CarrySlot, potion: Item) {
        val what = Potion.of(potion.value)
        val champion = _state.value.game.championIn(whose) ?: return

        _state.update { it.copy(game = it.game.slotEmptied(whose, slot)) }
        viewModelScope.launch { playTrack(DRINK) }

        when (what) {
            // The two that mend, by the dice the table gives them.
            Potion.HEALING, Potion.EXTRA_HEALING ->
                _state.update {
                    it.copy(game = it.game.championMended(whose, what.mends(Dice.random)))
                }

            // Vitality fills the stomach outright rather than adding to it.
            Potion.VITALITY -> _state.update { it.copy(game = it.game.championSated(whose)) }

            Potion.CURE_POISON ->
                _state.update { it.copy(game = it.game.championPoisoned(whose, yes = false)) }

            // Drinking the poison is being poisoned, which is the same thing a
            // spider does and works itself off the same way.
            Potion.POISON -> {
                _state.update { it.copy(game = it.game.championPoisoned(whose)) }
                sayOf(whose) { ChampionMessages.nowIs(it, WhatABlowLeaves.POISON.calledIt) }
            }

            // These want a champion to carry something they do not carry yet:
            // a strength that wears off, a speed, whether they can be seen,
            // and being poisoned at all. The bottle still empties.
            //
            Potion.GIANT_STRENGTH,
            Potion.SPEED,
            Potion.INVISIBILITY,
            null -> Logger.w(TAG) {
                "Nothing is written for a potion of ${what ?: "value ${potion.value}"}"
            }
        }

        // Every one of them is drunk out loud, whatever it turned out to do.
        what?.let {
            say(Potion.said(champion.name, it.feels))
            drawWords()
        }
        renderViewPort()
    }

    /**
     * A champion the fuller for eating [food], and the food gone. Rotten food
     * is worth nothing and so is not eaten.
     *
     * [consume] is where it goes from, and is the only difference between the
     * two ways of eating: the hand holds what the world is carrying, a pocket
     * holds what a champion is.
     */
    private fun eat(whose: PartySlot, food: Item, consume: (GameState) -> GameState) {
        if (food.value < 0) {
            say("That food is rotten.")
            drawWords()
            return
        }

        _state.update { it.copy(game = consume(it.game.championFed(whose, food.value))) }
        viewModelScope.launch { playTrack(EAT) }
        renderViewPort()
    }

    /**
     * The party lie down where they stand, and the hours run until everyone is
     * mended, the food is gone, or the player says to stop.
     *
     * The hours are counted on a clock rather than added up at once: a rest is
     * something the player watches and can cut short, which is the whole point
     * of the way out in the corner of the box.
     */
    private suspend fun onRest() {
        val inf = _state.value.inf
        val kinds = inf?.subLevels?.getOrNull(_state.value.subLevel)?.monsters.orEmpty()
        val resting = Resting(kinds = kinds, stepping = stepping())

        when (val begun = resting.begin(_state.value.game, walking())) {
            Rest.NotHere -> {
                showMenu(null)
                say(CANNOT_REST_HERE)
                renderViewPort()
            }

            is Rest.SomethingIsNear -> {
                _state.update { it.copy(game = begun.world) }
                showMenu(null)
                say(MONSTERS_ARE_NEAR)
                renderViewPort()
            }

            is Rest.Slept -> {
                _state.update { it.copy(game = begun.world) }
                keepResting(resting)
            }
        }
    }

    private fun keepResting(resting: Resting) {
        sleeping?.cancel()
        hoursSlept = 0

        sleeping = viewModelScope.launch {
            showMenu(CampMenu.resting(hoursSlept))

            // A rest runs until the party are mended, which is a long time to
            // have agreed to by accident — so lying down hurt is asked about
            // before any of it passes, and asked once.
            if (_state.value.game.anybodyStillHurt &&
                !asking(CampMenu.stillInjured(hoursSlept))
            ) {
                wakeUp(hoursSlept)
                return@launch
            }
            showMenu(CampMenu.resting(hoursSlept))

            while (_state.value.game.anybodyStillHurt) {
                val world = _state.value.game

                // Nothing more to be had from lying here: nobody can mend and
                // nobody is going hungry for it either.
                if (!world.anybodyCanStillMend && !world.anybodyStarving) break

                delay(AN_HOUR_OF_SLEEP.inMilliseconds)

                // The floor does not hold still while the party sleep: the
                // monsters take their turns between the hours, and one that
                // wanders up wakes the party where they lie.
                when (val still = resting.begin(_state.value.game, walking())) {
                    is Rest.SomethingIsNear -> {
                        _state.update { it.copy(game = still.world) }
                        showMenu(null)
                        say(MONSTERS_ARE_NEAR)
                        renderViewPort()
                        return@launch
                    }

                    is Rest.Slept -> _state.update { it.copy(game = still.world) }
                    Rest.NotHere -> Unit
                }

                hoursSlept += HOURS_A_MENDED_POINT

                val before = _state.value.game
                var slept = before.sleptAnHour(HOURS_A_MENDED_POINT)

                // A day on an empty stomach costs a hit point, and a day is
                // three of these stretches rather than one.
                val aDayGone = hoursSlept % HOURS_A_STARVED_POINT == 0
                val starved = if (!aDayGone) slept else slept.starvedADay()
                val anybodyWasStarved = starved.champions != slept.champions
                slept = starved

                _state.update { it.copy(game = slept) }
                showMenu(CampMenu.resting(hoursSlept))

                // Asked when a stomach empties, and again on each day it costs
                // somebody a hit point — not on every stretch, which would put
                // the question up faster than the party could answer it.
                if (before.somebodyRanOutOfFood(slept) || anybodyWasStarved) {
                    if (!asking(CampMenu.starving(hoursSlept))) break
                    showMenu(CampMenu.resting(hoursSlept))
                }
            }

            wakeUp(hoursSlept)
        }
    }

    /**
     * The party growing hungry as they go.
     *
     * The original counts this off a clock, a mouthful a minute of play, which
     * suited a game nobody left running. A tab left open is not that: a party
     * stood still in an empty corridor would starve while nobody was even
     * looking at them. So it is the walking that empties a stomach here, and a
     * party who go nowhere keep what they have.
     */
    private fun theyWalkedASquare() {
        stepsSinceAMeal++
        if (stepsSinceAMeal < STEPS_TO_A_MEAL) return

        stepsSinceAMeal = 0
        _state.update { it.copy(game = it.game.hungrier()) }
    }

    /** Puts a question to a sleeping party and waits for its answer. */
    private suspend fun asking(question: CampMenu): Boolean {
        val asked = CompletableDeferred<Boolean>()
        sleepingOnHungry = asked
        showMenu(question)

        return asked.await().also { sleepingOnHungry = null }
    }

    /** The party up again, however the rest ended. */
    private suspend fun wakeUp(hours: Int) {
        val clock = sleeping
        sleeping = null
        sleepingOnHungry = null
        clock?.cancel()

        showMenu(null)

        // What to say is what actually happened. Nobody hurt is a party fully
        // rested; hurt with nothing left to eat is a party that cannot mend at
        // all, and telling them they are rested would be a plain lie.
        val world = _state.value.game
        say(
            when {
                !world.anybodyStillHurt -> FULLY_RESTED
                !world.anybodyCanStillMend -> STARVING
                hours > 0 -> "Hours rested: $hours"
                else -> FULLY_RESTED
            }
        )
        renderViewPort()
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
                if (!_state.value.preferences.sounds) {
                    audioSink.stopAll()
                    sounding = null
                }
                showMenu(CampMenu.preferences(_state.value.preferences))
            }

            MenuChoice.RestParty -> onRest()

            // The hours already slept are kept: waking early is not undoing
            // them. Said to a starving party's question, it is the answer no.
            MenuChoice.StopResting -> {
                val asked = sleepingOnHungry
                if (asked == null) wakeUp(hoursSlept) else asked.complete(false)
            }

            MenuChoice.KeepResting -> {
                sleepingOnHungry?.complete(true)
            }
            is MenuChoice.HoursRested -> Unit

            is MenuChoice.OpenSlots -> showSlots(choice.saving)
            is MenuChoice.UseSlot ->
                if (!choice.saving) loadFrom(choice.slot)
                else if (canTypeIntoTheGame) startNaming(choice.slot) else saveTo(choice.slot, suggestedName())
            MenuChoice.DropCharacter -> showMenu(whoCouldLeave())
            is MenuChoice.DropThisOne -> dropChampion(choice.whose)

            is MenuChoice.NotYet -> Logger.i(TAG) { "${choice.what} is not implemented" }
        }
    }

    /**
     * The party to choose from, or the refusal where there are too few of them
     * to lose one. The count is taken before the question is put, which is why
     * a party of four are told rather than offered a list they may not use.
     */
    private fun whoCouldLeave(): CampMenu {
        val party = _state.value.game.champions
        val standing = party.count { it.inTheParty }

        if (standing <= CampMenu.NEVER_FEWER_THAN) return CampMenu.tooFewToDrop()

        return CampMenu.whoLeaves(party.filter { it.inTheParty }.map { it.name })
    }

    /**
     * Sends one of them away, everything they carried landing on the square
     * underfoot — see [GameState.championDropped].
     *
     * The slot the menu names is a place in the list of those still standing,
     * not a place in the party: an empty slot is nobody and was never offered.
     */
    private suspend fun dropChampion(chosen: PartySlot) {
        val party = _state.value.game.champions
        val whose = party.indices
            .filter { party[it].inTheParty }
            .getOrNull(chosen.index)
            ?.let(::PartySlot)
            ?: return

        val level = _state.value.inf?.let { levelNumber(it.name) } ?: return
        val who = party[whose.index].name

        _state.update {
            it.copy(
                game = it.game.championDropped(
                    whose = whose,
                    level = level,
                    at = it.game.party.position,
                    facing = it.game.party.facing,
                    dice = Dice.random,
                ),
            )
        }
        Logger.i(TAG) { "$who was dropped from the party at ${party.size} strong" }

        showMenu(CampMenu.gameOptions())
        drawViewPort()
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
            preferences = _state.value.preferences,
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
            handAt(x, y)?.let { hand ->
                swapHandWith(hand.champion, hand.holds)
                return
            }

            val face = championBoxes.indexOfFirst { it.showsFaceAt(x, y) }
            if (face >= 0) {
                val whose = PartySlot(face)
                // A click on the face is the way onto the page, and only that,
                // whatever is being carried at the time. The game asks nothing
                // about the hand here — a face is not somewhere a thing is
                // used — and while it did, food could not be carried past a
                // champion to be put away: offering it was eating it.
                if (_state.value.game.championIn(whose) != null) {
                    showSheet(CharacterSheet(whose))
                }
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
        (aimedAt(x, y) ?: FloorReach.at(x, y))?.let { reach ->
            if (reachedInto(reach)) return
        }

        onClickedTheWorld(x, y)
    }

    /** Which champion's which hand a click landed on, if it landed on one. */
    private fun handAt(x: Int, y: Int): HandOnThePanel? {
        championBoxes.forEachIndexed { slot, box ->
            val whose = PartySlot(slot)
            if (_state.value.game.championIn(whose) == null) return@forEachIndexed

            repeat(Champion.HANDS) { hand ->
                if (box.holdsHandAt(x, y, hand)) {
                    return HandOnThePanel(whose, inventorySlotPositions[hand])
                }
            }
        }
        return null
    }

    /** One of the two slots beside a face on the party panel. */
    private data class HandOnThePanel(val champion: PartySlot, val holds: InventorySlot)

    /**
     * A click on a name, which is how two champions change places.
     *
     * The first names somebody and leaves their strip flickering between their
     * own name and the word for what is being done to them; the second names
     * who they change places with. Naming the same one twice is how the party
     * change their mind, since otherwise there is no way back out of it.
     *
     * @return whether the click was a name, and so is not also something else.
     */
    private fun swappedAt(x: Int, y: Int): Boolean {
        val whose = championBoxes
            .indexOfFirst { it.showsNameAt(x, y) }
            .takeIf { it >= 0 }
            ?.let(::PartySlot)
            ?: return false

        // An empty box has no name to click and nobody to move.
        if (_state.value.game.championIn(whose) == null) return false

        val waiting = _state.value.swapping

        _state.update {
            when (waiting) {
                null -> it.copy(swapping = whose)
                whose -> it.copy(swapping = null)
                else -> it.copy(
                    game = it.game.championsSwapped(waiting, whose),
                    swapping = null,
                )
            }
        }

        keepTheNameFlickering(_state.value.swapping != null)
        drawWords()
        return true
    }

    /**
     * The clock that flickers the name of whoever is waiting to be moved.
     *
     * It is the only thing on the panel that moves without the party doing
     * anything, so it needs a clock of its own — and must not have one when
     * nobody is waiting, or the panel is redrawn for ever.
     */
    private fun keepTheNameFlickering(anyWaiting: Boolean) {
        if (anyWaiting == (flickeringName?.isActive == true)) return

        flickeringName?.cancel()
        flickeringName = if (!anyWaiting) {
            null
        } else {
            viewModelScope.launch {
                while (_state.value.swapping != null) {
                    delay(SWAP_FLICKER.inMilliseconds)
                    _state.update { it.copy(swapShowing = !it.swapShowing) }
                    drawWords()
                }
            }
        }
    }

    /**
     * The other click, which uses what a slot holds where it lies instead of
     * taking it out.
     *
     * Reading and striking are the uses there are so far. Everything else a
     * thing can be — drunk, aimed, eaten — is left alone rather than answered
     * with the line about using a thing wrongly, which would say the wrong
     * thing about a potion that simply is not modelled yet.
     */
    private fun onUsedWhatIsAt(x: Int, y: Int) {
        val sheet = sheetOnShow

        if (sheet == null && swappedAt(x, y)) return

        val used = when {
            sheet == null -> handAt(x, y)?.let { it.champion to it.holds }
            sheet.page == CharacterSheet.Page.BELONGINGS ->
                inventorySlotAt(x, y)?.let { sheet.slot to it }

            else -> null
        } ?: return

        val (whose, slot) = used
        val world = _state.value.game
        val champion = world.championIn(whose) ?: return
        val held = world.item(champion.holding(slot.slot))

        // What this hand does with what it holds, which is one thing. Without
        // an item table nothing is known about anything, and a hand swings.
        val doing = itemTypes?.whatAHandDoesWith(held) ?: HandUse.Swing

        // A thing read is a thing read and nothing else: the page goes up and
        // the wall in front is left alone. Asking the wall's triggers here runs
        // a script that ends by clearing the box — which closes the page the
        // instant it opened, every time but the first, when reading the text
        // file for the first time is slow enough to win the race.
        if (doing is HandUse.Read) {
            viewModelScope.launch {
                when (val parchment = doing.what) {
                    is OnAParchment.Writing -> read(parchment.page)
                    is OnAParchment.Map -> lookAt(parchment)
                }
            }
            return
        }

        // Whether this champion is of a class that may use the thing at all.
        // It is said and then ignored: the game warns and lets them try, so a
        // fighter told they cannot use the lock picks still takes them to the
        // wall in front and the wall still answers.
        var said = false
        if (held != null && itemTypes?.isUsableBy(champion, held) == false) {
            say(ItemMessages.cannotUse(champion.name))
            said = true
        }

        // And what kind of thing it is, for the two answers that are nothing
        // but something to say. A kind nothing here has been written for yet
        // says nothing, which is not the same as the game having a line about
        // it.
        val aboutTheKind = when (doing) {
            HandUse.WorksByBeingWorn -> ItemMessages.WORKS_BY_BEING_WORN
            HandUse.NotUsedThisWay -> ItemMessages.NOT_USED_THIS_WAY
            else -> null
        }
        aboutTheKind?.let {
            say(it)
            said = true
        }

        // Nothing below is bound to draw — a swing that was refused does not,
        // and neither does a wall with nothing to say — so what has just been
        // written is put up here rather than left for the next thing that
        // happens to repaint.
        if (said) drawWords()

        // A horn is blown rather than swung, and blowing it is heard. What the
        // sound is for is the wall's business, asked for below like any other
        // thing taken to one — a puzzle listening for a horn wants the right
        // one, and the sound alone is all the horn itself does.
        // The one thing this hand does. Rations and potions are had out of the
        // pocket they are in, by whoever's pocket it is: that is the game's own
        // way of eating and drinking and wants nothing held, the plate being
        // for food carried and this for food put away.
        //
        // Only a swing is a swing, which is why the key that sets the whole
        // front rank going comes at [strike] from somewhere else.
        when (doing) {
            is HandUse.Blow -> {
                say(doing.horn.sounds)
                viewModelScope.launch { playTrack(doing.horn.heardAs) }
            }

            HandUse.Eat -> held?.let { eatFromSlot(whose, slot.slot, it) }
            HandUse.Drink -> held?.let { drink(whose, slot.slot, it) }

            HandUse.Swing -> if (slot.slot.isAHand) strike(whose, slot.slot)

            is HandUse.Read,
            HandUse.WorksByBeingWorn,
            HandUse.NotUsedThisWay,
            HandUse.NotWrittenYet -> Unit
        }

        // And whatever it was, the wall in front of the party is asked what it
        // makes of it. That is how a window is broken: not by pointing at it,
        // which reads the carving, but by taking something to it.
        usedOnTheWallAhead(whose, champion.holding(slot.slot))
    }

    /**
     * The wall the party face, asked what it makes of the thing just used.
     *
     * A weapon is only offered by the front rank — the two who can reach it —
     * which is a rule of the game and not a guess at one.
     */
    private fun usedOnTheWallAhead(whose: PartySlot, used: ItemIndex) {
        val held = _state.value.game.item(used)
        val swung = held != null && itemTypes?.isSwungByHand(held) == true

        if (swung && !whose.inTheFrontRank) return

        val party = _state.value.game.party
        runTriggersAt(
            at = party.facing.oneStepFrom(party.position),
            event = ScriptEvent.ITEM_USED_ON_WALL,
            used = used,
        )
    }

    /**
     * Everybody who can reach swings at once, which no button in the game does.
     *
     * A fight is fought a hand at a time, and clicking four slots to see what
     * one round comes to is most of the time it takes to test one. This is the
     * bench's own key, and it stands in for those four clicks: each hand of
     * the front rank is asked to swing and then to take what it holds to
     * whatever is in front, which is the pair of questions one click asks.
     *
     * Each still refuses on its own terms — a hand still coming back to rest,
     * a shield, a champion who is down — so this adds a way in and no rules.
     * What it does not do is say the lines a click says about a thing that is
     * not used that way: those answer somebody who pointed at one slot, and
     * four of them at once would be noise rather than an answer.
     */
    private fun theFrontRankStrikes() {
        _state.value.game.champions.indices
            .map(::PartySlot)
            .filter { it.inTheFrontRank }
            .forEach { whose ->
                repeat(CarrySlot.HANDS) { slot ->
                    val hand = CarrySlot(slot)

                    // Swinging at a thing and taking a thing to it are two
                    // different questions, and a barrel only answers the
                    // second: it is broken by what is brought to it rather
                    // than by being hit at. A click on a hand asks both, so a
                    // key standing in for those clicks asks both too.
                    //
                    // Only for a hand that actually went, though. A shield or
                    // an arm still coming back to rest took nothing to the
                    // wall, and asking on its behalf would run the wall's
                    // script once for every hand that did nothing.
                    if (strike(whose, hand)) {
                        _state.value.game.championIn(whose)?.let {
                            usedOnTheWallAhead(whose, it.holding(hand))
                        }
                    }
                }
            }
    }

    /**
     * [whose] swings what is in [hand] at whatever stands in front of the party.
     *
     * @return whether the arm actually went. A hand that was refused — still
     *   coming back to rest, holding something that is not swung, belonging to
     *   somebody who is down — did nothing, and a caller deciding what a swing
     *   leads on to wants to know that.
     */
    private fun strike(whose: PartySlot, hand: CarrySlot): Boolean {
        val inf = _state.value.inf ?: return false
        val types = itemTypes ?: return false

        val sublevel = inf.subLevels[_state.value.subLevel]
        val struck = Fighting(
            itemTypes = types,
            kinds = sublevel.monsters,
            level = levelNumber(inf.name),
            wallsThatGiveWay = sublevel.wallsThatGiveWay,
        ).strike(_state.value.game, whose, hand)

        Logger.d(TAG) { "$whose swings with $hand: ${struck.blow}" }
        // None of these is a swing, so nothing is heard and nothing moves.
        if (struck.blow == Blow.StillRecovering ||
            struck.blow == Blow.Unable ||
            struck.blow == Blow.NotAWeapon
        ) {
            return false
        }

        val before = _state.value.game.champions
        _state.update { it.copy(game = struck.world) }
        announceAnyLevelGained(before, struck.world.champions)
        Logger.d(TAG) { "Roused: ${struck.world.monsters.filter { m -> m.provoked }.map { m -> m.index }}" }

        // The blow is heard whether or not it lands. What a landed one looks
        // like, and what a monster sounds like, are still to come.
        viewModelScope.launch { playTrack(SWING) }
        renderViewPort()
        keepHandsRecovering()
        letTheDamageFade()
        letTheFlashFade()
        keepTheFightGoing()
        return true
    }

    /**
     * The clock the fight runs on.
     *
     * Three things are wound by it and they are deliberately separate: the
     * party's own step, the frames of a monster's swing, and the turn that
     * starts one. A monster's turn comes round every
     * twenty ticks whatever the party do — stepping does not hurry it and
     * being hit does not delay it, so a party who dance well are not slowing
     * anything down, only standing somewhere else when the blow falls.
     */
    private fun keepTheFightGoing() {
        if (fighting?.isActive == true) return

        // Nothing to keep going. This is asked after every draw, so starting a
        // clock that would find nothing to do and stop again is a clock per
        // frame — and a line in the log saying a fight nobody was in is over.
        if (!stillFighting()) return

        fighting = viewModelScope.launch {
            // A monster roused this instant has its whole turn ahead of it,
            // not the tail of one it never took.
            // The four groups start spread across a turn rather than together,
            // which is what stops a pair of monsters moving as one body.
            val untilTheirTurn = WHEN_EACH_GROUP_STARTS.toIntArray()
            var untilTheNextFrame = A_SWING_FRAME.value

            // And the floor's own clocks, which belong to the sublevel rather
            // than to the fight: they are wound from here because this is the
            // only thing that ticks.
            val floorClocks = FloorClocks(timersHere())

            // Ticks since this fight started, so the log reads as a rhythm
            // rather than as a pile of unrelated lines. The party stamp their
            // own steps with it, which is the comparison worth having.
            tickNow = 0
            lastTurnLine.clear()

            // The party's own step winds this clock too, so most of the time it
            // runs there is no fight at all. Only one that had something in it
            // is worth saying is over.
            var anybodyFought = false

            while (stillFighting()) {
                anybodyFought = anybodyFought || _state.value.game.monsters.any { it.provoked }
                delay(GameState.CLOCK_STEP.inMilliseconds)

                tickNow += GameState.CLOCK_STEP.value
                untilTheNextFrame -= GameState.CLOCK_STEP.value

                floorClocks.nowKeeping(timersHere())

                val cameRound = floorClocks.stepped(
                    by = GameState.CLOCK_STEP,
                    // a script owns the screen while it runs, and a waking
                    // that talked over it would take down what it had up
                    held = !debugging.floorsKeepTime.value || playing?.isActive == true,
                )

                val aFrame = untilTheNextFrame <= 0
                if (aFrame) untilTheNextFrame = A_SWING_FRAME.value

                // A blow coming down at the party holds the rest of the floor
                // still: their turns wait rather than being lost, so nothing
                // else swings until this one lands. The swing above still runs.
                val theirTurn = if (_state.value.game.pinnedByASwing) {
                    emptyList()
                } else {
                    untilTheirTurn.indices.filter { group ->
                        untilTheirTurn[group] -= GameState.CLOCK_STEP.value
                        (untilTheirTurn[group] <= 0).also {
                            if (it) untilTheirTurn[group] = A_MONSTER_TURN.value
                        }
                    }
                }

                // Everything the clock does to the world happens in one go.
                // Read it, change it and write it back separately and whatever
                // a swing landing in between did is thrown away.
                stepStillRunning = (stepStillRunning - GameState.CLOCK_STEP.value)
                    .coerceAtLeast(0)

                // The venom works on its own clock, not the monsters': it goes
                // on biting long after whatever bit them is dead, and it is
                // why this clock keeps running with nothing else left to do.
                untilPoisonBites -= GameState.CLOCK_STEP.value
                val poisonBites = untilPoisonBites <= 0
                if (poisonBites) untilPoisonBites = POISON_BITES.value

                var landed = emptyList<MonstersTurn.Struck>()
                var left = emptyList<MonstersTurn.Left>()
                var freed = emptyList<PartySlot>()
                var bitten = emptyList<PartySlot>()
                var ruined = emptyList<MonstersTurn.Ruined>()
                var swungAndMissed = emptyList<MonsterSlot>()
                var roused = emptyList<MonsterInstance>()
                var walked = emptyList<MonsterInstance>()
                var took = emptyList<String>()
                var moved = false
                var flewOnto = emptyList<Location>()
                var struckInFlight = emptyList<Flight.Hurt>()

                // Settled before the world is touched: an update that loses a
                // race runs its block again, and the way round a corner would
                // otherwise flip a second time on the retry.
                val walking = if (theirTurn.isEmpty()) null else walking()
                val wayRound = if (theirTurn.isEmpty()) {
                    MonsterPathing.WayRound.RIGHT_FIRST
                } else {
                    wayRound()
                }

                _state.update { state ->
                    var world = state.game

                    if (aFrame) {
                        world = world.swingsCarriedOn()

                        val landing = world.landingThisFrame

                        if (landing.isNotEmpty()) {
                            val taken = monstersTurn().landed(world, landing)
                            landed = taken.struck
                            ruined = taken.ruined
                            swungAndMissed = taken.missed
                            left = taken.left
                            world = taken.world
                        }
                    }

                    if (theirTurn.isNotEmpty()) {
                        val already = world.monsters.filter { it.striking != null }.map { it.index }
                        val stood = world.monsters.associate { it.index to it.location }
                        val was = world.monsters.associateBy { it.index }

                        theirTurn.forEach { group ->
                            world = monstersTurn().begun(
                                world = world,
                                walking = walking,
                                wayRound = wayRound,
                                group = group,
                            )
                        }

                        roused = world.monsters.filter {
                            it.striking != null && it.index !in already
                        }
                        walked = world.monsters.filter {
                            stood[it.index]?.let { at -> at != it.location } == true
                        }

                        took = world.monsters
                            .filter { it.turnGroup in theirTurn && it.provoked }
                            .mapNotNull { now ->
                                was[now.index]?.let { before ->
                                    whatItDidWithItsTurn(tickNow, before, now, world.party)
                                }
                            }
                    }

                    // What the venom takes, it takes from everyone it has hold
                    // of at once, and takes the same from each: a flat five,
                    // rolled for by nobody and saved against by nobody.
                    if (poisonBites) {
                        bitten = world.poisoned
                        bitten.forEach { world = world.championHurt(it, POISON_TAKES) }
                    }

                    // And whatever has hold of somebody loosens by a tick.
                    world.gripsStepped().let { (loosened, letGo) ->
                        world = loosened
                        freed = letGo
                    }

                    // Whatever is in the air crosses one more square. What it
                    // flew onto is kept rather than acted on: each of those
                    // squares has its script to run, and running one is not
                    // something to do while holding the world still.
                    state.inf?.subLevels?.getOrNull(state.subLevel)?.let { here ->
                        val flown = Flight(
                            sublevel = here,
                            level = levelNumber(state.inf.name),
                            itemTypes = itemTypes,
                            kinds = here.monsters,
                        ).onward(world)

                        world = flown.world
                        flewOnto = flown.flewOnto
                        struckInFlight = flown.hurt
                    }


                    moved = world.somethingMoved(state.game)
                    state.copy(game = world)
                }

                landed.forEach {
                    Logger.d(TAG) { "$tickNow  m${it.monster} lands on ${it.at} for ${it.damage}" }
                }

                // A blow that landed puts a number on a portrait, and the
                // clock that takes it off again is not this one: this one
                // stops with the fight, and a splat left when it did would
                // stay on the face until something else started it.
                if (landed.isNotEmpty() || bitten.isNotEmpty() || struckInFlight.isNotEmpty()) {
                    letTheDamageFade()
                }
                ruined.forEach { sayWhatWasRuined(it) }

                // A square something has just flown over gets its say. This is
                // how a trap reaches further than it stands: the bolt is what
                // travels, and each square it crosses is asked what it makes
                // of that.
                flewOnto.forEach { where ->
                    Logger.d(TAG) { "$tickNow  something flew onto $where" }
                    runTriggersAt(where, ScriptEvent.SOMETHING_FLEW_IN)
                }

                // And a square the floor keeps coming back to gets its say,
                // whether or not anybody is near it. This is the only thing a
                // level does on its own.
                cameRound.forEach { where ->
                    runTriggersAt(
                        at = where,
                        event = ScriptEvent.THE_CLOCK_CAME_ROUND,
                        byItself = true,
                    )
                }

                if (_state.value.game.bursting.isNotEmpty()) letTheBurstsBurn()

                struckInFlight.forEach { hit ->
                    Logger.d(TAG) { "$tickNow  in flight: $hit" }
                }

                // What a blow left is said once, when it takes hold. Only the
                // venom is said again after that: what it costs is said every
                // time it costs anything, which is the only sign the player
                // gets that it is still working.
                left.forEach { what ->
                    sayOf(what.whose) { ChampionMessages.nowIs(it, what.what.calledIt) }
                }
                bitten.forEach { whose -> sayOf(whose, ChampionMessages::feelsThePoison) }
                freed.forEach { whose -> sayOf(whose, ChampionMessages::isNoLongerParalysed) }
                swungAndMissed.forEach { slot -> Logger.d(TAG) { "$tickNow  m${slot.value} misses" } }
                took.forEach { line -> Logger.d(TAG) { line } }

                roused.forEach { monster ->
                    monsterSound(monster)?.let { playTrack(it, asFarOffAs(monster)) }
                }

                // There is one voice, and a new sound takes it from whatever
                // had it. So a swing keeps it: an attack holds the world for
                // its length and nothing is heard over it, and without this a
                // monster stepping somewhere in the dark silences the one in
                // front winding up. One pair of feet at a
                // time, too, for the same reason.
                if (roused.isEmpty()) {
                    walked.minByOrNull { squaresOff(it) }?.let { monster ->
                        movingSound(monster)?.let { playTrack(it, asFarOffAs(monster)) }
                    }
                }
                if (moved) drawViewPort()
            }

            if (anybodyFought) Logger.d(TAG) { "The fight is over" }
        }
    }

    /**
     * One line for one monster's turn, for reading the rhythm of a fight off
     * the log rather than off the tables.
     *
     * Everything needed to see why it chose what it chose is on the line:
     * where it was and where it went, which way it looks, which corner it
     * stands on, where the party are, and whether its arm reaches them from
     * there. A turn where it does nothing is a line too — that is the half of
     * the rhythm nothing else shows.
     *
     * The same line twice running is not, though. Something roused that
     * cannot reach the party and has not been let walk waits for ever, and
     * saying so once a turn until the party do something buries the fight it
     * was written to show. So a line is kept back while it would repeat, and
     * the next one that differs is printed.
     */
    private fun whatItDidWithItsTurn(
        tick: Int,
        before: MonsterInstance,
        after: MonsterInstance,
        party: PartyState,
    ): String? {
        val did = when {
            after.striking != null && before.striking == null -> "SWINGS"
            before.location != after.location -> "steps "
            before.direction != after.direction -> "turns "
            before.place != after.place -> "shifts"
            else -> "waits "
        }

        fun <T> both(was: T, now: T) = if (was == now) "$now" else "$was->$now"

        val line = "m${after.index} $did" +
            "\t${both("${before.x}x${before.y}", "${after.x}x${after.y}")}" +
            "\t${both(before.direction, after.direction)}" +
            "\t${both(before.place, after.place)}" +
            "\tparty ${party.position.x}x${party.position.y} ${party.facing}" +
            "\treach=${kindOf(after)?.let { after.canReach(party, it.size) }}" +
            " ready=${after.readyToStrike}"

        if (lastTurnLine[after.index] == line) return null
        lastTurnLine[after.index] = line

        return "$tick\t$line"
    }

    /**
     * Whether anything is still going that the clock has to keep winding.
     *
     * Once monsters walk it never stops while any of them is awake, because
     * noticing the party is itself something a turn does — a clock that waited
     * for a fight would be waiting for the thing it is supposed to start.
     */
    /** The clocks the sublevel the party are standing in keeps. */
    private fun timersHere(): List<ScriptTimer> =
        _state.value.inf?.subLevels?.getOrNull(_state.value.subLevel)?.scriptTimers.orEmpty()

    private fun stillFighting(): Boolean {
        val monsters = _state.value.game.monsters

        // A floor with a clock of its own never finishes: its squares go on
        // being woken with nobody near them, which is what a floor that does
        // something by itself is. Stopping the clock from the debug menu is
        // the one thing that lets this settle.
        if (debugging.floorsKeepTime.value && timersHere().isNotEmpty()) return true

        // Poison outlasts whatever gave it: this clock is the only thing that
        // winds it, so it has to keep running for a party standing perfectly
        // still in an empty corridor with somebody quietly dying on it.
        if (_state.value.game.poisoned.isNotEmpty()) return true

        // And so does whatever has hold of somebody, for the same reason and
        // more urgently: nothing else lets go of a paralysed champion, so a
        // clock stopped over one leaves them held for the rest of the game.
        if (_state.value.game.holding.isNotEmpty()) return true

        // And a thing in the air has to come down, whether or not anybody is
        // fighting over it — and a burst has to finish burning.
        if (_state.value.game.inFlight.isNotEmpty()) return true
        if (_state.value.game.bursting.isNotEmpty()) return true

        if (monsters.any { it.provoked } || stepStillRunning > 0) return true

        return debugging.monstersMayWalk.value && monsters.any { !it.standingBy }
    }

    /**
     * The line for a thing a monster's blow destroyed.
     *
     * Read after the world has lost it: a slot is emptied rather than the
     * thing itself unmade, so it is still there to be named.
     */
    private fun sayWhatWasRuined(lost: MonstersTurn.Ruined) {
        val world = _state.value.game
        val names = itemNames ?: return
        val item = world.item(lost.what) ?: return
        val whose = world.championIn(lost.whose) ?: return

        say(ItemMessages.ruined(whose.name, whose.sex, names.of(item, itemTypes)))
    }

    private fun monstersTurn() = MonstersTurn(
        kinds = _state.value.inf?.subLevels?.getOrNull(_state.value.subLevel)?.monsters.orEmpty(),
        itemTypes = itemTypes,
        stepping = stepping(),
    )

    /** How a monster shifts its feet on its own square, rooted or not. */
    private fun stepping(): MonsterStepping? {
        val inf = _state.value.inf ?: return null
        val sublevel = inf.subLevels.getOrNull(_state.value.subLevel) ?: return null
        return MonsterStepping(levelNumber(inf.name), sublevel, sublevel.monsters)
    }

    /**
     * How to walk across the floor, or null while monsters are rooted where
     * they were placed.
     */
    private fun walking(): MonsterPathing? {
        if (!debugging.monstersMayWalk.value) return null

        val inf = _state.value.inf ?: return null
        val sublevel = inf.subLevels.getOrNull(_state.value.subLevel) ?: return null
        val kinds = sublevel.monsters

        return MonsterPathing(
            stepping = MonsterStepping(levelNumber(inf.name), sublevel, kinds),
            kinds = kinds,
        )
    }

    /**
     * Which way monsters go round things this turn.
     *
     * It flips every so often so that two given the same problem do not solve
     * it the same way for ever. The count is here rather than in the world
     * because a script writing the world back would undo it.
     */
    private fun wayRound(): MonsterPathing.WayRound {
        if (++stepsTaken > STEPS_BEFORE_SWAPPING) {
            stepsTaken = 0
            goingRight = !goingRight
        }

        return if (goingRight) {
            MonsterPathing.WayRound.RIGHT_FIRST
        } else {
            MonsterPathing.WayRound.LEFT_FIRST
        }
    }

    /**
     * How far off something is, as the game measures what can be heard: the
     * shorter way halved and added to the longer, so a thing on the diagonal
     * is nearer than counting both ways would make it.
     */
    private fun squaresOff(monster: MonsterInstance): Int {
        val party = _state.value.game.party.position
        val across = abs(monster.x - party.x)
        val along = abs(monster.y - party.y)

        return maxOf(across, along) + minOf(across, along) / 2
    }

    /** And so how loud it is here, or silent for something too far off. */
    private fun asFarOffAs(monster: MonsterInstance) = Volume.asFarOffAs(squaresOff(monster))

    /** What a monster sounds like swinging. */
    private fun monsterSound(monster: MonsterInstance): TrackIndex? =
        kindOf(monster)?.sound1?.takeIf { it > 0 }?.let(::TrackIndex)

    /** And what it sounds like taking a step, which is its other sound. */
    private fun movingSound(monster: MonsterInstance): TrackIndex? =
        kindOf(monster)?.sound2?.takeIf { it > 0 }?.let(::TrackIndex)

    private fun kindOf(monster: MonsterInstance) =
        _state.value.inf?.subLevels?.getOrNull(_state.value.subLevel)?.monsters
            ?.firstOrNull { it.id == monster.type.value }

    /**
     * Takes the silhouette off whatever was struck, a moment after it was.
     *
     * The flash is not an animation with steps to it — the shape is drawn in
     * one colour and then drawn properly again — so this is one wait and one
     * redraw rather than a clock.
     */
    private fun letTheFlashFade() {
        if (!_state.value.game.anythingFlashing) return

        fading?.cancel()
        fading = viewModelScope.launch {
            delay(FLASH.inMilliseconds)
            _state.update { it.copy(game = it.game.flashesFaded()) }
            renderViewPort()
        }
    }

    /**
     * Takes the splat off a champion's portrait once its moment has passed.
     *
     * The same countdown the weapon hands report on, and the same length:
     * both hang on one timer.
     */
    /**
     * Burns the bursts down on a clock of their own, faster than the world's.
     *
     * A fireball is over in a third of a second, which is three turns of the
     * world's clock and so three pictures of it — a blink rather than an
     * explosion. On its own clock it gets a dozen, and the same third of a
     * second buys something worth watching.
     */
    private fun letTheBurstsBurn() {
        if (burning?.isActive == true) return

        burning = viewModelScope.launch {
            viewModelScope.launch { playTrack(BURSTING) }

            while (_state.value.game.bursting.isNotEmpty()) {
                delay(Burst.A_FRAME)

                _state.update { state ->
                    state.copy(
                        game = state.game.copy(
                            bursting = state.game.bursting
                                .map { it.onward() }
                                .filter { it.burning },
                        ),
                    )
                }
                drawViewPort()
            }
        }
    }

    private fun letTheDamageFade() {
        if (fadingDamage?.isActive == true) return

        fadingDamage = viewModelScope.launch {
            while (_state.value.game.showingDamage.isNotEmpty()) {
                delay(DamageShown.STEP.inMilliseconds)

                val before = _state.value.game.showingDamage
                _state.update { it.copy(game = it.game.damageFaded()) }
                if (_state.value.game.showingDamage != before) drawWords()
            }
        }
    }

    /**
     * Counts the swung hands back to rest, and redraws as each comes back so
     * the grid over it lifts.
     *
     * The same shape as the door clock, and for the same reason: the party are
     * free while it runs.
     */
    private fun keepHandsRecovering() {
        if (recoveringHands?.isActive == true) return

        recoveringHands = viewModelScope.launch {
            while (_state.value.game.recovering.isNotEmpty()) {
                delay(HandRecovering.STEP.inMilliseconds)

                // Two things a step can change on screen: a hand coming back
                // to use, and one of them giving up saying what it came to.
                val before = _state.value.game.asTheSlotsRead
                _state.update { it.copy(game = it.game.recoveryStepped()) }
                if (_state.value.game.asTheSlotsRead != before) drawWords()
            }
        }
    }

    /**
     * Puts a page up to be read, with a word in the corner to close it.
     *
     * Nothing waits on it being closed — this is not a script's question — and
     * a page part way through is the dialogue box's own business, so all this
     * has to do is put the first one up.
     */
    private suspend fun read(page: DialogueTextId) {
        val text = dialogueTextRepository.text(page)
            .onFailure { Logger.e(it) { "No dialogue text $page" } }
            .getOrNull() ?: return

        // nobody is speaking, and nothing said before stands in the box behind
        speaker = null
        standingInTheBox = emptyList()

        val unread = text.pages.drop(1)

        _state.update {
            it.copy(
                dialog = DialogPrompt(
                    scene = sceneFor(
                        scene = emptyList(),
                        text = text.first,
                        buttonLabels = listOf(if (unread.isEmpty()) OK else MORE),
                        waitsToBeRead = true,
                        readOff = DialogueScene.ReadOff.APageOverTheView,
                    ),
                    unread = unread,
                    buttons = listOf(OK),
                    waitsToBeRead = true,
                    readOff = DialogueScene.ReadOff.APageOverTheView,
                )
            )
        }
        drawWords()
    }

    /** Holds a map up. It is looked at rather than read, and any click ends that. */
    private suspend fun lookAt(map: OnAParchment.Map) {
        val sheet = pictureCalled(OnAParchment.Map.SHEET) ?: return
        val frame = dialogueFrame ?: return

        speaker = null
        standingInTheBox = emptyList()

        _state.update {
            it.copy(
                dialog = DialogPrompt(
                    scene = DialogueScene.aPicture(
                        frame = frame,
                        picture = DialogueScene.Picture(
                            cps = sheet,
                            sourceLeft = map.sourceLeft,
                            sourceTop = map.sourceTop,
                            goes = DialogueScene.PictureFrame.SPEAKER,
                        ),
                    ),
                )
            )
        }
        drawWords()
    }

    /**
     * Swaps what is being held with what is in one of a champion's slots.
     *
     * With an empty hand that is taking what was there, and with an empty slot
     * it is putting something down; the three cases are not told apart, and neither does this.
     */
    private fun swapHandWith(champion: PartySlot, slot: InventorySlot) {
        val world = _state.value.game
        val who = world.championIn(champion) ?: return
        val inSlot = who.holding(slot.slot)

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

        _state.update {
            it.copy(
                game = world.holding(inSlot)
                    .carrying(champion, slot.slot, world.inHand, itemTypes),
            )
        }
        announceTaking(world.item(inSlot))
        renderViewPort()
    }

    /**
     * The quiver, which holds a stack rather than one thing. An arrow in the
     * hand joins the ones already in it and the tally goes up; an empty hand
     * takes one back out and the tally goes down.
     */
    private fun useQuiver(champion: PartySlot, slot: InventorySlot, head: ItemIndex) {
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

        _state.update {
            it.copy(game = stacked.world.carrying(champion, slot.slot, stacked.head, itemTypes))
        }
        renderViewPort()
    }

    /** Whatever comes into the hand says what it is. */
    private fun announceTaking(item: Item?) {
        val names = itemNames ?: return
        if (item == null) return

        say(ItemMessages.taken(names.of(item, itemTypes)))
    }

    /** A line about one champion, which wants their name in it. */
    private fun sayOf(whose: PartySlot, line: (String) -> String) {
        _state.value.game.championIn(whose)?.let { say(line(it.name)) }
    }

    private fun say(line: String, ink: PaletteIndex = ScriptSpeech.DEFAULT_INK) {
        _state.update {
            it.copy(
                messages = (it.messages + PlayField.Message(line, ink))
                    .takeLast(MESSAGES_KEPT),
            )
        }
    }

    /**
     * Whoever has just climbed a level says so and is heard.
     *
     * A multi-class champion says it once however many of their classes came up
     * together, which is what the line is: they gained a level, not a level in
     * something.
     */
    private fun announceAnyLevelGained(was: List<Champion>, now: List<Champion>) {
        val climbed = now.filterIndexed { slot, champion ->
            val before = was.getOrNull(slot) ?: return@filterIndexed false
            champion.levels.zip(before.levels).any { (after, then) -> after.level > then.level }
        }
        if (climbed.isEmpty()) return

        climbed.forEach { say("${it.name} has gained a level.", LEVEL_GAINED_INK) }
        viewModelScope.launch { playTrack(LEVEL_GAINED) }
    }

    /**
     * The piece of floor a click is aimed at, when it lands on the picture of
     * something lying within reach.
     *
     * A click otherwise reaches whichever of the four strips of floor it fell
     * in, and a thing is drawn centred on its corner rather than inside a
     * strip — so the top of a thing on the party's own square is drawn up in
     * the strip belonging to the square ahead, and clicking it reaches past
     * it. Aiming at what is drawn is what a player means by the click; the
     * strips still answer everywhere else, so bare floor works as it did.
     *
     * Only what the party could reach anyway: a thing on a corner no strip
     * names is looked at rather than picked up, exactly as before.
     */
    private fun aimedAt(x: Int, y: Int): FloorReach? {
        val world = _state.value.game

        // A full hand is putting down rather than picking up, and what it is
        // aimed at is the floor the thing would stand on. Where two of those
        // overlap — a thing on the party's own square is drawn over the feet
        // of one on the square ahead — the nearer middle wins.
        if (world.inHand.isSomething) {
            return drawn?.landingSpots
                ?.filter { it.where.covers(x, y) }
                ?.minByOrNull { it.where.howFarFrom(x, y) }
                ?.reach
        }

        val ahead = party.facing.transformCoordinates(0, -1).let { (dx, dy) ->
            Location(party.position.x + dx, party.position.y + dy)
        }

        // the last drawn is the one on top, which is the one being looked at
        val clicked = drawn?.itemsOnTheFloor?.lastOrNull { it.covers(x, y) } ?: return null
        val item = world.item(clicked.slot) ?: return null

        return FloorReach.entries.firstOrNull { reach ->
            val square = if (reach.aheadOfTheParty) ahead else party.position
            item.location == square && item.place == reach.placeFacing(party.facing)
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

        val place = reach.placeFacing(party.facing)

        // What the square holds is the half of this worth reading in a log: a
        // click that finds nothing is either the wrong corner or the wrong
        // square, and the two look the same from the outside.
        Logger.d(TAG) {
            "Reached into $reach: $place on $at, holding ${world.inHand}, " +
                "lyingAt=${world.lyingAt(level, at, place)}, square holds " +
                world.items.withIndex()
                    .filter { (_, item) -> item.level == level && item.location == at }
                    .map { (slot, item) -> "$slot:${item.place}:${item.icon.value}" }
        }

        val changed = if (world.inHand.isSomething) {
            world.puttingDown(level, at, place)
        } else {
            val lying = world.lyingAt(level, at, place) ?: return false
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
        // Eating leaves the page open: the plate is there to be used while a
        // champion's things are being looked through.
        if (choice == SheetChoice.Eat) {
            plateOffered(sheet.slot)
            return
        }

        showSheet(
            when (choice) {
                SheetChoice.Close -> null
                SheetChoice.TurnPage -> sheet.turnedOver
                SheetChoice.Eat -> sheet
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
        get() = _state.value.sheet?.takeIf { _state.value.game.championIn(it.slot) != null }

    /** The champion's page as it is to be drawn, or null with none on show. */
    private fun openSheet(): OpenSheet? {
        val sheet = sheetOnShow ?: return null
        val world = _state.value.game
        val champion = world.championIn(sheet.slot) ?: return null

        return OpenSheet(
            page = sheet.page,
            champion = champion,
            carrying = champion.carrying.map { world.item(it) },
            arrows = world.stackedIn(champion.holding(CarrySlot.QUIVER)),
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

        val level = levelNumber(inf.name)

        // the world's wall, not the file's: a wall a script has already opened
        // is no longer the one with the button on it
        val wall = _state.value.game.wall(level, ahead, facingUs)

        // A door is worked by the button beside it and by nothing else. One
        // with no button, or a click that misses the button it has, gets the
        // line the game gives a door that will not be opened by hand — but
        // only while it is shut, an open doorway having nothing to say.
        if (wall is Maz.WallType.Door) {
            val door = sublevel.doors.getOrNull(wall.doorIndex.value) ?: return

            when {
                wall.hasButton && ClickedWall.hitsDoorButton(door, x, y) ->
                    swingsTheDoor(level, ahead, facingUs, opening = !wall.isOpen)

                !wall.isOpen -> {
                    say(DoorMessages.NO_ONE_CAN_PRY)
                    drawWords()
                }
            }
            return
        }

        if (wall !is Maz.WallType.Decoration) return

        val decoration = sublevel.decorations
            .firstOrNull { it.decorationWallIndex == wall.decorationWallIndex }
            ?: return

        val hanging = decoration.dec.decorations
            .firstOrNull { it.index == decoration.decorationID }

        val does = decoration.doesWhenClicked

        // Some walls have nothing to aim at and answer a click anywhere on
        // them, a door is shoved by its doorway, and the rest want the thing
        // hanging there hit.
        val hit = when {
            does.answersAnyClick -> true
            does.isShoved -> ClickedWall.hitsTheDoorway(x, y)
            else -> hanging != null && ClickedWall.hits(hanging, decoration.dec.rectangles, x, y)
        }

        Logger.d(TAG) { "Clicked $ahead $facingUs is a $does, hit=$hit" }
        if (!hit) return

        // Most walls answer with their script and nothing else. These few are
        // worked as well: something on them moves, or something is taken from
        // them, and only then does the script have its say.
        when (does) {
            // what is shelved in a niche is taken, or what is held is put on it
            WallAction.NICHE -> reachedIntoNiche(level, ahead)

            WallAction.LEVER_ON, WallAction.LEVER_OFF ->
                threwTheLever(level, ahead, facingUs, up = does == WallAction.LEVER_ON)

            WallAction.STUCK_DOOR -> forcedTheDoor(level, ahead, facingUs)

            WallAction.JAMMED_DOOR -> {
                say(DoorMessages.NO_ONE_CAN_PRY)
                drawWords()
            }

            else -> runTriggersAt(ahead, ScriptEvent.WALL_CLICKED)
        }
    }

    /**
     * A door set going by its button. The button is what is heard here; the
     * door is heard by the clock that moves it.
     */
    private fun swingsTheDoor(level: Int, at: Location, side: WallSide, opening: Boolean) {
        viewModelScope.launch {
            playTrack(DOOR_BUTTON)
            _state.update { it.copy(game = it.game.doorSetGoing(level, at, side, opening)) }
            renderViewPort()
        }
    }

    /**
     * The clock that moves whatever doors are going, a position at a time.
     *
     * A door outlives what started it — a button press, a script that ended
     * two instructions later — so nothing holds it and the party are free to
     * turn and watch. It stops when the last one has arrived, or the game
     * would redraw for ever.
     */
    private fun keepDoorsGoing(anyGoing: Boolean) {
        if (anyGoing == (swingingDoors?.isActive == true)) return

        swingingDoors?.cancel()
        swingingDoors = if (!anyGoing) {
            null
        } else {
            viewModelScope.launch {
                while (_state.value.game.swinging.isNotEmpty()) {
                    delay(DOOR_STEP.inMilliseconds)

                    Logger.d(TAG) { "Door step, going ${_state.value.game.swinging}" }
                    val stepped = _state.value.game.doorsStepped()
                    _state.update { it.copy(game = stepped.world) }
                    stepped.heard.forEach { playTrack(it) }
                    drawViewPort()
                }
            }
        }
    }

    /**
     * A lever, which flips to its other position and then lets the wall's
     * script say what that did.
     */
    private fun threwTheLever(level: Int, at: Location, side: WallSide, up: Boolean) {
        _state.update { it.copy(game = it.game.leverThrown(level, at, side, up)) }

        // the script draws for itself when it has anything to say
        if (!runTriggersAt(at, ScriptEvent.WALL_CLICKED)) renderViewPort()
    }

    /**
     * A door stuck in its frame, which the strongest of the party puts a
     * shoulder to. One that gives becomes a doorway like any other.
     */
    private fun forcedTheDoor(level: Int, at: Location, side: WallSide) {
        val outcome = ForcingADoor.tried(_state.value.game.champions, Dice.random)

        say(outcome.says)
        drawWords()

        if (outcome != ForcingADoor.Outcome.Gives) return

        // a doorway takes the wall's place still shut, and then swings
        _state.update { it.copy(game = it.game.forcedOutOfItsFrame(level, at, side)) }
        swingsTheDoor(level, at, side, opening = true)
    }

    /**
     * The shelf set into a wall: what is on it comes into an empty hand, and
     * what is held goes onto it — but only if it is one of the small shapes,
     * a niche having no room for anything bigger.
     */
    private fun reachedIntoNiche(level: Int, at: Location) {
        val world = _state.value.game

        val changed = if (!world.inHand.isSomething) {
            val shelved = world.lyingAt(level, at, SquarePlace.IN_A_NICHE) ?: return
            announceTaking(world.item(shelved))
            world.takingUp(shelved)
        } else {
            if (Cps.shapeOf(world.held!!.icon) !is Cps.ShapeLocation.SmallItem) {
                say(ItemMessages.TOO_LARGE_TO_FIT)
                drawWords()
                return
            }
            world.puttingDown(level, at, SquarePlace.IN_A_NICHE)
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

    /**
     * The world as the script has it, with the party as they now stand.
     *
     * A script is handed the world when it starts and gives it back when it
     * ends, so anything the player did meanwhile is in neither copy — and the
     * party are the one thing the player can move while a script runs. Taking
     * the script's walls and the player's party is what keeps a step made
     * while a door swung from being undone when the door finishes.
     *
     * A script that has taken the party is the one steering them, and then its
     * own copy is the true one.
     */
    private fun GameState.withWhoeverStandsThere(live: GameState): GameState =
        if (scriptHasTheParty) this else copy(party = live.party)

    private fun runTriggersAt(
        at: Location,
        event: ScriptEvent,
        used: ItemIndex? = null,
        /**
         * Whether the floor set this off rather than anybody: see
         * [ScriptTimer]. Such a run that changes nothing leaves no trace at
         * all — neither a line nor a frame.
         */
        byItself: Boolean = false,
    ): Boolean {
        val inf = _state.value.inf ?: return false
        val runner = scriptRunner ?: return false

        playing?.cancel()
        scriptHasTheParty = false
        playing = viewModelScope.launch {
            val run = runner.onEvent(
                triggers = inf.triggers,
                event = event,
                state = _state.value.game,
                stage = stage,
                at = at,
                used = used,
                byItself = byItself,
            )
            // Whatever the script left on screen goes with it. Scripts end
            // without closing the box they last wrote in — the one that walks
            // the party downstairs says so and changes level on the next
            // instruction — and a box with nothing to click cannot be got rid
            // of by the player.
            val wasSaying = _state.value.dialog != null
            val stood = _state.value.game
            if (wasSaying) silenceEffects()
            _state.update {
                it.copy(
                    game = run.state
                        .withWhoeverStandsThere(it.game)
                        .asAScriptLeaves(it.game)
                        .blowsShown(run.hurt),
                    dialog = null,
                )
            }
            speaker = null
            scriptHasTheParty = false
            if (run.hurt.isNotEmpty()) letTheDamageFade()

            val change = run.changeLevel
            if (change == null) {
                if (run.worthNoticing(before = stood, byItself = byItself, aBoxWasUp = wasSaying)) {
                    drawViewPort()
                    autosave()
                }
            } else {
                Logger.i(TAG) {
                    "Changing to level ${change.level} sublevel ${change.subLevel} " +
                        "at ${change.location}"
                }
                // The bank goes with the level, so whatever is still sounding
                // belongs to a floor the party has left.
                audioSink.stopAll()
                sounding = null
                onVmpSelected(
                    name = "LEVEL${change.level}.INF",
                    subLevel = change.subLevel,
                    playerX = change.location.x,
                    playerY = change.location.y,
                    direction = change.direction,
                    walkingIn = true,
                )
            }
        }
        return true
    }

    /**
     * Plays a numbered track out of whichever bank the sublevel the party is
     * standing in names.
     *
     * The number alone does not say what will be heard: each level names its
     * own bank, and the same number is a different recording from one floor to
     * the next. A bank that has nothing under that number is not a fault —
     * the forest has no voice for someone who is not out there.
     *
     * There are four ways of playing nothing, and from outside they all look
     * the same: a speaker working perfectly and a track nobody rendered are
     * both silence. Each says which one it was, so the difference can be read
     * rather than guessed at.
     */
    /**
     * @param alongside whether this may sound over whatever is still playing
     *   rather than taking its place. For the few effects written to be heard
     *   together: a pair meant as one noise is two clicks if the second stops
     *   the first.
     */
    private suspend fun playTrack(
        track: TrackIndex,
        volume: Volume = Volume.FULL,
        alongside: Boolean = false,
    ) {
        if (!_state.value.preferences.sounds) {
            Logger.d(TAG) { "Not playing $track: sounds are switched off" }
            return
        }

        val inf = _state.value.inf ?: run {
            Logger.d(TAG) { "Not playing $track: no level loaded" }
            return
        }
        val bank = inf.subLevels.getOrNull(_state.value.subLevel)?.sound ?: run {
            Logger.d(TAG) { "Not playing $track: sublevel has no sound bank" }
            return
        }

        val clip = soundRepository.clip(SoundBank(bank), track)
            .onFailure { Logger.d(TAG) { "Not playing $track: $bank has nothing under it" } }
            .getOrNull() ?: return

        // One effect at a time. The chip the banks were written for had nine
        // voices and a new program took them over, so nothing could pile up on
        // it; here nothing stops it, and a level whose scripts hand out
        // seventeen second drones turns a walk into one long chord.
        //
        // Which leaves nowhere for the handful of effects written to be heard
        // at once, so those say so and are let through. What they start is not
        // held onto: it is short, and stopping it is what they are avoiding.
        if (alongside) {
            audioSink.play(clip, volume)
            return
        }

        sounding?.stop()
        sounding = audioSink.play(clip, volume)
    }

    /**
     * The screen, from a running script's side.
     *
     * A script draws, waits and asks where it stands, so this is what it draws
     * on, what holds its pauses, and what its question is put up as.
     */
    private val stage = object : ScriptStage {

        override suspend fun show(world: GameState) {
            _state.update { it.copy(game = world.withWhoeverStandsThere(it.game)) }
            drawViewPort()
        }

        override fun takesTheParty() {
            scriptHasTheParty = true
        }

        /**
         * A script's sound outlives the script. Triggers are run in a job the
         * next step cancels, and a lever thrown on the way past would lose its
         * click halfway through being fetched.
         */
        override suspend fun play(track: TrackIndex) {
            viewModelScope.launch { playTrack(track) }
        }

        /**
         * The box a question would be asked in, with no question in it: the
         * script has written a line and will hold the screen while it is read.
         */
        override suspend fun say(speech: ScriptSpeech) {
            val inf = _state.value.inf ?: return

            if (speech.boxJustDrawn) standingInTheBox = emptyList()

            if (speech.isEmpty) {
                silenceEffects()
                _state.update { it.copy(dialog = null) }
                speaker = null
                standingInTheBox = emptyList()
                drawWords()
                return
            }

            val spoken = (standingInTheBox + speech.said.mapNotNull { inf.message(it) })
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

        // A gap in the interpreter is the log's business and not the bar's.
        // Written on the bar it outlives the square that caused it — the bar
        // keeps its lines across steps, across levels and into the save — so a
        // notice from one floor is still sitting there on the next, read as
        // coming from wherever the party now stand. The runner logs it with
        // the level, the square and the whole instruction, which is what
        // anybody going to write it needs anyway.

        override suspend fun hold(ticks: Ticks) = delay(ticks.inMilliseconds)

        /**
         * The archway, put up whole and then opened a step at a time.
         *
         * It stands over the view rather than in a box, so the view is drawn
         * again for every step of it — which is what the arch is drawn on top
         * of, and what is left when it goes.
         */
        override suspend fun opensThePortal() {
            val arch = pictureCalled("PORTALA.CPS")
            val through = pictureCalled("PORTALB.CPS")

            if (arch == null || through == null) {
                notImplemented("the archway, whose pictures would not load")
                return
            }

            suspend fun draw(step: ThePortal.Step) {
                portalShowing = ThePortal.Showing(step, arch, through)
                drawViewPort()
                step.sounds.forEach { track ->
                    viewModelScope.launch { playTrack(track, alongside = true) }
                }
            }

            try {
                // It appears shut and entire, and stands so before it stirs.
                draw(ThePortal.Step(arch = 0, showing = null))
                ThePortal.AS_IT_APPEARS.forEach { track ->
                    viewModelScope.launch { playTrack(track, alongside = true) }
                }
                hold(ThePortal.BEFORE_IT_STIRS)

                ThePortal.OPENS.forEach { step ->
                    draw(step)
                    hold(ThePortal.A_STEP)
                }
            } finally {
                // Whatever stopped it, the arch does not stay on the screen.
                portalShowing = null
                drawViewPort()
            }
        }

        override suspend fun ask(question: ScriptQuestion): DialogAnswer {
            Logger.i(TAG) { "Script is showing text ${question.textId} with ${question.buttons}" }

            return awaiting.ask { showDialog(question) }.also {
                // An answered question leaves the box drawn again behind it, so
                // whatever is said to the answer is said on a clean one rather
                // than under the question it answers.
                standingInTheBox = emptyList()
            }
        }
    }

    /**
     * A script stopped to ask something. The speech comes from TEXT.DAT and
     * the button words from the level's own messages.
     *
     * @return whether the script waits for this to be clicked.
     */
    private suspend fun showDialog(question: ScriptQuestion): Boolean {
        val inf = _state.value.inf ?: return false

        val speech = question.textId
            ?.let { asked ->
                dialogueTextRepository.text(asked)
                    .onFailure { Logger.e(it) { "No dialogue text $asked" } }
                    .getOrNull()
            }
            ?: question.spoken?.let { DialogueText(listOf(it)) }
            ?: DialogueText.EMPTY

        val labels = question.words.ifEmpty { question.buttons.mapNotNull { inf.message(it) } }
        val unread = speech.pages.drop(1)

        val clickable = question.hasSomethingToClick(labels)

        // the party's own line goes in the box above what it answers
        val spoken = (standingInTheBox + question.said.mapNotNull { inf.message(it) } + speech.first)
            .filter { it.isNotBlank() }
            .joinToString("\n") { it.spokenBy(whoeverSpeaks()) }

        // and what is being said now stands in the box after it has been read
        standingInTheBox = standingInTheBox + speech.first

        _state.update {
            it.copy(
                dialog = DialogPrompt(
                    scene = sceneFor(
                        scene = question.scene,
                        text = spoken,
                        buttonLabels = when {
                            unread.isNotEmpty() -> listOf(MORE)
                            clickable -> labels
                            else -> emptyList()
                        },
                        waitsToBeRead = unread.isNotEmpty() || question.waitsToBeRead,
                        met = question.met,
                    ),
                    unread = unread,
                    buttons = labels,
                    waitsToBeRead = question.waitsToBeRead,
                )
            )
        }
        drawWords()

        return unread.isNotEmpty() || clickable
    }

    /**
     * Whoever the party have run into, standing in the view while they speak.
     *
     * They are not one of the pictures a level names: everybody the dungeon
     * holds is cut from one sheet, at their own size, and stands on the floor
     * of the view rather than inside the frame a speaker is framed in.
     */
    private suspend fun whoeverIsMet(npc: NpcId): DialogueScene.Picture? {
        val meeting = NpcMeeting.called(npc) ?: return null
        val sheet = pictureCalled(NpcMeeting.SHEET) ?: return null

        return DialogueScene.Picture(
            cps = sheet,
            sourceLeft = 0,
            sourceTop = meeting.standing.sourceTop,
            goes = meeting.standing.inTheView(),
        )
    }

    /**
     * Turns the script's drawing instructions into something the play field can
     * put on screen.
     *
     * Speakers are packed four to a file, and the instruction's x and y name the
     * corner to cut out — x in units of eight pixels, as the file counts
     * them — and its rect says which of the two places it goes.
     */
    private suspend fun sceneFor(
        scene: List<Dialog>,
        text: String,
        buttonLabels: List<String>,
        waitsToBeRead: Boolean,
        readOff: DialogueScene.ReadOff.Written = DialogueScene.ReadOff.TheStripBelow,
        met: NpcId? = null,
    ): DialogueScene {
        val font = font ?: return DialogueScene(null, null, emptyList(), emptyList())

        val instruction = scene.filterIsInstance<Dialog.DisplayPicture>().lastOrNull()
        val portrait = if (met != null) {
            whoeverIsMet(met)
        } else if (instruction == null) {
            // a reply draws no one: whoever is speaking stays up while they talk
            speaker
        } else {
            pictureCalled("${instruction.pictureName.uppercase()}.CPS")
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
            readOff = readOff,
        )
    }

    /**
     * The file a script draws its pictures from, kept from one to the next.
     *
     * An animation is a run of frames cut from a single sheet, a fifth of a
     * second apart, and unpacking that sheet again for every frame is most of
     * the time between them.
     */
    private suspend fun pictureCalled(name: String): Cps? {
        drawnFrom?.let { (called, cps) -> if (called == name) return cps }

        val cps = cpsRepository.loadCps(name)
            .onFailure { error -> Logger.e(error) { "No picture $name" } }
            .getOrNull()

        drawnFrom = cps?.let { name to it }
        return cps
    }

    /** Puts the next part of a speech up, without letting the script move on. */
    private fun turnThePage(dialog: DialogPrompt) {
        val unread = dialog.unread.drop(1)

        // turning a page empties the box and writes on from there, so the page
        // being read is the whole of what stands in it
        standingInTheBox = listOf(dialog.unread.first())

        viewModelScope.launch {
            _state.update {
                it.copy(
                    dialog = dialog.copy(
                        scene = sceneFor(
                            scene = emptyList(),
                            text = dialog.unread.first(),
                            buttonLabels = if (unread.isEmpty()) dialog.buttons else listOf(MORE),
                            waitsToBeRead = unread.isNotEmpty() || dialog.waitsToBeRead,
                            readOff = dialog.readOff,
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

        // A script draws whatever follows its own question. Nothing follows a
        // page read off a thing that was picked up, so without this the box
        // would stay on screen until something else happened to draw.
        if (!awaiting.answer(answer)) drawWords()
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

        val sub = followTheWalls(inf, wallAt)
        val sublevel = inf.subLevels[sub]

        _state.update {
            val mapped = it.game.visiting(level, party.position)
            it.copy(game = mapped, visited = mapped.visited(level))
        }

        // Every square they cover is a square nearer their next meal. Turning
        // on the spot is not covering one, and neither is standing still.
        val standing = Location(party.position.x, party.position.y)
        if (wasStandingOn != null && wasStandingOn != standing) theyWalkedASquare()
        wasStandingOn = standing

        viewConeRepository.renderPosition(
            items = _state.value.game.items,
            monsters = _state.value.game.monsters,
            sublevel = sublevel,
            playerX = party.position.x,
            playerY = party.position.y,
            direction = party.facing,
            wallAt = wallAt,
            pulse = pulse,
            fromTheBottomUp = _state.value.game.fromTheBottomUp,
            holding = _state.value.game.inHand.takeIf { it.isSomething },
            inFlight = _state.value.game.inFlight,
            bursting = _state.value.game.bursting,
        ).onSuccess { viewPort ->
            drawn = viewPort
            paint(viewPort, sublevel.palette)
        }.onFailure {
            Logger.e(it) { "Error while rendering position" }
        }

        keepFlickering(teleportersInView(party.position, party.facing, wallAt).isNotEmpty())
        keepDoorsGoing(_state.value.game.swinging.isNotEmpty())

        // A fight can start without the party doing anything: level 5's scene
        // rouses the pair itself once they choose to attack. Asking here means
        // every way of starting one winds the clock, rather than each having
        // to remember to.
        keepTheFightGoing()
    }

    /**
     * Which sublevel to draw, having let the walls in sight correct it.
     *
     * Only walking through one can put the party somewhere no script sent
     * them, so this is asked only while walls are passable. In an ordinary
     * game the sublevel is whatever sent the party here said it was, and
     * guessing it from the walls is not a safer answer than that one — it is
     * a worse one. Where a sublevel maps every wall another maps and more, a
     * single glimpse of a wall only the second draws moves the party to it,
     * and nothing ever moves them back: every monster of the floor they left
     * then stands there undrawn, in a corridor that looks empty.
     */
    private fun followTheWalls(inf: Inf, wallAt: (Location, WallSide) -> Maz.WallType): Int {
        if (!debugging.wallsArePassable.value) return _state.value.subLevel

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
     * see.
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
                thrown = thrownShapes,
                preferences = _state.value.preferences,
            )
                .render(
                    viewPort = viewPort,
                    direction = party.facing,
                    dialogue = _state.value.dialog?.scene,
                    messages = _state.value.messages,
                    party = roster,
                    portraits = portraits,
                    metPortraits = metPortraits,
                    menu = _state.value.menu,
                    sheet = openSheet(),
                    carrying = { _state.value.game.item(it) },
                    recovering = { whose, hand -> _state.value.game.isRecovering(whose, hand) },
                    reporting = { whose, hand -> _state.value.game.reportIn(whose, hand) },
                    hurt = { whose -> _state.value.game.damageShownOn(whose) },
                    swapping = _state.value.swapping
                        ?.takeIf { !_state.value.swapShowing },
                    portal = portalShowing,
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
            Direction.NORTH -> onWalked(y = party.position.y - 1)
            Direction.EAST -> onWalked(x = party.position.x + 1)
            Direction.SOUTH -> onWalked(y = party.position.y + 1)
            Direction.WEST -> onWalked(x = party.position.x - 1)
        }
    }

    private fun onMoveForward() {
        when (party.facing) {
            Direction.NORTH -> onWalked(y = party.position.y - 1)
            Direction.EAST -> onWalked(x = party.position.x + 1)
            Direction.SOUTH -> onWalked(y = party.position.y + 1)
            Direction.WEST -> onWalked(x = party.position.x - 1)
        }
    }

    private fun onMoveBackwards() {
        when (party.facing) {
            Direction.NORTH -> onWalked(y = party.position.y + 1)
            Direction.EAST -> onWalked(x = party.position.x - 1)
            Direction.SOUTH -> onWalked(y = party.position.y - 1)
            Direction.WEST -> onWalked(x = party.position.x + 1)
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

        /**
         * The other click, which uses what a slot holds rather than picking it
         * up: reading what is written on a thing, drinking it, aiming it.
         */
        data class UsedWhatIsAt(val x: Int, val y: Int) : Event()
        data object MoveForward : Event()
        data object MoveBackwards : Event()
        data object StrafeLeft : Event()
        data object StrafeRight : Event()
        data object RotateRight : Event()
        data object RotateLeft : Event()

        /** The bench's own: every hand of the front rank swings at once. */
        data object FrontRankStrikes : Event()
        data object Camp : Event()

        /** A key press, while a save is being named. */
        data class Typed(val typing: Typing) : Event()

        /**
         * Whether this is the party moving themselves, which is the one thing
         * they are made to take their time over. Turning counts: it is a step
         * of the same length, and a fight is danced as much with turns as
         * with steps.
         */
        val isAStep: Boolean
            get() = this is MoveForward || this is MoveBackwards ||
                this is StrafeLeft || this is StrafeRight ||
                this is RotateLeft || this is RotateRight

        /**
         * Whether this is the party acting rather than the game being set up
         * or asked a question. It is what a monster's swing suspends.
         */
        val isTheirOwnDoing: Boolean
            get() = isAStep || this is ClickedTheView || this is UsedWhatIsAt ||
                this is Camp
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
        /** Where the pages after the first go up, which is where the first did. */
        val readOff: DialogueScene.ReadOff.Written = DialogueScene.ReadOff.TheStripBelow,
    )

    data class State(
        val inf: Inf? = null,

        /**
         * Which of the level's sublevels the party are in. Nothing in the maze
         * says: a script puts them in one, and walking somewhere no script
         * sent them is settled by [Inf.subLevelShowing].
         */
        val subLevel: Int = 0,

        /** Who has been named to change places, while nobody has been named to. */
        val swapping: PartySlot? = null,

        /**
         * Which half of the flicker the named champion's strip is on: their own
         * name, or the word for what is being done to them.
         */
        val swapShowing: Boolean = true,

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

        /** The squares the party have stood on in the sublevel they are in. */
        val visited: Set<Location> = emptySet(),

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

        /** How long a door rests at each of the positions it slides through. */
        private val DOOR_STEP = Ticks(5)

        /** How long each half of a waiting champion's name flicker lasts. */
        private val SWAP_FLICKER = Ticks(9)

        /** How long an hour of sleep takes to watch. */
        private val AN_HOUR_OF_SLEEP = Ticks(3)

        /**
         * How far the party walk on one mouthful.
         *
         * The original spends a point of food a minute of play whatever the
         * party are doing — reading, fighting, standing still — which comes to
         * a full stomach every hour and a half at the table. Only walking
         * counts here, and a minute of the original's walking is about this
         * many squares, so this is that minute with the idle time taken out.
         */
        private const val STEPS_TO_A_MEAL = 100

        private const val CANNOT_REST_HERE = "You do not feel it is safe to rest here."
        private const val MONSTERS_ARE_NEAR = "You can't rest here, monsters are near."
        private const val FULLY_RESTED = "All characters are fully rested."
        private const val STARVING = "Your party is starving."

        private val WALL_BUMP = TrackIndex(29)
        private val EAT = TrackIndex(9)
        private val LEVEL_GAINED = TrackIndex(23)
        private val LEVEL_GAINED_INK = ScriptSpeech.inkOf(1)
        private val DOOR_BUTTON = TrackIndex(6)

        /** What a bottle being emptied sounds like. */
        private val DRINK = TrackIndex(10)

        /**
         * How often the venom takes its bite, and how much. Both transcribed:
         * a flat five, nothing rolled and no saving throw, every 546 ticks —
         * which is about half a minute of playing.
         */
        private val POISON_BITES = Ticks(546)
        private val POISON_TAKES = Damage(5)

        /** And under 32: a weapon swung, whether or not it finds anything. */
        private val SWING = TrackIndex(32)

        /** Something being loosed down a corridor, whoever loosed it. */
        private val LOOSED = TrackIndex(11)

        /** And what it sounds like arriving. */
        private val BURSTING = TrackIndex(35)

        /** How long a struck monster is drawn as a silhouette. */
        private val FLASH = Ticks(2)

        /** How long one frame of a monster's swing is held. Transcribed. */
        private val A_SWING_FRAME = Ticks(8)

        /** And how often a monster's group takes a turn. Also transcribed. */
        private val A_MONSTER_TURN = Ticks(20)

        /**
         * How far into the first turn each of the four groups first acts.
         * Transcribed, and the last two share a start.
         */
        private val WHEN_EACH_GROUP_STARTS = listOf(0, 7, 14, 14)

        /** How many steps before monsters try the other way round a corner. */
        private const val STEPS_BEFORE_SWAPPING = 10

        private const val PLAY_FIELD_CPS = "PLAYFLD.CPS"
        private const val DECORATIONS_CPS = "DECORATE.CPS"
        private const val DIALOGUE_FRAME_CPS = "BORDER.CPS"
        private const val DIALOGUE_FONT = "FONT6.FNT"

        /** The bigger one the interface is set in. */
        private const val MENU_FONT = "FONT8.FNT"
        private const val PORTRAITS_CPS = "CHARGENA.CPS"

        /** The faces of the people the dungeon holds, rather than made ones. */
        private const val MET_PORTRAITS_CPS = "OUTPORTS.CPS"
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
