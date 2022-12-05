import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {isLightThemeStored} from "@/UtilityFunctions";
import {FitAddon} from "xterm-addon-fit";
import "xterm/css/xterm.css";
import {ITheme, Terminal} from "xterm";

export interface XtermHook {
    termRef: React.RefObject<HTMLDivElement>;
    terminal: Terminal;
    fitAddon: FitAddon;
}

export function useXTerm(props: { autofit?: boolean } = {}): XtermHook {
    const didMount = useRef(false);

    const [term] = useState(() => new Terminal({
        theme: getTheme(),
        fontFamily: "Jetbrains Mono, Ubuntu Mono, courier-new, courier, monospace",
        rendererType: "canvas",
        fontSize: 16
    }));

    const [fitAddon] = useState(() => new FitAddon());
    const elem = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (elem.current) {
            if (!didMount.current) {
                term.loadAddon(fitAddon);
                term.open(elem.current);
                didMount.current = true;
            }
            fitAddon.fit();
        } else if (elem.current === null) {
            didMount.current = false;
        }
    }, []);

    React.useLayoutEffect(() => {
        const listener = (): void => {
            if (props.autofit) {
                fitAddon.fit();
            }
        };
        window.addEventListener("resize", listener);

        return () => {
            window.removeEventListener("resize", listener);
        };
    }, [props.autofit]);

    const [storedTheme, setStoredTheme] = useState(isLightThemeStored());

    if (isLightThemeStored() !== storedTheme) {
        setStoredTheme(isLightThemeStored());
        term.setOption("theme", getTheme());
    }

    return {
        termRef: elem,
        terminal: term,
        fitAddon,
    };
}

export function appendToXterm(term: Terminal, textToAppend: string): void {
    const remainingString = textToAppend.replace(/\n/g, "\r\n");
    term.write(remainingString);
}

function getTheme(): ITheme {
    if (!isLightThemeStored()) {
        return {
            background: "#282a36",
            selection: "#44475a",
            foreground: "#f8f8f2",
            cyan: "#8be9fd",
            green: "#50fa7b",
            black: "#21222C",
            red: "#FF5555",
            yellow: "#F1FA8C",
            blue: "#BD93F9",
            magenta: "#FF79C6",
            white: "#F8F8F2",
            brightBlack: "#6272A4",
            brightRed: "#FF6E6E",
            brightGreen: "#69FF94",
            brightYellow: "#FFFFA5",
            brightBlue: "#D6ACFF",
            brightMagenta: "#FF92DF",
            brightCyan: "#A4FFFF",
            brightWhite: "#FFFFFF",
        };
    } else {
        return {
            background: "#ffffff",
            selection: "#d6d6d6",
            foreground: "#4d4d4c",
            red: "#c82829",
            yellow: "#eab700",
            green: "#718c00",
            cyan: "#3e999f",
            blue: "#4271ae",
            magenta: "#8959a8",
            cursor: "#4d4d4c"
        };
    }
}
