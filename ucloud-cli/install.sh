#!/usr/bin/env bash
set -euo pipefail

site_url="${UCLOUD_CLI_BASE_URL:-https://cloud.sdu.dk}"
site_url="${site_url%/}"
download_base_url="${site_url}/cli"

if [[ -z "${HOME:-}" ]]; then
    echo "HOME is not set" >&2
    exit 1
fi

install_dir="${UCLOUD_CLI_INSTALL_DIR:-${HOME}/.local/bin}"
config_path="${HOME}/.config/ucloud/config.yaml"
configure_default_environment=false
if [[ ! -e "${config_path}" ]]; then
    configure_default_environment=true
fi

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
        --help|-h)
            echo "Usage: install.sh [--install-dir PATH]"
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            exit 1
            ;;
    esac
done

if ! command -v curl >/dev/null 2>&1; then
    echo "curl is required to install UCloud CLI" >&2
    exit 1
fi

case "$(uname -s)" in
    Linux) os="linux" ;;
    Darwin) os="darwin" ;;
    *)
        echo "Unsupported operating system: $(uname -s)" >&2
        exit 1
        ;;
esac

case "$(uname -m)" in
    x86_64|amd64) arch="amd64" ;;
    arm64|aarch64) arch="arm64" ;;
    *)
        echo "Unsupported CPU architecture: $(uname -m)" >&2
        exit 1
        ;;
esac

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

version="$(curl -fsSL "${download_base_url}/current/version.txt")"
archive="ucloud-${version}-${os}-${arch}.tar.gz"
curl -fsSL "${download_base_url}/current/${archive}" -o "${tmp_dir}/${archive}"
curl -fsSL "${download_base_url}/current/checksums.txt" -o "${tmp_dir}/checksums.txt"

expected="$(awk -v file="${archive}" '{ count = split($2, path, "/"); if (path[count] == file) { print $1; exit } }' "${tmp_dir}/checksums.txt")"
if [[ -z "${expected}" ]]; then
    echo "No checksum found for ${archive}" >&2
    exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
    actual="$(sha256sum "${tmp_dir}/${archive}" | awk '{ print $1 }')"
elif command -v shasum >/dev/null 2>&1; then
    actual="$(shasum -a 256 "${tmp_dir}/${archive}" | awk '{ print $1 }')"
else
    echo "sha256sum or shasum is required to verify the download" >&2
    exit 1
fi

if [[ "${actual}" != "${expected}" ]]; then
    echo "Checksum verification failed for ${archive}" >&2
    exit 1
fi

tar -xzf "${tmp_dir}/${archive}" -C "${tmp_dir}"
mkdir -p "${install_dir}"
install -m 0755 "${tmp_dir}/ucloud" "${install_dir}/ucloud"

if [[ "${os}" == "darwin" ]] && command -v xattr >/dev/null 2>&1; then
    xattr -d com.apple.quarantine "${install_dir}/ucloud" 2>/dev/null || true
fi

echo "Installed UCloud CLI ${version} to ${install_dir}/ucloud"
if [[ "${configure_default_environment}" == true ]]; then
    "${install_dir}/ucloud" environment add ucloud --url "${site_url}" >/dev/null
    echo "Configured the default UCloud environment to ${site_url}"
fi

case ":${PATH}:" in
    *":${install_dir}:"*) ;;
    *) echo "Add ${install_dir} to your PATH to run ucloud from any directory." ;;
esac
