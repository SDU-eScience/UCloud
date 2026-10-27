# Ucloud completion

Completion for UCloud CLI.

## Build and publish the download image

Set up Docker Buildx and registry access, then run:

```
./build-container.sh
```

This builds Linux and macOS binaries for amd64 and arm64. It publishes versioned and `latest` images to the Core registry. Set the CLI version in `version.txt` before a release.

The script uses BuildKit named contexts for the local modules in `provider-integration`. It mounts those modules during compilation. BuildKit also caches Go modules and compiler output between builds on the same builder.

## Install

The installer selects the binary for the current operating system and architecture. It checks the download checksum and installs to `~/.local/bin`.

```
curl -fsSL 'https://cloud.sdu.dk/cli/install.sh' | UCLOUD_CLI_BASE_URL='https://cloud.sdu.dk' bash
```

Set `UCLOUD_CLI_BASE_URL` to the UCloud site root. The installer downloads files from its `/cli` path and configures the default CLI environment to that site if no CLI config exists.

## Install a local build

The local installer builds the current working tree and installs the binary to `~/.local/bin`. It uses the local modules from `provider-integration`, so uncommitted source changes are included. The build reports version `dev` and does not auto-update.

```
./install-local.sh
```

Pass `--install-dir` to choose another directory. Pass `--url` to change the default environment. On a fresh machine, the installer configures `https://ucloud.localhost.direct` to match the launcher. An existing CLI config is left unchanged.

```
./install-local.sh --install-dir "$HOME/bin" --url 'https://ucloud.localhost.direct'
```

## Updates

Release builds check `<environment>/cli/current/manifest.json` at most once every 15 minutes. The CLI downloads a newer version, verifies its SHA-256 checksum, and replaces the running binary.

Automatic checks are skipped in these cases:

- The CLI is a development build.
- Input or output is not a terminal.
- The active environment is a local launcher environment.

Run `ucloud update --check` to check manually. Run `ucloud update` to install the newest version. Run `ucloud version` to print the current version.

## Workspaces

Commands use your personal workspace when no workspace is selected. Run `ucloud workspace use personal` to return to it. Run `ucloud workspace use <name>` to select a project.

### Build

run to build the `ucloud` executable

```
./build.sh
```

### Usage

to make it globally available
either cp `ucloud` to `/usr/local/bin` or add to your `$PATH`

to generate completion script:

```
mkdir -p ~/.config/ucloud/completion
ucloud completion > ~/.config/ucloud/completion/_ucloud
```

Add to your `.bashrc` or `.zshrc`

```
export PATH="~/.config/ucloud/completion:$PATH"
fpath=("~/.config/ucloud/completion" $fpath)

#Ensure that completion system is initialized
autoload -Uz compinit
compinit

# Load/register ucloud completion
autoload -Uz _ucloud
compdef _ucloud ucloud

```
