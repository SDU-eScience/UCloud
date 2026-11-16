#!/usr/bin/env python3

import calendar
import json
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request
from datetime import date, datetime, timezone

REPO_ROOT = os.path.abspath(
    os.environ.get(
        "K3S_RELEASE_REPO_ROOT",
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."),
    )
)
CATALOG_PATH = os.path.join(
    REPO_ROOT, "integrated-applications/k8s-app/pkg/shared/catalog.go"
)
DOCS_PATH = os.path.join(
    REPO_ROOT,
    "provider-integration/im2/docs/src/k8s/kubernetes-clusters/reference.md",
)

K3S_TAG_RE = re.compile(r"^v(\d+)\.(\d+)\.(\d+)\+k3s(\d+)$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")

CATALOG_MARKER = "var K3sCatalog = []K3sRelease{"
CATALOG_ENTRY_RE = re.compile(
    r"\t\{\n"
    r"\t\tRelease:\s+\"(?P<release>[^\"]+)\",\n"
    r"\t\tSha256Amd64:\s+\"(?P<amd64>[^\"]+)\",\n"
    r"\t\tSha256Arm64:\s+\"(?P<arm64>[^\"]+)\",\n"
    r"\t\tSupportedFrom:\s+time\.Date\((?P<sy>\d+), (?P<sm>\d+), (?P<sd>\d+), 0, 0, 0, 0, time\.UTC\),\n"
    r"\t\tEolAt:\s+time\.Date\((?P<ey>\d+), (?P<em>\d+), (?P<ed>\d+), 0, 0, 0, 0, time\.UTC\),\n"
    r"\t\},"
)

DOCS_MARKER = (
    "| Release | Channel | Available from | Support ends |\n"
    "|---------|---------|----------------|--------------|\n"
)
DOCS_ROW_RE = re.compile(
    r"^\| `(?P<release>[^`]+)` \| (?P<channel>[a-z]+) \| (?P<available>[^|]+) \| (?P<eol>[^|]+) \|$"
)


def fail(message):
    print(message, file=sys.stderr)
    sys.exit(1)


def gh_api(args):
    result = subprocess.run(
        ["gh", "api", *args],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        fail(result.stderr.strip() or "the GitHub API request failed")
    return result.stdout


def release_key(tag):
    match = K3S_TAG_RE.fullmatch(tag)
    if match is None:
        return None
    return tuple(int(value) for value in match.groups())


def parse_date(year, month, day):
    return date(int(year), int(month), int(day))


def add_months(value, months):
    month_index = value.month - 1 + months
    year = value.year + month_index // 12
    month = month_index % 12 + 1
    day = min(value.day, calendar.monthrange(year, month)[1])
    return date(year, month, day)


def format_docs_date(value):
    return f"{value.day} {value.strftime('%B')} {value.year}"


def read_url(url):
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "ucloud-k3s-release-updater"},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read().decode("utf-8")


def parse_checksum(text, asset):
    for line in text.splitlines():
        fields = line.split()
        if len(fields) == 2 and fields[1].lstrip("*") == asset:
            value = fields[0].strip().lower()
            if SHA256_RE.fullmatch(value) is None:
                fail(f"Invalid checksum for {asset}: {value}")
            return value
    fail(f"Could not find {asset} in the checksum file")


def fetch_checksums(tag):
    quoted = urllib.parse.quote(tag, safe="")
    base = f"https://github.com/k3s-io/k3s/releases/download/{quoted}"
    amd64 = parse_checksum(read_url(f"{base}/sha256sum-amd64.txt"), "k3s")
    arm64 = parse_checksum(read_url(f"{base}/sha256sum-arm64.txt"), "k3s-arm64")
    return amd64, arm64


def kubernetes_minor_release_date(major, minor):
    tag = f"v{major}.{minor}.0"
    output = gh_api(
        [
            f"repos/kubernetes/kubernetes/releases/tags/{tag}",
            "--jq",
            ".published_at",
        ]
    ).strip()
    if output in ("", "null"):
        fail(f"Could not find the release date of Kubernetes {tag}")
    released = datetime.fromisoformat(output.replace("Z", "+00:00"))
    return released.astimezone(timezone.utc).date()


def read_catalog(text):
    marker_at = text.index(CATALOG_MARKER)
    body_start = text.index("\n", marker_at) + 1
    body_end = text.index("\n}", body_start)
    body = text[body_start:body_end]

    entries = []
    pieces = []
    for match in CATALOG_ENTRY_RE.finditer(body):
        pieces.append(match.group(0))
        entries.append(
            {
                "release": match.group("release"),
                "amd64": match.group("amd64"),
                "arm64": match.group("arm64"),
                "supported_from": parse_date(
                    match.group("sy"), match.group("sm"), match.group("sd")
                ),
                "eol_at": parse_date(match.group("ey"), match.group("em"), match.group("ed")),
            }
        )

    if "\n".join(pieces) != body:
        fail("The k3s catalog has an unexpected format")

    keys = [release_key(entry["release"]) for entry in entries]
    if any(key is None for key in keys):
        fail("The k3s catalog contains an unknown release tag")
    if keys != sorted(keys, reverse=True):
        fail("The k3s catalog is not sorted from newest to oldest")

    return body_start, body_end, body, entries


def render_catalog_entry(entry):
    supported_from = entry["supported_from"]
    eol_at = entry["eol_at"]
    return (
        "\t{\n"
        f'\t\tRelease:       "{entry["release"]}",\n'
        f'\t\tSha256Amd64:   "{entry["amd64"]}",\n'
        f'\t\tSha256Arm64:   "{entry["arm64"]}",\n'
        f"\t\tSupportedFrom: time.Date({supported_from.year}, {supported_from.month}, {supported_from.day}, 0, 0, 0, 0, time.UTC),\n"
        f"\t\tEolAt:         time.Date({eol_at.year}, {eol_at.month}, {eol_at.day}, 0, 0, 0, 0, time.UTC),\n"
        "\t},"
    )


def stable_k3s_releases():
    releases = json.loads(gh_api(["repos/k3s-io/k3s/releases?per_page=100"]))

    latest_by_minor = {}
    for release in releases:
        if release.get("draft") or release.get("prerelease"):
            continue

        tag = release.get("tag_name", "")
        key = release_key(tag)
        if key is None:
            continue

        minor = (key[0], key[1])
        if minor not in latest_by_minor or key > latest_by_minor[minor][0]:
            latest_by_minor[minor] = (key, tag)

    return latest_by_minor


def read_docs(text):
    marker_at = text.index(DOCS_MARKER)
    rows_start = marker_at + len(DOCS_MARKER)
    rest = text[rows_start:]

    rows = []
    for line in rest.splitlines(keepends=True):
        if not line.startswith("|"):
            break
        rows.append(line.rstrip("\r\n"))

    rows_text = "".join(row + "\n" for row in rows)

    parsed_rows = []
    for row in rows:
        match = DOCS_ROW_RE.fullmatch(row)
        if match is None:
            fail("The Kubernetes release table has an unexpected format")
        key = release_key(match.group("release"))
        if key is None:
            fail("The Kubernetes release table contains an unknown release tag")
        parsed_rows.append((key, match.group("release"), row))

    keys = [item[0] for item in parsed_rows]
    if keys != sorted(keys, reverse=True):
        fail("The Kubernetes release table is not sorted from newest to oldest")

    return rows_start, rows_text, parsed_rows


def render_docs_row(entry):
    return (
        f"| `{entry['release']}` | stable | "
        f"{format_docs_date(entry['supported_from'])} | "
        f"{format_docs_date(entry['eol_at'])} |"
    )


def write_pr_body(added):
    lines = [
        "This pull request adds new entries to the pinned k3s release catalog and the "
        "Kubernetes release table in the documentation.",
        "",
        "New releases:",
        "",
    ]
    for entry in added:
        url = "https://github.com/k3s-io/k3s/releases/tag/" + urllib.parse.quote(
            entry["release"], safe=""
        )
        lines.append(f"- `{entry['release']}` - {url}")

    lines += [
        "",
        "The action only adds entries. It does not change or remove existing entries.",
        "",
        "The SHA-256 checksums come from the `sha256sum-amd64.txt` and "
        "`sha256sum-arm64.txt` release assets. The support window is 14 months "
        "from the upstream Kubernetes minor release.",
        "",
        "After merging, publish a new version of the Kubernetes application so "
        "the new releases become available.",
    ]

    body = "\n".join(lines) + "\n"
    body_path = os.environ.get("K3S_RELEASE_PR_BODY_PATH")
    if body_path:
        with open(body_path, "w", encoding="utf-8") as output:
            output.write(body)


def write_outputs(added):
    versions = ", ".join(entry["release"] for entry in added)

    output_path = os.environ.get("GITHUB_OUTPUT")
    if output_path:
        with open(output_path, "a", encoding="utf-8") as output:
            output.write("updated=true\n")
            output.write(f"versions={versions}\n")

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as summary:
            summary.write("## New k3s releases\n\n")
            for entry in added:
                summary.write(f"- `{entry['release']}`\n")


def main():
    with open(CATALOG_PATH, "r", encoding="utf-8") as source:
        catalog_text = source.read()

    body_start, body_end, catalog_body, existing = read_catalog(catalog_text)

    if "\n".join(render_catalog_entry(entry) for entry in existing) != catalog_body:
        fail("The k3s catalog cannot be rendered without changing existing entries")

    existing_tags = {entry["release"] for entry in existing}
    existing_keys = [release_key(entry["release"]) for entry in existing]
    if not existing_keys:
        fail("The k3s catalog is empty")
    minor_floor = min(key[:2] for key in existing_keys)

    dates_by_minor = {}
    for entry, key in zip(existing, existing_keys):
        minor = key[:2]
        previous = dates_by_minor.get(minor)
        if previous is None or key > previous[0]:
            dates_by_minor[minor] = (key, entry["supported_from"], entry["eol_at"])

    now = datetime.now(timezone.utc).date()
    latest_by_minor = stable_k3s_releases()

    added = []
    for minor in sorted(latest_by_minor):
        if minor < minor_floor:
            continue

        key, tag = latest_by_minor[minor]
        if tag in existing_tags:
            continue

        known_dates = dates_by_minor.get(minor)
        if known_dates is not None and key <= known_dates[0]:
            continue
        if known_dates is not None:
            supported_from = known_dates[1]
            eol_at = known_dates[2]
        else:
            supported_from = kubernetes_minor_release_date(*minor)
            eol_at = add_months(supported_from, 14)

        if eol_at <= now:
            print(f"Skipping {tag}: support ended on {eol_at.isoformat()}")
            continue

        amd64, arm64 = fetch_checksums(tag)
        added.append(
            {
                "release": tag,
                "amd64": amd64,
                "arm64": arm64,
                "supported_from": supported_from,
                "eol_at": eol_at,
            }
        )

    if not added:
        print("No new k3s releases found")
        return

    added.sort(key=lambda entry: release_key(entry["release"]), reverse=True)
    all_entries = sorted(
        existing + added,
        key=lambda entry: release_key(entry["release"]),
        reverse=True,
    )
    new_catalog_body = "\n".join(
        render_catalog_entry(entry) for entry in all_entries
    )
    catalog_text = (
        catalog_text[:body_start] + new_catalog_body + catalog_text[body_end:]
    )

    with open(DOCS_PATH, "r", encoding="utf-8") as source:
        docs_text = source.read()

    rows_start, docs_rows_text, docs_rows = read_docs(docs_text)
    docs_releases = {item[1] for item in docs_rows}
    added_entries = [entry for entry in added if entry["release"] not in docs_releases]

    if added_entries:
        new_rows = sorted(
            [(key, release, row) for key, release, row in docs_rows]
            + [
                (release_key(entry["release"]), entry["release"], render_docs_row(entry))
                for entry in added_entries
            ],
            key=lambda item: item[0],
            reverse=True,
        )
        new_docs_rows_text = "".join(row + "\n" for _, _, row in new_rows)
        docs_text = (
            docs_text[:rows_start]
            + new_docs_rows_text
            + docs_text[rows_start + len(docs_rows_text):]
        )

    with open(CATALOG_PATH, "w", encoding="utf-8") as output:
        output.write(catalog_text)
    with open(DOCS_PATH, "w", encoding="utf-8") as output:
        output.write(docs_text)

    write_pr_body(added)
    write_outputs(added)

    print("Added k3s releases: " + ", ".join(entry["release"] for entry in added))


if __name__ == "__main__":
    main()
