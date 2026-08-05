#!/usr/bin/env python3
"""Renders the AdLib banks into the clips the game plays.

The `.ADL` files are scores for a Yamaha OPL2 chip rather than samples, so
every track is rendered ahead of time and the game plays the results. This
produced what is in `composeApp/src/commonMain/composeResources/files/audio`
and is here so that nobody has to guess where those came from.

    brew install adplay
    scripts/render-adlib.py ../EOB2 composeApp/src/commonMain/composeResources/files/audio

## What a bank looks like

A `.ADL` opens with a 120 byte table of sound ids, one per track, where 0xFF
means the slot is unused; the programs those ids address follow it. A track is
rendered on its own, which is why 745 numbered tracks come out of ten banks.

Identical renderings are folded together and named by the hash of their
samples: a door is a door on every floor, so 745 tracks are 181 files. What
puts the numbering back is `clips.json`, written beside them — bank, then
track, then which file it turned out to be.

Tracks that render to nothing are left out entirely rather than written as
silence. Some of them are the driver's own control tracks, which the game
plays for their effect on what is already sounding rather than to be heard.
"""

import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path

RATE = 22050

# Every track ends on its own, but a bad render would not, and writing runs
# far faster than real time — so a length no tune reaches is still a stop.
MAX_SECONDS = 300

# Below this a sample is not being heard, which is how the end of a clip and a
# track with nothing in it are both recognised.
SILENCE = 64


def used_tracks(bank: Path) -> list[int]:
    table = bank.read_bytes()[:120]
    return [i for i, sound_id in enumerate(table) if sound_id != 0xFF]


def render(bank: Path, track: int, dest: Path) -> bool:
    cmd = [
        "adplay", "-O", "disk", "-d", str(dest), "-s", str(track),
        "-o", "-q", "-f", str(RATE), "--mono", "--16bit", str(bank),
    ]
    try:
        subprocess.run(cmd, timeout=25, stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL, check=False)
    except subprocess.TimeoutExpired:
        pass
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
    values = struct.unpack_from(f"<{count}h", data)
    last = count
    while last > 0 and abs(values[last - 1]) < SILENCE:
        last -= 1
    return data[:last * 2]


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

    clips: dict[str, str] = {}
    table: dict[str, dict[str, str]] = {}
    silent = capped = 0

    for bank in sorted(banks_in.glob("*.ADL")):
        table[bank.name] = {}
        tracks = used_tracks(bank)
        print(f"{bank.name}: {len(tracks)} tracks", flush=True)

        for track in tracks:
            scratch.unlink(missing_ok=True)
            if not render(bank, track, scratch):
                continue

            data = samples_of(scratch)
            if not data:
                silent += 1
                continue
            if len(data) >= MAX_SECONDS * RATE * 2:
                capped += 1
                print(f"  capped: {bank.name} track {track}", flush=True)

            digest = hashlib.sha256(data).hexdigest()[:12]
            name = clips.get(digest)
            if name is None:
                name = f"snd_{digest}.wav"
                clips[digest] = name
                write_wav(out / name, data)
            table[bank.name][str(track)] = name

    scratch.unlink(missing_ok=True)
    (out / "clips.json").write_text(json.dumps(table, indent=1, sort_keys=True))

    total = sum(f.stat().st_size for f in out.glob("*.wav"))
    pairs = sum(len(v) for v in table.values())
    print(f"\n{pairs} (bank, track) pairs -> {len(clips)} distinct clips")
    print(f"{silent} rendered to nothing, {capped} hit the {MAX_SECONDS}s cap")
    print(f"{total / 1_048_576:.1f} MB")


if __name__ == "__main__":
    main()
