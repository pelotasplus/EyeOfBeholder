package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.LCWHelper
import pl.pelotasplus.eyeofbeholder.data.model.DamageDice
import pl.pelotasplus.eyeofbeholder.data.model.Door
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.MonsterGfx
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.ScriptTimer

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

        var hunkSize = reader.readU16LE()
        Logger.d(TAG) { "XXX first hunkSize $hunkSize" }

//        while (offset < hunkSize) {
        hunkSize = reader.readU16LE()
        Logger.d(TAG) { "XXX next hunkSize $hunkSize" }

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
                Logger.d(TAG) { "Decoration: $gfx $dec" }
            } else if (cmd == 0xFB) {
                // assign decorations
                Logger.d(TAG) { "Assigning decorations..." }
                val wallIndex = reader.readU8()
                val vmpIndex = reader.readU8()
                val decIndex = reader.readU8()
                val specialType = reader.readU8()
                val flags = reader.readU8()
                Logger.d(TAG) { "Assigning decorations: wallIndex: $wallIndex vmpIndex: $vmpIndex decIndex: $decIndex specialType: $specialType flags: $flags" }
            } else {
                check(false) { "Unexpected cmd $cmd" }
            }
        }

        Logger.d(TAG) { "Offset is ${reader.offset} remaining ${reader.remaining}" }

        val scriptTimers = readScriptTimers(reader)
        scriptTimers.forEach {
            Logger.d(TAG) { "Got script timer: $it" }
        }

        Logger.d(TAG) { "Offset is ${reader.offset} remaining ${reader.remaining}" }
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
