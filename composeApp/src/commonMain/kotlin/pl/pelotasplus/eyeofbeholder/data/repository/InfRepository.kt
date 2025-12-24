package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.LCWHelper
import pl.pelotasplus.eyeofbeholder.data.model.DamageDice
import pl.pelotasplus.eyeofbeholder.data.model.Door
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.MonsterGfx
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.ScriptTimer
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptToken

interface InfRepository {
    suspend fun loadInf(name: String): Result<Inf>

    suspend fun getAllInfNames(): Result<List<String>>
}

class InfRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : InfRepository {

    private val TAG = "InfRepository"

    override suspend fun loadInf(name: String): Result<Inf> {
        return runCatching {
            val bytes = resourceRepository.readResource("files/$name")
            val reader = ByteReader(bytes)

            Logger.d(TAG) { "Decompressing $name; On-disk file size ${bytes.size}" }

            val sizeFromHeader = reader.readU16LE()
            Logger.d(TAG) { "Header file size $sizeFromHeader" }

            val compressionType = reader.readU16LE()
            Logger.d(TAG) { "Compression Type $compressionType" }

            val uncompressedSize = reader.readU32LE()
            Logger.d(TAG) { "Uncompressed size $uncompressedSize" }

            val paletteSize = reader.readU16LE()
            check(paletteSize == 0) {
                "Unexpected palette size: $paletteSize, expected 0"
            }
            Logger.d(TAG) { "Palette Size $paletteSize" }

            val compressed = reader.readRemaining()
            val decompressed = UByteArray(uncompressedSize)

            LCWHelper.decompress(compressed, decompressed)

            decodeInf(decompressed)

            Inf(
                name = name,
                data = decompressed
            )
        }
    }

    private fun decodeInf(bytes: UByteArray) {
        val reader = ByteReader(bytes)

        val hunkSize = reader.readU16LE()
        Logger.d(TAG) { "XXX first hunkSize $hunkSize" }

        val nextHunkSize = reader.readU16LE()
        Logger.d(TAG) { "XXX next hunkSize $nextHunkSize" }

        var cmd = reader.readU8()
        check(cmd == 0xEC) { "expected 0xEC, got $cmd" }

        val mazName = reader.readString(13)
        Logger.d(TAG) { "Maz name '$mazName'" }

        val vmpData = reader.readString(13)
        Logger.d(TAG) { "VMP name '$vmpData'" }

        cmd = reader.readU8()
        check(cmd == 0xFF) { "expected 0xFF, got 0x${cmd.toHexString()}" }

        val palette = reader.readString(13)
        Logger.d(TAG) { "Palette name '$palette'" }

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

        Logger.d(TAG) { "Offset is ${reader.offset} remaining ${reader.remaining}" }

        cmd = reader.readU8()
        check(cmd == 0xEC) { "expected 0xFF, got 0x${cmd.toHexString()}" }

        val decorationBlocks = reader.readU16LE()
        Logger.d(TAG) { "Decorations block count $decorationBlocks" }
        for (i in 0 until decorationBlocks) {
            cmd = reader.readU8()
            if (cmd == 0xEC) {
                // read decorations
                val gfx = reader.readString(13)
                val dec = reader.readString(13)
                Logger.d(TAG) { "Decoration: gfc: $gfx dec: $dec" }
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
                val wallIndex = reader.readU8()
                val wallType = reader.readU8()
                val decorationID = reader.readU8()
                val specialType = reader.readU8()
                val flags = reader.readU8()
                Logger.d(TAG) { "Assigning decorations: wallIndex: $wallIndex vmpIndex: $wallType decIndex: $decorationID specialType: $specialType flags: $flags" }
            } else {
                check(false) { "Unexpected cmd $cmd" }
            }
        }

        val scriptTimers = readScriptTimers(reader)
        scriptTimers.forEach {
            Logger.d(TAG) { "Got script timer: $it" }
        }

        check(nextHunkSize == reader.offset) {
            "After reading timers expected to be at offset $nextHunkSize but is at offset ${reader.offset}"
        }

        // timer?
        reader.readU8()
        reader.readU8()
        reader.readU8()
        reader.readU8()
        reader.readU8()
        reader.readU8()
        reader.readU8()
        reader.readU8()
        reader.readU8()
        reader.readU8()

        Logger.d(TAG) { "Offset is ${reader.offset} remaining ${reader.remaining}" }

        readMonsterData(reader)

        Logger.d(TAG) { "Offset is ${reader.offset} remaining ${reader.remaining}" }

        val script = readScript(reader)
        script.tokens.forEach {
            Logger.d(TAG) { "Got script token: $it" }
        }
    }

    private fun readScript(reader: ByteReader): Script {
        val tokens = mutableListOf<ScriptToken>()
        val startOffset = reader.offset
        val length = reader.readU16LE()

        Logger.d(TAG) { "Script size $length starting at $startOffset" }

        while (reader.offset < startOffset + length) {
            val tokenOffset = reader.offset - startOffset
            val opcode = reader.readU8()

            Logger.d(TAG) { "Script opCode ${opcode.toHexString()} at offset $tokenOffset" }

            val scriptToken = when (opcode) {
                0xEE -> Eval.read(reader)
                else -> error("Unsupported script opcode: 0x${opcode.toHexString()}")
            }

            tokens.add(scriptToken)

//            val token: ScriptToken? = when (opcode) {
//                0xFF -> ScriptToken.SetWall
//                0xFE -> ScriptToken.ChangeWall
//                0xFD -> ScriptToken.OpenDoor
//                0xFC -> ScriptToken.CloseDoor
//                0xFB -> ScriptToken.CreateMonster
//                0xFA -> ScriptToken.Teleport
//                0xF9 -> ScriptToken.StealItem
//                0xF8 -> ScriptToken.Message
//                0xF7 -> ScriptToken.SetFlag
//                0xF6 -> ScriptToken.Sound
//                0xF5 -> ScriptToken.ClearFlag
//                0xF4 -> ScriptToken.Heal
//                0xF3 -> ScriptToken.Damage
//                0xF2 -> ScriptToken.Goto(reader.readU16LE())
//                0xF1 -> ScriptToken.End
//                0xF0 -> ScriptToken.Return
//                0xEF -> ScriptToken.GoSub(reader.readU16LE())
//                0xEE -> ScriptToken.Eval(readConditional(reader))
//                0xED -> ScriptToken.ConsumeItem
//                0xEC -> ScriptToken.ChangeLevel
//                0xEB -> ScriptToken.GiveXP
//                0xEA -> ScriptToken.NewItem
//                0xE9 -> ScriptToken.Launcher
//                0xE8 -> ScriptToken.Turn
//                0xE7 -> ScriptToken.IdentifyAllItems
//                0xE6 -> ScriptToken.Encounter
//                0xE5 -> ScriptToken.Wait
//                0xE4 -> ScriptToken.UpdateScreen
//                0xE3 -> ScriptToken.Dialog
//                0xE2 -> ScriptToken.SpecialEvent
//                0xD3 -> ScriptToken.CutScene
//                else -> {
//                    Logger.w(TAG) { "Unknown script opcode: 0x${opcode.toHexString()}" }
//                    ScriptToken.Unknown(opcode)
//                }
//            }
//
//            token?.let { tokens[tokenOffset] = it }
        }

        return Script(tokens = tokens)
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

    fun readMonsterData(reader: ByteReader) {
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
                val i = reader.readU8()

                val weapon = reader.readU16LE()
                val pocketItem = reader.readU16LE()
                Logger.d(TAG) { "Monster index $idx -> monsterIndex $monsterIndex unit $unit block $block location $pos $dir type $type" }
            } else {
                Logger.d(TAG) { "Monster index $idx -> skip" }
                reader.skip(13)
            }
        }
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
                reader.readU8() // unknown
                reader.readU8() // unknown
                val name = reader.readString(13)
                reader.readU8() // unknown
                monsterGfxList.add(MonsterGfx(name = name))
            }
        }

        return monsterGfxList
    }

    private fun readDoors(reader: ByteReader): List<Door> {
        val doors = mutableListOf<Door>()

        repeat(2) {
            val cmd = reader.readU8()
            if (cmd == 0xEC || cmd == 0xEA) {
                val doorName = reader.readString(13)
                val idx = reader.readU8()

                reader.readU8() // type
                reader.readU8() // knob

                // door rectangles
                repeat(2) {
                    reader.readU16LE()
                    reader.readU16LE()
                    reader.readU16LE()
                    reader.readU16LE()
                }

                // button rectangles
                repeat(2) {
                    reader.readU16LE()
                    reader.readU16LE()
                    reader.readU16LE()
                    reader.readU16LE()
                }

                // button positions
                repeat(2) {
                    reader.readU16LE()
                    reader.readU16LE()
                    reader.readU16LE()
                    reader.readU16LE()
                }

                doors.add(Door(file = doorName, index = idx))
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
