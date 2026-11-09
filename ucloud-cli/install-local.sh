#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"

if [[ -z "${HOME:-}" ]]; then
    echo "HOME is not set" >&2
    exit 1
fi

install_dir="${UCLOUD_CLI_INSTALL_DIR:-${HOME}/.local/bin}"
site_url="${UCLOUD_CLI_BASE_URL:-https://ucloud.localhost.direct}"
site_url="${site_url%/}"

usage() {
    echo "Usage: install-local.sh [--install-dir PATH] [--url URL]"
    echo
    echo "Builds the CLI from this working tree and installs it."
    echo
    echo "Options:"
    echo "  --install-dir PATH  Install directory (default: ~/.local/bin)"
    echo "  --url URL           Default environment URL (default: https://ucloud.localhost.direct)"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --install-dir)
            if [[ $# -lt 2 ]]; then
                echo "Missing path after --install-dir" >&2
                exit 1
            fi
            install_dir="$2"
            shift 2
            ;;
        --url)
            if [[ $# -lt 2 ]]; then
                echo "Missing URL after --url" >&2
                exit 1
            fi
            site_url="${2%/}"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            exit 1
            ;;
    esac
done

if ! command -v go >/dev/null 2>&1; then
    echo "go is required to build UCloud CLI" >&2
    exit 1
fi

if [[ -z "${install_dir}" ]]; then
    echo "The install directory cannot be empty" >&2
    exit 1
fi

if [[ -z "${site_url}" ]]; then
    echo "The environment URL cannot be empty" >&2
    exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

echo "Building UCloud CLI from ${script_dir}"
(
    cd "${script_dir}"
    CGO_ENABLED=0 go build -mod=readonly -o "${tmp_dir}/ucloud" ./cmd/ucloud
)

mkdir -p "${install_dir}"
install -m 0755 "${tmp_dir}/ucloud" "${install_dir}/ucloud"

version="$("${install_dir}/ucloud" version)"
revision="$(git -C "${repo_root}" rev-parse --short HEAD 2>/dev/null || true)"

echo "Installed UCloud CLI ${version}${revision:+ (${revision})} to ${install_dir}/ucloud"

config_path="${HOME}/.config/ucloud/config.yaml"
if [[ ! -e "${config_path}" ]]; then
    "${install_dir}/ucloud" environment add ucloud --url "${site_url}" >/dev/null
    echo "Configured the default UCloud environment to ${site_url}"
else
    echo "Existing CLI config left unchanged"
fi

case ":${PATH}:" in
    *":${install_dir}:"*) ;;
    *) echo "Add ${install_dir} to your PATH to run ucloud from any directory." ;;
esac
