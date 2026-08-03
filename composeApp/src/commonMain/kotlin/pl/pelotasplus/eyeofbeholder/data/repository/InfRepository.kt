package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.DamageDice
import pl.pelotasplus.eyeofbeholder.data.model.Dec
import pl.pelotasplus.eyeofbeholder.data.model.Decoration
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Door
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Trigger
import pl.pelotasplus.eyeofbeholder.data.model.TriggerFlags
import pl.pelotasplus.eyeofbeholder.data.model.MonsterDecorationSetId
import pl.pelotasplus.eyeofbeholder.data.model.MonsterGfx
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.ScriptTimer
import pl.pelotasplus.eyeofbeholder.data.model.SubLevel
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.script.ClearFlag
import pl.pelotasplus.eyeofbeholder.data.model.script.CloseDoor
import pl.pelotasplus.eyeofbeholder.data.model.script.ConsumeItem
import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.Damage
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
import pl.pelotasplus.eyeofbeholder.data.model.script.Encounter
import pl.pelotasplus.eyeofbeholder.data.model.script.End
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.GoSub
import pl.pelotasplus.eyeofbeholder.data.model.script.Goto
import pl.pelotasplus.eyeofbeholder.data.model.script.Launcher
import pl.pelotasplus.eyeofbeholder.data.model.script.Message
import pl.pelotasplus.eyeofbeholder.data.model.script.NewItem
import pl.pelotasplus.eyeofbeholder.data.model.script.NewLevelOrMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.OpenDoor
import pl.pelotasplus.eyeofbeholder.data.model.script.Return
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.SetFlag
import pl.pelotasplus.eyeofbeholder.data.model.script.SetWall
import pl.pelotasplus.eyeofbeholder.data.model.script.Sound
import pl.pelotasplus.eyeofbeholder.data.model.script.SpecialEvent
import pl.pelotasplus.eyeofbeholder.data.model.script.Teleport
import pl.pelotasplus.eyeofbeholder.data.model.script.ToggleWall
import pl.pelotasplus.eyeofbeholder.data.model.script.Turn
import pl.pelotasplus.eyeofbeholder.data.model.script.UpdateScreen
import pl.pelotasplus.eyeofbeholder.data.model.script.Wait

/**
 * Parses .INF files — the most complex game data format, containing complete level data.
 *
 * An INF file is LCW-compressed and structured as three sequential blocks:
 *
 * ## Block A — SubLevels (from start to offsetBlockB)
 * A linked list of sublevel definitions. Each sublevel contains:
 * - Maze reference (.MAZ filename → loaded via [MazRepository])
 * - Viewport mapping reference (.VMP → [VmpRepository])
 * - Tile set reference (.VCN, derived from VMP name → [VcnRepository])
 * - Palette (.PAL → [PalRepository], either custom or derived from VMP name)
 * - Sound filename
 * - 2 door definitions with CPS graphics, rectangles, and buttons
 * - Monster graphics (up to 2 CPS sprite sheets)
 * - Monster properties (AD&D stats, terminated by 0xFF)
 * - Decoration blocks (DEC+CPS pairs, then wall-to-decoration mappings)
 * - Script timers (function/ticks pairs, terminated by 0xFFFF)
 * - 0xFFFF padding between sublevels
 *
 * ## Block B — Scripts & Messages (from offsetBlockB to offsetBlockC)
 * - Monster instance data (30 entries)
 * - Script bytecode (29 opcodes, see ScriptToken hierarchy)
 * - Null-terminated message strings
 *
 * ## Block C — Trigger Map (from offsetBlockC to end)
 * - Count of trigger entries
 * - Each entry: packed location, flags, script offset
 *
 * ## Opcode table (Block B script)
 * ```
 * 0xFF=SetWall  0xFE=ToggleWall  0xFD=OpenDoor   0xFC=CloseDoor
 * 0xFB=CreateMonster  0xFA=Teleport  0xF8=Message  0xF7=SetFlag
 * 0xF6=Sound  0xF5=ClearFlag  0xF3=Damage  0xF2=Goto  0xF1=End
 * 0xF0=Return  0xEF=GoSub  0xEE=Eval  0xED=ConsumeItem
 * 0xEC=NewLevelOrMonster  0xEA=NewItem  0xE9=Launcher  0xE8=Turn
 * 0xE6=Encounter  0xE5=Wait  0xE4=UpdateScreen  0xE3=Dialog
 * 0xE2=SpecialEvent
 * ```
 */
interface InfRepository {
    suspend fun loadInf(name: String, items: List<Item>): Result<Inf>

    /**
     * Only the level's script. Block B does not refer back to Block A, so this
     * seeks straight past it and loads none of the mazes, tile sets, palettes
     * or graphics a full [loadInf] pulls in.
     */
    suspend fun loadScript(name: String): Result<List<Script>>

    suspend fun getAllInfNames(): Result<List<String>>
}

class InfRepositoryImpl(
    private val resourceRepository: ResourceRepository,
    private val mazRepository: MazRepository,
    private val vmpRepository: VmpRepository,
    private val vcnRepository: VcnRepository,
    private val palRepository: PalRepository,
    private val cpsRepository: CpsRepository,
    private val decRepository: DecRepository
) : InfRepository {

    private val TAG = "InfRepository"

    override suspend fun loadInf(name: String, items: List<Item>): Result<Inf> {
        return runCatching {
            val decompressed = resourceRepository.decompressResource("files/$name").bytes
            decodeInf(name, decompressed, items)
        }
    }

    override suspend fun loadScript(name: String): Result<List<Script>> {
        return runCatching {
            val decompressed = resourceRepository.decompressResource("files/$name").bytes
            val reader = ByteReader(decompressed)

            val offsetBlockB = reader.readU16LE()
            reader.skip(offsetBlockB - reader.offset)

            readBlockBHeader(reader)
            readScript(reader)
        }
    }

    private suspend fun decodeInf(name: String, bytes: UByteArray, items: List<Item>): Inf {
        val levelNumber = name.replace("LEVEL", "").replace(".INF", "").toInt()

        val reader = ByteReader(bytes)

        val offsetBlockB = reader.readU16LE()
        Logger.d(TAG) { "Block B starts at $offsetBlockB" }

        val subLevels = mutableListOf<SubLevel>()

        var subLevelIndex = 0
        while (reader.offset < offsetBlockB) {
            val nextSubLevelOffset = reader.readU16LE()
            Logger.d(TAG) { "Starting sublevel at ${reader.offset} nextSubLevelOffset $nextSubLevelOffset offsetBlockB offset is $offsetBlockB" }

            var cmd = reader.readU8()
            check(cmd == 0xEC) { "expected 0xEC, got ${cmd.toHexString()}" }

            val mazName = reader.readString(13).uppercase()
            Logger.d(TAG) { "Maz name '$mazName'" }

            val maz = mazRepository.loadMaz(mazName).getOrThrow()

            val vmpName = reader.readString(13)
                .uppercase() + ".VMP"
            Logger.d(TAG) { "VMP name '$vmpName'" }
            val vmp = vmpRepository.loadVmp(vmpName).getOrThrow()

            val vcnName = vmpName.replace(".VMP", ".VCN")
            Logger.d(TAG) { "VCN name '$vcnName'" }
            val vcn = vcnRepository.loadVcn(vcnName).getOrThrow()

            cmd = reader.readU8()
            check(cmd == 0xFF || cmd == 0x01) { "expected 0xFF or 0x01, got 0x${cmd.toHexString()}" }

            val palette = if (cmd != 0xFF) {
                reader.readString(13).uppercase() + ".PAL"
            } else {
                vmpName.replace(".VMP", ".PAL")
            }
            Logger.d(TAG) { "Palette name '$palette'" }
            val pal = palRepository.loadPal(palette).getOrThrow()

            val sound = reader.readString(13)
            Logger.d(TAG) { "Sound file '$sound'" }

            val doors = readDoors(reader)
            doors.forEach { door ->
                Logger.d(TAG) { "Got door: $door" }
            }

            val stepsUntilScriptCall = reader.readU16LE()
            Logger.d(TAG) { "stepsUntilScriptCall/maxMonstersCount is $stepsUntilScriptCall" }

            val monsterGfx = readMonsterGfx(reader)
            monsterGfx.forEach { monsterGfx ->
                Logger.d(TAG) { "Got monster gfx: $monsterGfx" }
            }

            val monsters = readMonsterProperties(reader)
            monsters.forEach {
                Logger.d(TAG) { "Got monster: $it" }
            }

            cmd = reader.readU8()
            check(cmd == 0xEC) { "expected 0xFF, got 0x${cmd.toHexString()}" }

            val decorations = mutableListOf<Decoration>()

            var dec: Dec? = null
            var cps: Cps? = null

            val decorationBlocks = reader.readU16LE()
            Logger.d(TAG) { "Decorations block count $decorationBlocks" }
            repeat(decorationBlocks) {
                cmd = reader.readU8()
                if (cmd == 0xEC) {
                    // read decorations
                    val cpsName = reader.readString(13).uppercase() + ".CPS"
                    val decName = reader.readString(13).uppercase()
                    dec = decRepository.loadDec(decName).getOrThrow()
                    cps = cpsRepository.loadCps(cpsName).getOrThrow()
                    Logger.d(TAG) { "Decoration: CPS: $cps" }
                    Logger.d(TAG) { "Decoration: DEC: $dec" }
                    dec.decorations.forEach { decoration ->
                        Logger.d(TAG) { "Decoration $decoration" }
                    }
                } else if (cmd == 0xFB) {
                    // assign decorations
                    /**
                     * struct WallMapping
                     * {
                     *    unsigned char wallMappingIndex; /* This is the index used by the .maz file. */
                     *    unsigned char wallType; /* Index to what backdrop wall type that is being used. */
                     *    unsigned char decorationID; /* Index to and optional overlay decoration image in
                     *                                   the DecorationData.decorations array in the
                     *                                   [[eob.dat|.dat]] files. */
                     *    unsigned char unknownFlags1;
                     *    unsigned char unknownFlags2;
                     * };
                     */
                    val decorationWallIndex = reader.readU8() // wallIndex
                    val wallType = reader.readU8() // vmpIndex
                    val decorationID = reader.readI8() // decIndex
                    val specialType = reader.readU8() // specialType
                    val flags = reader.readU8() // flags

                    Logger.d(TAG) { "Assigning decorations: decorationWallIndex: $decorationWallIndex wallType: $wallType decorationID: $decorationID specialType: $specialType flags: $flags" }

                    if (decorationID != -1) {
                        val matchingDecoration =
                            dec!!.decorations.firstOrNull { it.index == decorationID }
                        check(matchingDecoration != null) {
                            "Decoration ID $decorationID not found in DEC"
                        }
                    }

                    decorations.add(
                        Decoration(
                            decorationWallIndex = decorationWallIndex,
                            wallType = wallType,
                            decorationID = decorationID,
                            specialType = specialType,
                            flags = flags,
                            cps = cps!!,
                            dec = dec!!
                        )
                    )
                } else {
                    check(false) { "Unexpected cmd $cmd" }
                }
            }

            val scriptTimers = readScriptTimers(reader)
            scriptTimers.forEach {
                Logger.d(TAG) { "Got script timer: $it" }
            }

            Logger.d(TAG) { "After script timers Offset is ${reader.offset} remaining ${reader.remaining}" }

            check(reader.offset == nextSubLevelOffset) {
                "Expected to be at sublevel offset $nextSubLevelOffset but is at offset ${reader.offset}"
            }

            // extra padding
            val padding = reader.readU16LE()
            check(padding == 0xFFFF) {
                "Expected padding to be 0xFFFF but is ${padding.toHexString()}"
            }

            subLevels.add(
                SubLevel(
                    level = levelNumber,
                    index = subLevelIndex,
                    maz = maz,
                    vmp = vmp,
                    vcn = vcn,
                    scriptTimers = scriptTimers,
                    monsters = monsters,
                    monsterGfx = monsterGfx,
                    sound = sound,
                    doors = doors,
                    palette = pal,
                    decorations = decorations
                )
            )

            subLevelIndex += 1

            Logger.d(TAG) { "Done reading sublevel offset is ${reader.offset} offsetBlockB $offsetBlockB" }
        }

        // done reading Block A so main level and all sublevels
        check(offsetBlockB == (reader.offset)) {
            "After reading main level and all sublevels expected to be at offset $offsetBlockB but is at offset ${reader.offset}"
        }

        val (offsetBlockC, monsterInstances) = readBlockBHeader(reader)

        val script = readScript(reader)
        script.forEach {
            Logger.d(TAG) { "Got script token: $it" }
        }

        val messages = mutableListOf<String>()
        while (reader.offset < offsetBlockC) {
            val message = reader.readString()
            messages.add(message)
        }
        messages.forEachIndexed { index, message ->
            Logger.d(TAG) { "Got message: $index -> $message" }
        }

        Logger.d(TAG) { "After block B offset is ${reader.offset} remaining ${reader.remaining}" }

        check(reader.offset == offsetBlockC) { "After reading script and messages expected be at offset $offsetBlockC but is at offset ${reader.offset}" }

        val numberOfSpecialBlocks = reader.readU16LE()

        val triggers = List(numberOfSpecialBlocks) {
            val location = Location.read(reader)
            val flag = reader.readU16LE()
            val scriptOffset = ScriptOffset(reader.readU16LE())

            val matchingScript = script.first { it.offset == scriptOffset }

            Logger.d(TAG) { "Got special block for location: $location flag: $flag matchingScript: $matchingScript" }
            Trigger(location = location, flags = TriggerFlags(flag), script = matchingScript)
        }

        Logger.d(TAG) { "After block C offset is ${reader.offset} remaining ${reader.remaining}" }

        check(reader.remaining == 0) {
            "Expected empty reader after all INF parsing"
        }

        script.forEach { step ->
            val token = step.token
            if (token is Message) {
                check(token.messageId.index in messages.indices) {
                    "$name at ${step.offset} prints message ${token.messageId}, " +
                        "but the level has only ${messages.size}"
                }
            }
        }

        items.filter { it.level == levelNumber }.forEachIndexed { index, item ->
            Logger.d(TAG) { "XXX Got item: $index -> $item" }
        }

        return Inf(
            name = name,
            subLevels = subLevels.map { sub ->
                val mine = sub.decorations.mapTo(mutableSetOf()) { it.decorationWallIndex }
                sub.copy(
                    mappedNextDoor = subLevels
                        .filter { it !== sub }
                        .flatMapTo(mutableSetOf()) { other ->
                            other.decorations.map { it.decorationWallIndex }
                        } - mine,
                )
            },
            script = script,
            messages = messages,
            items = items,
            monsterInstances = monsterInstances,
            triggers = triggers
        )
    }

    /** Everything in Block B that comes before the script bytecode. */
    private data class BlockBHeader(
        val offsetBlockC: Int,
        val monsterInstances: List<MonsterInstance>
    )

    // D6 08 EC 00 23 01 19 FF
    private fun readBlockBHeader(reader: ByteReader): BlockBHeader {
        val offsetBlockC = reader.readU16LE()
        Logger.d(TAG) { "Starting Block B at ${reader.offset}. Block C starts at $offsetBlockC" }

        val ec = reader.readU8()
        check(ec == 0xEC || ec == 0xFF) { "Expected 0xEC or 0xFF but got 0x${ec.toHexString()}" }

        val monsterInstances = if (ec == 0xEC) {
            repeat(5) { reader.readU8() }

            readMonsterData(reader)
        } else {
            emptyList()
        }

        return BlockBHeader(offsetBlockC, monsterInstances)
    }

    private fun readScript(reader: ByteReader): List<Script> {
        val tokens = mutableListOf<Script>()
        val scriptStartOffset = reader.offset
        val scriptLength = reader.readU16LE()

        Logger.d(TAG) { "Script size $scriptLength starting at $scriptStartOffset" }

        while (reader.offset < scriptStartOffset + scriptLength) {
            val tokenOffset = reader.offset - scriptStartOffset
            val opcode = reader.readU8()

//            Logger.d(TAG) { "Script opCode ${opcode.toHexString()} at script offset $tokenOffset" }

            val scriptToken = when (opcode) {
                0xFF -> SetWall.read(reader)
                0xFE -> ToggleWall.read(reader)
                0xFD -> OpenDoor.read(reader)
                0xFC -> CloseDoor.read(reader)
                0xFB -> CreateMonster.read(reader)
                0xFA -> Teleport.read(reader)
//                0xF9 -> ScriptToken.StealItem
                0xF8 -> Message.read(reader)
                0xF7 -> SetFlag.read(reader)
                0xF6 -> Sound.read(reader)
                0xF5 -> ClearFlag.read(reader)
//                0xF4 -> ScriptToken.Heal
                0xF3 -> Damage.read(reader)
                0xF2 -> Goto.read(reader)
                0xF1 -> End
                0xF0 -> Return

                0xEF -> GoSub.read(reader)
                0xEE -> Eval.read(reader)
                0xED -> ConsumeItem.read(reader)
                0xEC -> NewLevelOrMonster.read(reader)
//                0xEB -> ScriptToken.GiveXP
                0xEA -> NewItem.read(reader)
                0xE9 -> Launcher.read(reader)
                0xE8 -> Turn.read(reader)
//                0xE7 -> ScriptToken.IdentifyAllItems
                0xE6 -> Encounter.read(reader)
                0xE5 -> Wait.read(reader)
                0xE4 -> UpdateScreen
                0xE3 -> Dialog.read(reader)
                0xE2 -> SpecialEvent.read(reader)

//                0xD3 -> ScriptToken.CutScene

                else -> error("Unsupported script opcode: 0x${opcode.toHexString()}")
            }

            tokens.add(
                Script(
                    offset = ScriptOffset(tokenOffset),
                    token = scriptToken
                )
            )
        }

        return tokens
    }

    /**
     *     unsigned char  index;
     *     unsigned char  levelType;
     *     unsigned short pos;
     *     unsigned char  subpos;
     *     unsigned char  direction;
     *     unsigned char  type;
     *     unsigned char  picture;
     *     unsigned char  phase;
     *     unsigned char  pause;
     *     unsigned short weapon;
     *     unsigned short pocket_item;
     *
     *     x_pos = (pos >> 5) & 0x1F;
     *     y_pos = pos & 0x1F;
     */

    fun readMonsterData(reader: ByteReader): List<MonsterInstance> {
        val monsters = mutableListOf<MonsterInstance>()
        repeat(30) { idx ->
            val monsterIndex = reader.readU8()
            if (monsterIndex != 0xFF) {
                val unit = reader.readU8()
                val block = reader.readU16LE()

                val pos = reader.readU8() // pos
                val dir = reader.readU8() // dir

                val type = reader.readU8()
                val shpIndex = reader.readU8()

                val mode = reader.readU8()
                val pause = reader.readU8()

                val weapon = reader.readU16LE()
                val pocketItem = reader.readU16LE()
                Logger.d(TAG) { "Monster index $idx -> monsterIndex $monsterIndex unit $unit block $block location $pos $dir type $type" }

                monsters.add(
                    MonsterInstance(
                        index = monsterIndex,
                        unit = unit,
                        block = block,
                        pos = pos,
                        direction = Direction.entries[dir and 3],
                        type = MonsterTypeId(type),
                        gfxIndex = shpIndex,
                        mode = mode,
                        pause = pause,
                        weapon = weapon,
                        pocketItem = pocketItem,
                    )
                )
            } else {
                Logger.d(TAG) { "Monster index $idx -> skip" }
                reader.skip(13)
            }
        }
        return monsters
    }

    fun readScriptTimers(reader: ByteReader): List<ScriptTimer> {
        val scriptTimers = mutableListOf<ScriptTimer>()

        while (true) {
            val func = reader.readU16LE()
            if (func == 0xFFFF) {
                break
            }
            val ticks = reader.readU16LE() * 18

            val scriptTimer = ScriptTimer(func, ticks)
            scriptTimers.add(scriptTimer)
        }

        return scriptTimers
    }

    private fun readMonsterGfx(reader: ByteReader): List<MonsterGfx> {
        val monsterGfxList = mutableListOf<MonsterGfx>()

        repeat(2) {
            val cmd = reader.readU8()
            if (cmd == 0xEC || cmd == 0xEA) {
                val sizeClass = reader.readU8()
                val slot = reader.readU8()
                val name = reader.readString(13)
                val dcrFlag = reader.readU8()
                monsterGfxList.add(
                    MonsterGfx(
                        name = name,
                        sizeClass = sizeClass,
                        slot = slot,
                        hasDecorations = dcrFlag != 0,
                    )
                )
            }
        }

        return monsterGfxList
    }

    private suspend fun readDoors(reader: ByteReader): List<Door> {
        val doors = mutableListOf<Door>()

        repeat(2) {
            val cmd = reader.readU8()
            if (cmd == 0xEC || cmd == 0xEA) {
                val doorName = reader.readString(13)

                val idx = reader.readU8()
                val type = reader.readU8() // type
                val knob = reader.readU8() // knob

                // door rectangles
                val rectangles = List(3) {
                    Door.Rectangle(
                        x = reader.readU16LE() shl 3,
                        y = reader.readU16LE(),
                        w = reader.readU16LE() shl 3,
                        h = reader.readU16LE()
                    )
                }

                val buttons = List(2) {
                    Door.Button(
                        x = reader.readU16LE() shl 3,
                        y = reader.readU16LE(),
                        w = reader.readU16LE() shl 3,
                        h = reader.readU16LE(),
                        posX = reader.readU16LE(),
                        posY = reader.readU16LE()
                    )
                }

                doors.add(
                    Door(
                        index = idx,
                        type = type,
                        knob = knob,
                        rectangles = rectangles,
                        buttons = buttons,
                        cps = cpsRepository.loadCps(doorName.uppercase() + ".CPS").getOrThrow()
                    )
                )
            }
        }

        return doors
    }

    private fun readMonsterProperties(reader: ByteReader): List<MonsterProperty> {
        val monsters = mutableListOf<MonsterProperty>()

        var cmd = reader.readU8()
        while (cmd != 0xFF) {
            val armorClass = reader.readI8()
            val hitChance = reader.readI8()
            val level = reader.readI8()
            val hpDcTimes = reader.readU8()
            val hpDcPips = reader.readU8()
            val hpDcBase = reader.readU8()
            val attacksPerRound = reader.readU8()

            val dmgDc = List(3) {
                DamageDice(
                    times = reader.readU8(),
                    pips = reader.readU8(),
                    base = reader.readI8()
                )
            }

            val immunityFlags = reader.readU16LE()
            val capsFlags = reader.readU16LE()
            val typeFlags = reader.readU16LE()
            val experience = reader.readU16LE()

            val u30 = reader.readU8()
            val sound1 = reader.readI8()
            val sound2 = reader.readI8()

            val numRemoteAttacks = reader.readU8()

            var remoteWeaponChangeMode: Int? = null
            var numRemoteWeapons: Int? = null
            val remoteWeapons = mutableListOf<Int>()

            if (reader.readU8() != 0xFF) {
                remoteWeaponChangeMode = reader.readU8()
                numRemoteWeapons = reader.readU8()

                repeat(numRemoteWeapons) {
                    remoteWeapons.add(reader.readI8())
                    reader.skip(1) // skip second byte
                }
            }

            val tuResist = reader.readI8()
            val dmgModifierEvade = reader.readU8()

            val decorations = List(3) { reader.readU8() }
                .filter { it != 0 }
                .map { MonsterDecorationSetId(it) }

            monsters.add(
                MonsterProperty(
                    id = cmd,
                    armorClass = armorClass,
                    hitChance = hitChance,
                    level = level,
                    hpDcTimes = hpDcTimes,
                    hpDcPips = hpDcPips,
                    hpDcBase = hpDcBase,
                    attacksPerRound = attacksPerRound,
                    dmgDc = dmgDc,
                    immunityFlags = immunityFlags,
                    capsFlags = capsFlags,
                    typeFlags = typeFlags,
                    experience = experience,
                    u30 = u30,
                    sound1 = sound1,
                    sound2 = sound2,
                    numRemoteAttacks = numRemoteAttacks,
                    remoteWeaponChangeMode = remoteWeaponChangeMode,
                    numRemoteWeapons = numRemoteWeapons,
                    remoteWeapons = remoteWeapons,
                    tuResist = tuResist,
                    dmgModifierEvade = dmgModifierEvade,
                    decorations = decorations
                )
            )

            cmd = reader.readU8()
        }

        return monsters
    }

    override suspend fun getAllInfNames(): Result<List<String>> {
        return resourceRepository.listResources(".INF")
    }
}
