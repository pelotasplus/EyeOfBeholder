package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import eyeofbeholder.composeapp.generated.resources.Res
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.LCWHelper
import pl.pelotasplus.eyeofbeholder.data.RleHelper
import pl.pelotasplus.eyeofbeholder.data.model.Palette

/**
 * Foundation layer for reading raw game asset files.
 *
 * All game data files are bundled as Compose Multiplatform resources and
 * accessed via `Res.readBytes()`. This repository handles:
 * - Raw file I/O ([readResource])
 * - LCW decompression ([decompressResource]) for .CPS, .VCN, .INF files
 * - Asset discovery via [listResources] from the 250+ file manifest
 *
 * ## Compressed file header format
 * Files like .CPS and .VCN have a standard header before the LCW data:
 * - sizeFromHeader (u16) — reported file size
 * - compressionType (u16) — compression method identifier
 * - uncompressedSize (u32) — size of decompressed output buffer
 * - paletteSize (u16) — embedded palette size: 0, or 768 when the file carries
 *   its own 256-color VGA palette between the header and the payload
 * - Remaining bytes: LCW-compressed payload
 */
interface ResourceRepository {
    suspend fun readResource(path: String): UByteArray

    suspend fun decompressResource(path: String): DecompressedResource

    suspend fun listResources(extension: String): Result<List<String>>
}

/**
 * @property bytes the LCW-decompressed payload
 * @property palette the file's own palette, or null when it relies on an
 *   external .PAL
 */
data class DecompressedResource(
    val bytes: UByteArray,
    val palette: Palette?,
)

class ResourceRepositoryImpl() : ResourceRepository {
    private val TAG = "ResourceRepository"
    private val manifest = listOf(
        "AIRSEAL.CPS",
        "ALTAR.CPS",
        "ANT.CPS",
        "ASERVANT.CPS",
        "AZURE.ADL",
        "AZURE.DEC",
        "AZURE.EGA",
        "AZURE.PAL",
        "AZURE.SND",
        "AZURE1.CPS",
        "AZURE2.CPS",
        "BADMOOD.CPS",
        "BASILISK.CPS",
        "BEHOLDER.CPS",
        "BEHOLDER.DCR",
        "BLOOD.CPS",
        "BORDER.CPS",
        "BROWN.DEC",
        "BROWN1.CPS",
        "BROWN2.CPS",
        "BROWN3.CPS",
        "BULETTE.CPS",
        "CATACOMB.ADL",
        "CATACOMB.SND",
        "CHARGEN.CPS",
        "CHARGENA.CPS",
        "CHARGENB.CPS",
        "CHOICE.CPS",
        "CLERIC1.CPS",
        "CLERIC1.DCR",
        "CLERIC2.CPS",
        "CLERIC2.DCR",
        "CLERIC3.CPS",
        "CLERIC3.DCR",
        "COIN.CPS",
        "CREDITS.CPS",
        "CREDITS.TXT",
        "CREDITS2.CPS",
        "CREDITS3.CPS",
        "CRIMRING.CPS",
        "CRIMSON.CPS",
        "CRIMSON.DEC",
        "CRIMSON.EGA",
        "CRIMSON.PAL",
        "CRIMSON.VCN",
        "CRIMSON.VMP",
        "CRIMSON1.ADL",
        "CRIMSON1.SND",
        "CRIMSON2.ADL",
        "CRIMSON2.CPS",
        "CRIMSON2.SND",
        "CRYSTAL.CPS",
        "CUBE.CPS",
        "DARKMOON.CPS",
        "DECORATE.CPS",
        "DESTROY0.CPS",
        "DESTROY1.CPS",
        "DESTROY2.CPS",
        "DESTROY3.CPS",
        "DOOR1.CPS",
        "DOOR2.CPS",
        "DOORWAY1.CPS",
        "DOORWAY2.CPS",
        "DRAGON.CPS",
        "DRAGON.DCR",
        "DRAGON1.CPS",
        "DRAGON2.CPS",
        "DRAN.CPS",
        "DRANAZ3.CPS",
        "DRANSL2.CPS",
        "DRANX.CPS",
        "DREAM.CPS",
        "DUNG.EGA",
        "DUNG.PAL",
        "DUNG.VCN",
        "DUNG.VMP",
        "EOBDATA0.SAV",
        "EOSPREFS.DAT",
        "FADING.DAT",
        "FINALE1.ADL",
        "FINALE1.SND",
        "FINALE2.ADL",
        "FINALE2.SND",
        "FINALE_0.PAL",
        "FINALE_1.PAL",
        "FINALE_2.PAL",
        "FINALE_3.PAL",
        "FINALE_4.PAL",
        "FINALE_5.PAL",
        "FINALE_6.PAL",
        "FINALE_7.PAL",
        "FONT6.FNT",
        "FONT8.FNT",
        "FOREST.ADL",
        "FOREST.CPS",
        "FOREST.DEC",
        "FOREST.EGA",
        "FOREST.PAL",
        "FOREST.SND",
        "FOREST.VCN",
        "FOREST.VMP",
        "FRGIANT.CPS",
        "FSNAKE.CPS",
        "GARGOYLE.CPS",
        "GASSPORE.CPS",
        "GLASS.CPS",
        "GUARD1.CPS",
        "GUARD1.DCR",
        "GUARD2.CPS",
        "GUARD2.DCR",
        "GUARDIAN.CPS",
        "HEAD.CPS",
        "HELLHND.CPS",
        "HEROES.CPS",
        "HURRY1.CPS",
        "HURRY2.CPS",
        "IMPLODE.CPS",
        "INTRO.ADL",
        "INTRO.CPS",
        "INTRO.EGA",
        "INTRO.SND",
        "INVENT.CPS",
        "ITEM.DAT",
        "ITEMICN.CPS",
        "ITEML1.CPS",
        "ITEMS1.CPS",
        "ITEMTYPE.DAT",
        "KHELBAN1.CPS",
        "KHELBAN2.CPS",
        "KHELBAN3.CPS",
        "KHELBAN4.CPS",
        "KHELBAN5.CPS",
        "KHELBAN6.CPS",
        "KHELBEN.CPS",
        "KHELDRAN.CPS",
        "LEVEL1.INF",
        "LEVEL1.MAZ",
        "LEVEL10.INF",
        "LEVEL10.MAZ",
        "LEVEL11.INF",
        "LEVEL11.MAZ",
        "LEVEL12.INF",
        "LEVEL12.MAZ",
        "LEVEL13.INF",
        "LEVEL13.MAZ",
        "LEVEL14.INF",
        "LEVEL14.MAZ",
        "LEVEL15.INF",
        "LEVEL15.MAZ",
        "LEVEL16.INF",
        "LEVEL2.INF",
        "LEVEL2.MAZ",
        "LEVEL3.INF",
        "LEVEL3.MAZ",
        "LEVEL4.INF",
        "LEVEL4.MAZ",
        "LEVEL5.INF",
        "LEVEL5.MAZ",
        "LEVEL6.INF",
        "LEVEL6.MAZ",
        "LEVEL7.INF",
        "LEVEL7.MAZ",
        "LEVEL8.INF",
        "LEVEL8.MAZ",
        "LEVEL9.INF",
        "LEVEL9.MAZ",
        "LEVELS.TMP",
        "MAGE.CPS",
        "MAGE.DCR",
        "MAGIC.CPS",
        "MANTIS.CPS",
        "MANTIS.DCR",
        "MAP.CPS",
        "MEDUSA.CPS",
        "MENU.CPS",
        "MENU.EGA",
        "MEZANINE.ADL",
        "MEZANINE.SND",
        "MEZZ.DAT",
        "MEZZ.DEC",
        "MEZZ.EGA",
        "MEZZ.PAL",
        "MEZZ.VCN",
        "MEZZ.VMP",
        "MEZZ1.CPS",
        "MEZZ2.CPS",
        "MGA.OVL",
        "MINDFLAY.CPS",
        "MMOUTH1.CPS",
        "MMOUTH2.CPS",
        "OUTPORTS.CPS",
        "OUTTAKE.CPS",
        "PALETTE.COL",
        "PALETTE0.PAL",
        "PALETTE1.PAL",
        "PALETTE2.PAL",
        "PALETTE3.PAL",
        "PALETTE4.PAL",
        "PLAYFLD.CPS",
        "PORTALA.CPS",
        "PORTALB.CPS",
        "SALAMNDR.CPS",
        "SETUP.EXE",
        "SHRUNKEN.CPS",
        "SILVER.ADL",
        "SILVER.DEC",
        "SILVER.EGA",
        "SILVER.PAL",
        "SILVER.SND",
        "SILVER.VCN",
        "SILVER.VMP",
        "SILVER1.CPS",
        "SILVER2.CPS",
        "SKELWAR.CPS",
        "SOUT1.CPS",
        "SOUT2.CPS",
        "SOUT3.CPS",
        "SOUT4.CPS",
        "SOUT5.CPS",
        "SPIDER.CPS",
        "START.EXE",
        "STONEGIA.CPS",
        "STREET1.CPS",
        "STREET2.CPS",
        "SUICIDE.CPS",
        "TANGLOR.CPS",
        "TEXT.DAT",
        "THANKS.CPS",
        "THROWN.CPS",
        "WASP.CPS",
        "WESTWOOD.CPS",
        "WILLOWIS.CPS",
        "WINDING.CPS",
        "WOLF.CPS",
        "XGA.OVL",
    )

    override suspend fun readResource(path: String): UByteArray {
        return Res.readBytes(path).asUByteArray()
    }

    override suspend fun decompressResource(path: String): DecompressedResource {
        val bytes = readResource(path)

        // COIN.CPS and KHELBAN2.CPS ship as 0-byte files in EoB2
        check(bytes.size >= HEADER_SIZE) {
            "$path is ${bytes.size} bytes, too short to hold a $HEADER_SIZE byte header"
        }

        val reader = ByteReader(bytes)

        Logger.d(TAG) { "Decompressing $path; On-disk file size ${bytes.size}" }

        val sizeFromHeader = reader.readU16LE()
//        Logger.d(TAG) { "Header file size $sizeFromHeader" }

        val compressionType = reader.readU16LE()
//        Logger.d(TAG) { "Compression Type $compressionType" }
        // 0 = uncompressed, 1 = LZW, 3 = run-length, 4 = LCW. LZW is the one
        // nothing in the game data uses.
        check(compressionType in DECOMPRESSED_BY) {
            "$path uses compression type $compressionType, which nothing here decompresses"
        }

        val uncompressedSize = reader.readU32LE()
//        Logger.d(TAG) { "Uncompressed size $uncompressedSize" }

        // A CPS may carry its own VGA palette between the header and the LCW
        // data, in which case it — not the sublevel .PAL — is the right palette
        // to draw the image with. HEROES.CPS is one such file.
        val paletteSize = reader.readU16LE()
        check(paletteSize == 0 || paletteSize == Palette.BYTE_SIZE) {
            "Unexpected palette size: $paletteSize, expected 0 or ${Palette.BYTE_SIZE}"
        }
//        Logger.d(TAG) { "Palette Size $paletteSize" }

        val palette = if (paletteSize == 0) {
            null
        } else {
            Palette.fromVgaBytes(name = path, bytes = reader.readBytes(paletteSize))
        }

        val compressed = reader.readRemaining()
        val decompressed = UByteArray(uncompressedSize)

        when (compressionType) {
            COMPRESSION_NONE -> compressed.copyInto(
                destination = decompressed,
                endIndex = minOf(compressed.size, decompressed.size),
            )

            COMPRESSION_RLE -> RleHelper.decompress(compressed, decompressed)
            else -> LCWHelper.decompress(compressed, decompressed)
        }

        return DecompressedResource(bytes = decompressed, palette = palette)
    }

    override suspend fun listResources(extension: String): Result<List<String>> {
        return runCatching {
            manifest.filter { it.endsWith(extension, ignoreCase = true) }
                .also { Logger.d(TAG) { "listResources: $extension got $it" } }
        }
    }

    companion object {
        private const val HEADER_SIZE = 10

        private const val COMPRESSION_NONE = 0
        private const val COMPRESSION_RLE = 3
        private const val COMPRESSION_LCW = 4

        private val DECOMPRESSED_BY =
            setOf(COMPRESSION_NONE, COMPRESSION_RLE, COMPRESSION_LCW)
    }
}
