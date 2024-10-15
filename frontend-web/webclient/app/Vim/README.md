# vim.wasm

This repository contains files related to vim in the browser. These files are forked from the original repository at
https://github.com/rhysd/vim.wasm/blob/wasm and are released under a similar license as seen in `LICENSE.md`.

## Compilation

The module can be compiled via Docker using (assuming the linked repo is at `~/vim.wasm`):

```
docker run --rm -it --platform linux/amd64 -v ~/vim.wasm/:/mnt/vim.wasm emscripten/emsdk:1.39.10 bash
```

Inside the container run (from `/mnt/vim.wasm`):

```
RELEASE=1 ./build.sh
```

Copy `vim.js`, `vim.data` and `vim.wasm` to `/app/Assets/Vim`.

The `vim.js` file needs to be patched slightly such that the `getpwuid` function returns 0 instead of throwing an error.

The current version committed to VSC also adds in the [PaperColor](https://github.com/NLKNguyen/papercolor-theme/blob/master/colors/PaperColor.vim) theme.
This was done by adding the `PaperColor.vim` file in the appropriate folder found in `wasm/usr/local/share/vim/colors`.