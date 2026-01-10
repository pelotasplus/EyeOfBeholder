#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlin:kotlin-stdlib:2.0.0")

import java.io.File

fun readU16LE(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xFF) +
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}

fun readU32LE(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xFF) +
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) +
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) +
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}

fun decompress(source: ByteArray, dest: ByteArray) {
    var sp = 0 // Source Pointer
    var dp = 0 // Destination Pointer

    val rel = source[sp].toInt() and 0xFF
    if (rel == 0) {
        sp++
    }

    while (true) {
        val com = source[sp].toInt() and 0xFF
        sp++
        val b7 = com shr 7

        if (b7 == 0) {
            val count = ((com and 0x7F) shr 4) + 3
            var posit = ((com and 0x0F) shl 8) + (source[sp].toInt() and 0xFF)
            sp++
            posit = dp - posit

            for (i in 0 until count) {
                dest[dp] = dest[posit + i]
                dp++
            }
        } else {
            val b6 = (com and 0x40) shr 6

            if (b6 == 0) {
                val count = com and 0x3F
                if (count == 0) {
                    break
                }

                for (i in 0 until count) {
                    dest[dp] = source[sp]
                    dp++
                    sp++
                }
            } else {
                var count = com and 0x3F

                if (count < 0x3E) {
                    count += 3
                    val posit = if (rel == 0) {
                        dp - readU16LE(source, sp)
                    } else {
                        readU16LE(source, sp)
                    }
                    sp += 2

                    for (i in 0 until count) {
                        dest[dp] = dest[posit + i]
                        dp++
                    }
                } else if (count == 0x3F) {
                    count = readU16LE(source, sp)
                    val posit = if (rel == 0) {
                        dp - readU16LE(source, sp + 2)
                    } else {
                        readU16LE(source, sp + 2)
                    }
                    sp += 4

                    for (i in 0 until count) {
                        dest[dp] = dest[posit + i]
                        dp++
                    }
                } else {
                    count = readU16LE(source, sp)
                    sp += 2
                    val b = source[sp]
                    sp++

                    for (i in 0 until count) {
                        dest[dp] = b
                        dp++
                    }
                }
            }
        }
    }
}

// Main script
if (args.size != 2) {
    println("Usage: decompress.main.kts <input_file> <output_file>")
    kotlin.system.exitProcess(1)
}

val inputPath = args[0]
val outputPath = args[1]

val inputFile = File(inputPath)
if (!inputFile.exists()) {
    println("Error: Input file '$inputPath' does not exist")
    kotlin.system.exitProcess(1)
}

val bytes = inputFile.readBytes()
var offset = 0

println("Reading $inputPath; file size ${bytes.size}")

val sizeFromHeader = readU16LE(bytes, offset)
offset += 2
println("Header file size: $sizeFromHeader")

val compressionType = readU16LE(bytes, offset)
offset += 2
println("Compression type: $compressionType")

val uncompressedSize = readU32LE(bytes, offset)
offset += 4
println("Uncompressed size: $uncompressedSize")

val paletteSize = readU16LE(bytes, offset)
offset += 2
println("Palette size: $paletteSize")

val compressed = bytes.copyOfRange(offset, bytes.size)
val decompressed = ByteArray(uncompressedSize)

println("Decompressing ${compressed.size} bytes...")
decompress(compressed, decompressed)

File(outputPath).writeBytes(decompressed)
println("Written $uncompressedSize bytes to $outputPath")
