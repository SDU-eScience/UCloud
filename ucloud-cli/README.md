# Ucloud completion

Completion for Ucloud CLI.

## Build and publish the download image

Set up Docker Buildx and registry access, then run:

```
./build-container.sh
```

This builds Linux and macOS binaries for amd64 and arm64. It publishes versioned and `latest` images to the Core registry. Set the CLI version in `version.txt` before a release.

The script uses BuildKit named contexts for the local modules in `provider-integration`. It mounts those modules during compilation.

## Install

The installer selects the binary for the current operating system and architecture. It checks the download checksum and installs to `~/.local/bin`.

```
curl -fsSL 'https://cloud.sdu.dk/cli/install.sh' | UCLOUD_CLI_BASE_URL='https://cloud.sdu.dk/cli' bash
```

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
