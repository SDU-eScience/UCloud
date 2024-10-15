import * as React from "react";
import {useEffect, useLayoutEffect, useRef} from "react";
import {VimWasm} from "@/Vim/vimwasm";
import {injectStyle} from "@/Unstyled";
import {isCommandPaletteTriggerEvent} from "@/CommandPalette/CommandPalette";

const vimStyle = injectStyle("vim", k => `
    ${k} {
        padding: 0;
        margin: 0;
        width: 100%;
        height: 100%;
    }
    
    ${k} > canvas {
        padding: 0;
        width: 100%;
        height: 100%;
    }
    
    ${k} > input {
        width: 1px;
        color: transparent;
        background-color: transparent;
        padding: 0;
        border: 0;
        outline: none;
        vertical-align: middle;
        position: absolute;
        top: 0;
        left: 0;   
    }
`);

export const VimEditor: React.FunctionComponent<{
    vim: React.MutableRefObject<VimWasm | null>;
    onInit: () => void;
}> = (props) => {
    const editorRef = useRef<HTMLDivElement>(null);
    const screenRef = useRef<HTMLCanvasElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        const vim = props.vim.current;
        if (!vim) return;
        vim.onVimInit = props.onInit;
    }, [props.onInit]);

    useEffect(() => {
        return () => {
            props.vim.current = null;
        };
    }, []);

    useLayoutEffect(() => {
        if (!editorRef.current || !screenRef.current || !inputRef.current) return;
        const vim = new VimWasm({
            canvas: screenRef.current,
            input: inputRef.current,
            workerScriptPath: "/app/Assets/Vim/vim.js",
        });
        props.vim.current = vim;

        const clipboardAvailable = navigator.clipboard !== undefined;

        function clipboardSupported(): Promise<any> | undefined {
            if (clipboardAvailable) return undefined;
            return Promise.reject();
        }

        vim.readClipboard = () => {
            return clipboardSupported() ?? navigator.clipboard.readText();
        };
        vim.onWriteClipboard = text => {
            return clipboardSupported() ?? navigator.clipboard.writeText(text);
        };

        vim.onError = err => {
            console.error(err);
        };

        vim.onVimInit = () => {
            vim.cmdline("set clipboard=unnamed");
            props.onInit();
        };

        vim.onVimExit = (statusCode) => {
            console.log("Vim exit", {statusCode});
        };

        vim.onKeyDownHandler = ev => {
            if (isCommandPaletteTriggerEvent(ev)) {
                // Prevent default but do propagate
                ev.preventDefault();
            }
        };

        vim.start({
            debug: false,
            perf: false,
            dirs: [],
            files: {},
            persistentDirs: ["/home/web_user/.vim"],
            cmdArgs: ["/tmp/index", "-c", "set guifont=JetBrains\\ Mono:h14"],
            clipboard: clipboardAvailable,
        });
    }, []);

    return <div ref={editorRef} className={vimStyle}>
        <canvas ref={screenRef}/>
        <input ref={inputRef} autoComplete="off" autoFocus/>
    </div>
};
