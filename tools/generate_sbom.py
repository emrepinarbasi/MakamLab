#!/usr/bin/env python3
"""Generate the SPDX 2.3 inventory for the MakamLab release source."""

from __future__ import annotations

import datetime as dt
import json
import pathlib
import sys
import uuid


def package(spdx_id: str, name: str, version: str, license_id: str,
            source: str, supplier: str = "NOASSERTION") -> dict:
    return {
        "SPDXID": spdx_id,
        "name": name,
        "versionInfo": version,
        "downloadLocation": source,
        "filesAnalyzed": False,
        "licenseConcluded": license_id,
        "licenseDeclared": license_id,
        "copyrightText": "NOASSERTION",
        "supplier": supplier,
    }


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: generate_sbom.py VERSION OUTPUT", file=sys.stderr)
        return 2

    version, destination = sys.argv[1], pathlib.Path(sys.argv[2])
    namespace = uuid.uuid5(uuid.NAMESPACE_URL, f"makamlab:{version}")
    packages = [
        package("SPDXRef-MakamLab", "MakamLab", version,
                "GPL-3.0-or-later", "NOASSERTION",
                "Person: Emre Pinarbasi (emrepinarbasi@gmail.com)"),
        package("SPDXRef-MakamBox", "MakamBox theory and reference data",
                "fdfbd03531afea0a2b4d4d1c4d9d1de229032b0f",
                "GPL-3.0-or-later", "https://github.com/miracatici/MakamBox"),
        package("SPDXRef-JLayer", "JLayer", "1.0.1", "LGPL-2.1-only",
                "https://repo1.maven.org/maven2/javazoom/jlayer/1.0.1/"),
        package("SPDXRef-POI", "Apache POI", "6.0.0", "Apache-2.0",
                "https://poi.apache.org/"),
        package("SPDXRef-XMLBeans", "Apache XMLBeans", "5.4.1", "Apache-2.0",
                "https://xmlbeans.apache.org/"),
        package("SPDXRef-Log4j", "Apache Log4j", "2.26.1", "Apache-2.0",
                "https://logging.apache.org/log4j/2.x/"),
        package("SPDXRef-CommonsCodec", "Apache Commons Codec", "1.23.0",
                "Apache-2.0", "https://commons.apache.org/proper/commons-codec/"),
        package("SPDXRef-CommonsCollections", "Apache Commons Collections", "4.6.0",
                "Apache-2.0", "https://commons.apache.org/proper/commons-collections/"),
        package("SPDXRef-CommonsCompress", "Apache Commons Compress", "1.28.0",
                "Apache-2.0", "https://commons.apache.org/proper/commons-compress/"),
        package("SPDXRef-CommonsIO", "Apache Commons IO", "2.22.0",
                "Apache-2.0", "https://commons.apache.org/proper/commons-io/"),
        package("SPDXRef-CommonsMath", "Apache Commons Math", "3.6.1",
                "Apache-2.0", "https://commons.apache.org/proper/commons-math/"),
        package("SPDXRef-SparseBitSet", "SparseBitSet", "1.3", "Apache-2.0",
                "https://github.com/brettwooldridge/SparseBitSet"),
        package("SPDXRef-CurvesAPI", "curvesapi", "1.08", "BSD-3-Clause",
                "https://github.com/virtuald/curvesapi"),
        package("SPDXRef-JavaRuntime", "OpenJDK runtime", "21",
                "GPL-2.0-with-classpath-exception", "NOASSERTION"),
        package("SPDXRef-FFmpegWindows", "FFmpeg Windows LGPL shared build",
                "9.0.2", "LGPL-2.1-or-later", "https://github.com/BtbN/FFmpeg-Builds"),
        package("SPDXRef-FFmpegUbuntu", "FFmpeg Ubuntu system package",
                "distribution-provided", "NOASSERTION", "https://packages.ubuntu.com/ffmpeg"),
    ]

    relationships = [{
        "spdxElementId": "SPDXRef-DOCUMENT",
        "relationshipType": "DESCRIBES",
        "relatedSpdxElement": "SPDXRef-MakamLab",
    }]
    for item in packages[1:]:
        relationships.append({
            "spdxElementId": "SPDXRef-MakamLab",
            "relationshipType": "DEPENDS_ON",
            "relatedSpdxElement": item["SPDXID"],
        })

    document = {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": f"MakamLab-{version}",
        "documentNamespace": f"https://spdx.org/spdxdocs/MakamLab-{version}-{namespace}",
        "creationInfo": {
            "created": dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
            .isoformat().replace("+00:00", "Z"),
            "creators": [
                "Person: Emre Pinarbasi (emrepinarbasi@gmail.com)",
                "Tool: MakamLab tools/generate_sbom.py",
            ],
        },
        "packages": packages,
        "relationships": relationships,
    }
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        json.dumps(document, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
