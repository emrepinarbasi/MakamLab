# Runtime dependency inventory

The JAR files in `lib/` are intentionally versioned with the source snapshot so
the release can be rebuilt without resolving changing dependency versions.

| File | Purpose |
|---|---|
| `jl1.0.1.jar` | MP3 decoding |
| `poi-6.0.0.jar`, `poi-ooxml-6.0.0.jar`, `poi-ooxml-lite-6.0.0.jar` | DOCX reports |
| `xmlbeans-5.4.1.jar` | OOXML schema/runtime support |
| `log4j-api-2.26.1.jar`, `log4j-core-2.26.1.jar` | Apache POI logging runtime |
| `commons-codec-1.23.0.jar` | encoding/decoding utilities |
| `commons-collections4-4.6.0.jar` | collection utilities |
| `commons-compress-1.28.0.jar` | archive/compression support |
| `commons-io-2.22.0.jar` | I/O utilities |
| `commons-math3-3.6.1.jar` | numerical utilities |
| `SparseBitSet-1.3.jar` | sparse bit-set dependency |
| `curvesapi-1.08.jar` | OOXML curve/geometry dependency |

See `THIRD_PARTY_NOTICES.md` and `SBOM.spdx.json` for licenses and upstream
locations. Adding or upgrading a dependency requires updating all three files
and rerunning the release tests.
