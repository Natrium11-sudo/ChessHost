# Third-party components and licences

ChessHost as a whole is released under the **GNU General Public License v3.0**
(see [`../LICENSE`](../LICENSE)). This file lists every third-party component
that ends up in the app, what licence it uses and where its source can be
found.

If you only want one thing from this document: **the four chess engines shipped
in `app/src/main/jniLibs/` are GPLv3 software. Their complete source is
available — the exact upstream revisions are pinned below and every local
change is a patch in [`../patches/`](../patches).**

---

## 1. Chess engines bundled in the APK

All four engines are compiled for Android (arm64-v8a and x86_64) and shipped as
shared libraries under `app/src/main/jniLibs/`. The app starts each one as a
**separate process** and talks to it over the UCI protocol through pipes. They
are not linked into the app's own code.

| Engine | Version | Licence | Revision | Built by |
| --- | --- | --- | --- | --- |
| Stockfish | 17.1 | GPL-3.0 | tag `sf_17.1` | `tools/build-stockfish.sh` |
| Fairy-Stockfish | classic eval | GPL-3.0 | commit `5283883` | `tools/build-engines.sh` |
| Ethereal | 14 | GPL-3.0 | commit `0e47e9b` | `tools/build-engines.sh` |
| Monolith | 2.01 | GPL-3.0 | commit `19b8a61` | `tools/build-engines.sh` |

Upstream sources:

- Stockfish — <https://github.com/official-stockfish/Stockfish>
- Fairy-Stockfish — <https://github.com/fairy-stockfish/Fairy-Stockfish>
- Ethereal — <https://github.com/AndyGrant/Ethereal>
- Monolith — <https://github.com/cimarronOST/Monolith>

### Local modifications

**Stockfish** is the only engine with a source change. `NNUE_EMBEDDING_OFF`
disables the embedded NNUE networks so the binary shrinks from roughly 76 MB to
about 1 MB; the networks are downloaded by the app instead. The exact diff is
[`../patches/stockfish-17.1-nnue-embedding-off.patch`](../patches/stockfish-17.1-nnue-embedding-off.patch),
and `tools/build-stockfish.sh` applies it automatically. The build was done on
2026-11-22.

Fairy-Stockfish, Ethereal and Monolith are built from the revisions above
without source changes (only compiler/ABI flags differ, see the build script).

### Corresponding source

The corresponding source for every shipped engine can be obtained in two ways,
both free of charge:

1. **Reproduce it:** `tools/build-stockfish.sh` downloads Stockfish `sf_17.1`
   and applies the patch; `tools/build-engines.sh` clones the pinned commits.
   Both scripts contain every compiler flag that was used, so the result is the
   source that `libstockfish.so`, `libfairyfish.so`, `libethereal.so` and
   `libmonolith.so` were built from.
2. **Ask:** open an issue in this repository and the full source archive of the
   modified Stockfish 17.1 tree (including the patch and the toolchain
   description) will be provided.

Because the engines run as separate processes and the app communicates with
them only through the generic UCI text protocol, the app's own Kotlin code is
an independent work and is licensed under the GPL-3.0 as a whole (not because
it has to be, but because it keeps the licence situation simple).

### Engine names

"Stockfish", "Fairy-Stockfish", "Ethereal" and "Monolith" are names of their
respective projects. They are used here only to describe which engine is
running. This app is not affiliated with, endorsed by or sponsored by any of
these projects. No project logos are used; the launcher icon is original work.

---

## 2. Downloaded at runtime — not part of this repository

These files are fetched by the app on the user's device and are **not**
redistributed here. The licence obligations for them rest with whoever
redistributes them.

| Component | Licence | Source | Used for |
| --- | --- | --- | --- |
| Maia 3 networks (`maia3-5m`, `maia3-23m`, `.onnx`) | AGPL-3.0 | <https://huggingface.co/bqrio/maia3-onnx> (ONNX exports), official checkpoints: <https://huggingface.co/UofTCSSLab/Maia3-79M>, code: <https://github.com/CSSLab/maia3> | Neural, human-like opponent |
| Stockfish NNUE networks (`nn-*.nnue`) | GPL-3.0 (part of Stockfish) | <https://tests.stockfishchess.org/api/nn/> | Strong evaluation for Stockfish |
| Fairy-Stockfish NNUE network (`nn-3475407dc199.nnue`) | GPL-3.0 (part of Fairy-Stockfish) | <https://tests.stockfishchess.org/api/nn/> | Strong evaluation for Fairy-Stockfish |

Downloads are verified against a SHA-256 checksum before use, see
`app/src/main/assets/engines/catalog.json`.

Notes on Maia 3: the inference code in `CSSLab/maia3` is AGPL-3.0, and the
official checkpoints on Hugging Face (`UofTCSSLab/Maia3-*`) are published under
AGPLv3 as well. The ONNX files this app downloads are third-party exports from
`bqrio/maia3-onnx`, which declares `license: agpl-3.0` and names
`UofTCSSLab/Maia3-79M` as its base model. Since these files are downloaded on
the device and never shipped here, no AGPL obligation arises from this
repository. If you fork the app and decide to **bundle** the networks with your
release, the AGPL-3.0 applies to your distribution and you have to provide the
corresponding source of Maia 3 as well.

---

## 3. Libraries compiled into the APK

| Component | Licence | Notes |
| --- | --- | --- |
| ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android`) 1.32.0 | MIT | Runs the Maia 3 networks — <https://github.com/microsoft/onnxruntime> |
| AndroidX / Jetpack Compose / Room / Navigation / Lifecycle | Apache-2.0 | <https://developer.android.com/jetpack> |
| Kotlin standard library and coroutines | Apache-2.0 | <https://kotlinlang.org> |
| Material Icons Extended | Apache-2.0 | <https://fonts.google.com/icons> |

Licence texts: [`GPL-3.0.txt`](GPL-3.0.txt), [`MIT-ONNX-Runtime.txt`](MIT-ONNX-Runtime.txt),
[`Apache-2.0.txt`](Apache-2.0.txt), [`CC-BY-SA-3.0.txt`](CC-BY-SA-3.0.txt).

JUnit and `org.json` are used for tests only and are not part of the APK.

---

## 4. Artwork

**Chess pieces** — `app/src/main/res/drawable-nodpi/piece_*.png`

- Set: *cburnett* by Colin M. L. Burnett
- Licence: **CC BY-SA 3.0** — <https://creativecommons.org/licenses/by-sa/3.0/>
- Source: <https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces>
- Changes: the original SVG files were rasterised to 330×330 PNG with
  transparent background. The shapes themselves are unchanged.
- The ShareAlike condition applies to the piece images only. If you reuse or
  modify them, credit the author, link the licence and state your changes.

**App icon** — `app/src/main/res/drawable/ic_launcher_foreground.xml` and
`mipmap-anydpi-v26/ic_launcher*.xml`: original work, covered by the project
licence.

---

## 5. No warranty

The software is provided "as is", without warranty of any kind, as stated in
sections 15 and 16 of the GPL-3.0. Playing chess is safe; losing to Stockfish
is not covered by any support agreement.
