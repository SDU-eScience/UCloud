# Ucloud completion

Completion for Ucloud CLI.

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
