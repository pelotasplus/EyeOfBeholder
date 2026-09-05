#!/usr/bin/env python3
"""Renders the AdLib banks into the clips the game plays.

The `.ADL` files are scores for a Yamaha OPL2 chip rather than samples, so
every track is rendered ahead of time and the game plays the results. This
produced what is in `composeApp/src/commonMain/composeResources/files/audio`
and is here so that nobody has to guess where those came from.

    brew install adplay ffmpeg
    scripts/render-adlib.py ../EOB2 composeApp/src/commonMain/composeResources/files/audio

Each track is rendered at the chip's own rate and brought down to the shipping
rate by ffmpeg, because who does the downsampling is audible — see [OPL_RATE].
The whole set is then raised by a single gain, and every clip is faded out
over its last few milliseconds.

## What a bank looks like

A `.ADL` opens with a 120 byte table of sound ids, one per track, where 0xFF
means the slot is unused; the programs those ids address follow it. A track is
rendered on its own, which is why 748 numbered tracks come out of ten banks.

Identical renderings are folded together and named by the hash of their
samples: a door is a door on every floor, so 748 tracks are 184 files. What
puts the numbering back is `clips.json`, written beside them — bank, then
track, then which file it turned out to be.

Tracks that render to nothing are left out entirely rather than written as
silence. Some of them are the driver's own control tracks, which the game
plays for their effect on what is already sounding rather than to be heard.
"""

import hashlib
import json
import math
import struct
import subprocess
import sys
from pathlib import Path

# What the chip itself runs at. Rendering at anything else makes adplay
# resample, and an OPL voice is harmonically rich enough that its own
# resampler folds the top of the spectrum back down as hiss: rendered straight
# to 22050 a clip carries about a tenth of its energy again in the top two
# octaves, all of it junk that was never in the sound.
OPL_RATE = 49716

# And what is shipped, which is a quarter of the size and no worse to listen
# to once something with a real filter does the downsampling.
RATE = 22050

# Every track ends on its own, but a bad render would not, and writing runs
# far faster than real time — so a length no tune reaches is still a stop.
MAX_SECONDS = 300

# Below this a sample is not being heard, which is how the end of a clip and a
# track with nothing in it are both recognised. Low, because it also decides
# where a clip is cut: a threshold up at the level a sound is still audibly
# decaying takes the fade off the end and leaves a step where the tail was.
SILENCE = 8

# And the step is silenced properly regardless, by walking the last few
# milliseconds down to nothing. A clip that stops mid-waveform clicks.
FADE_SECONDS = 0.005


def used_tracks(bank: Path) -> list[int]:
    table = bank.read_bytes()[:120]
    return [i for i, sound_id in enumerate(table) if sound_id != 0xFF]


def render(bank: Path, track: int, dest: Path) -> bool:
    cmd = [
        "adplay", "-O", "disk", "-d", str(dest), "-s", str(track),
        "-o", "-q", "-f", str(OPL_RATE), "--mono", "--16bit", str(bank),
    ]
    try:
        subprocess.run(cmd, timeout=25, stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL, check=False)
    except subprocess.TimeoutExpired:
        pass
    return dest.exists() and dest.stat().st_size > 44


def to_shipping_rate(src: Path, dest: Path) -> bool:
    """Down to [RATE] through a filter that does not fold the top back down.

    A wide filter and a cutoff a little under Nyquist, which is what does the
    work: at the defaults enough gets through to be folded back, and this is
    the whole reason the rendering goes the long way round.
    """
    cmd = [
        "ffmpeg", "-y", "-loglevel", "error", "-i", str(src),
        "-af", "aresample=resampler=swr:filter_size=256:cutoff=0.91",
        "-ar", str(RATE), "-ac", "1", "-c:a", "pcm_s16le", str(dest),
    ]
    subprocess.run(cmd, stdout=subprocess.DEVNULL,
                   stderr=subprocess.DEVNULL, check=False)
    return dest.exists() and dest.stat().st_size > 44


def samples_of(wav: Path) -> bytes:
    """The data chunk of a canonical 16 bit mono WAV, capped and trimmed."""
    raw = wav.read_bytes()
    at = 12
    while at + 8 <= len(raw):
        chunk_id = raw[at:at + 4]
        size = struct.unpack_from("<I", raw, at + 4)[0]
        body = raw[at + 8:at + 8 + size]
        if chunk_id == b"data":
            return trim_trailing_silence(body[:MAX_SECONDS * RATE * 2])
        at += 8 + size + (size & 1)
    return b""


def trim_trailing_silence(data: bytes) -> bytes:
    count = len(data) // 2
    values = list(struct.unpack_from(f"<{count}h", data))
    last = count
    while last > 0 and abs(values[last - 1]) < SILENCE:
        last -= 1
    if last == 0:
        return b""

    values = values[:last]
    fade = min(int(FADE_SECONDS * RATE), last)
    for i in range(fade):
        values[last - fade + i] = int(values[last - fade + i] * (1 - (i + 1) / fade))

    return struct.pack(f"<{last}h", *values)


def loudest_fits(clips) -> float:
    """How much everything can be raised before the loudest clip clips."""
    peak = 0
    for data in clips:
        count = len(data) // 2
        values = struct.unpack_from(f"<{count}h", data)
        peak = max(peak, max(values, default=0), -min(values, default=0))
    return 1.0 if peak == 0 else 32767 / peak


def amplified(data: bytes, gain: float) -> bytes:
    count = len(data) // 2
    values = struct.unpack_from(f"<{count}h", data)
    raised = [max(-32768, min(32767, int(v * gain))) for v in values]
    return struct.pack(f"<{count}h", *raised)


def write_wav(path: Path, data: bytes) -> None:
    header = b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVE"
    header += b"fmt " + struct.pack("<IHHIIHH", 16, 1, 1, RATE, RATE * 2, 2, 16)
    header += b"data" + struct.pack("<I", len(data))
    path.write_bytes(header + data)


def main() -> None:
    if len(sys.argv) != 3:
        sys.exit(__doc__)

    banks_in, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    scratch = out / "_render.wav"
    downsampled = out / "_shipping.wav"

    clips: dict[str, bytes] = {}
    table: dict[str, dict[str, str]] = {}
    silent = capped = 0

    for bank in sorted(banks_in.glob("*.ADL")):
        table[bank.name] = {}
        tracks = used_tracks(bank)
        print(f"{bank.name}: {len(tracks)} tracks", flush=True)

        for track in tracks:
            scratch.unlink(missing_ok=True)
            downsampled.unlink(missing_ok=True)
            if not render(bank, track, scratch):
                continue
            if not to_shipping_rate(scratch, downsampled):
                continue

            data = samples_of(downsampled)
            if not data:
                silent += 1
                continue
            if len(data) >= MAX_SECONDS * RATE * 2:
                capped += 1
                print(f"  capped: {bank.name} track {track}", flush=True)

            digest = hashlib.sha256(data).hexdigest()[:12]
            clips.setdefault(digest, data)
            table[bank.name][str(track)] = f"snd_{digest}.wav"

    scratch.unlink(missing_ok=True)
    downsampled.unlink(missing_ok=True)

    # One gain for the whole dungeon rather than one per clip. What the bank
    # says about a bite being louder than a footstep is worth keeping, so this
    # only takes up the headroom nothing was using.
    gain = loudest_fits(clips.values())
    written = set()
    for digest, data in clips.items():
        name = f"snd_{digest}.wav"
        write_wav(out / name, amplified(data, gain))
        written.add(name)

    # A clip is named after what is in it, so a rendering that sounds different
    # is a different file rather than the same one written over. Left alone the
    # directory would keep every previous rendering beside this one, and all of
    # them would be deployed.
    stale = [f for f in out.glob("snd_*.wav") if f.name not in written]
    for f in stale:
        f.unlink()

    (out / "clips.json").write_text(json.dumps(table, indent=1, sort_keys=True))

    total = sum(f.stat().st_size for f in out.glob("*.wav"))
    pairs = sum(len(v) for v in table.values())
    print(f"\n{pairs} (bank, track) pairs -> {len(clips)} distinct clips")
    print(f"{silent} rendered to nothing, {capped} hit the {MAX_SECONDS}s cap")
    print(f"everything raised by {20 * math.log10(gain):.1f} dB")
    print(f"{len(stale)} clips from an earlier rendering removed")
    print(f"{total / 1_048_576:.1f} MB")


if __name__ == "__main__":
    main()
