#
# Aster Communications Inc.
#
# Copyright (c) 2026 Aster Communications Inc.
#
# This file is part of this project.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.
#

import re
import sys
import urllib.request
from pathlib import Path

IANA_TLD_URL = "https://data.iana.org/TLD/tlds-alpha-by-domain.txt"
MIN_EXPECTED_TLDS = 1000
TLD_PATTERN = re.compile(r"^(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$")
OUTPUT_PATH = (
    Path(__file__).resolve().parent.parent
    / "app/src/main/kotlin/org/astermail/android/settings/iana_tlds.kt"
)

LICENSE_HEADER = """//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
"""


def load_source(local_path):
    if local_path:
        return Path(local_path).read_text(encoding="utf-8")
    with urllib.request.urlopen(IANA_TLD_URL, timeout=30) as response:
        return response.read().decode("utf-8")


def parse_tld_list(text):
    lines = [line.strip() for line in text.splitlines()]
    version_match = next(
        (re.search(r"Version\s+(\d+)", line) for line in lines if line.startswith("#")),
        None,
    )
    if not version_match:
        raise SystemExit("IANA list has no version line")
    tlds = sorted({line.lower() for line in lines if line and not line.startswith("#")})
    invalid = [tld for tld in tlds if not TLD_PATTERN.match(tld)]
    if invalid:
        raise SystemExit(f"Unexpected TLD entries: {', '.join(invalid)}")
    if len(tlds) < MIN_EXPECTED_TLDS:
        raise SystemExit(f"IANA list has only {len(tlds)} entries")
    return version_match.group(1), tlds


def main():
    version, tlds = parse_tld_list(load_source(sys.argv[1] if len(sys.argv) > 1 else None))
    body = "\n".join(
        [
            LICENSE_HEADER,
            "package org.astermail.android.settings",
            "",
            f'internal const val IANA_TLD_LIST_VERSION = "{version}"',
            "",
            "internal val IANA_TLDS: Set<String> = hashSetOf(",
            *[f'    "{tld}",' for tld in tlds],
            ")",
            "",
        ]
    )
    OUTPUT_PATH.write_text(body, encoding="utf-8", newline="\n")
    print(f"Wrote {len(tlds)} TLDs (version {version}) to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
