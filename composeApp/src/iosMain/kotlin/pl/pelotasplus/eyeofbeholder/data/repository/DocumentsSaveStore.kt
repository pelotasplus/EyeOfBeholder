package pl.pelotasplus.eyeofbeholder.data.repository

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * Saves as files in the app's Documents directory, which iOS backs up.
 *
 * Untested on a device — nothing here runs an iOS build yet — so treat a
 * failure as this file's fault before anything above it.
 */
@OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)
class DocumentsSaveStore : SaveStore {

    override suspend fun written(): Set<SaveSlot> =
        SaveSlot.all.filterTo(mutableSetOf()) {
            NSFileManager.defaultManager.fileExistsAtPath(pathFor(it))
        }

    override suspend fun read(slot: SaveSlot): String? = NSString.stringWithContentsOfFile(
        path = pathFor(slot),
        encoding = NSUTF8StringEncoding,
        error = null,
    )

    override suspend fun write(slot: SaveSlot, contents: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = directory(),
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        NSString.create(string = contents).writeToFile(
            path = pathFor(slot),
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
    }

    override suspend fun erase(slot: SaveSlot) {
        NSFileManager.defaultManager.removeItemAtPath(pathFor(slot), null)
    }

    private fun pathFor(slot: SaveSlot) = "${directory()}/${slot.name}.json"

    private fun directory(): String {
        val documents = NSSearchPathForDirectoriesInDomains(
            directory = NSDocumentDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).first() as String

        return "$documents/saves"
    }
}
