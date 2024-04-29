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

export function useXTerm(props: {autofit?: boolean} = {}): XtermHook {
    const didMount = useRef(false);

    const fitAddon = React.useMemo(() => new FitAddon(), []);
    const term = React.useMemo(() => {
        const term = new Terminal({
            theme: getTheme(),
            fontFamily: "Jetbrains Mono, Ubuntu Mono, courier-new, courier, monospace",
            fontSize: 16,
        });
        term.loadAddon(fitAddon);
        return term;
    }, []);
    const elem = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (elem.current) {
            if (!didMount.current) {
                term.open(elem.current);
                didMount.current = true;
            }
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

        // HACK(Dan): Component is not auto-fitting correctly after upgrading to React 18. No doubt this is related to 
        // the fact that react doesn't like us using non-react components in this way. There is definitely a proper 
        // way fixing this. This is without a doubt not the proper way to fix it, but it is broken in production and
        // we don't have time to make a 'real' fix. So this fix, which works, will have to be our current solution.
        const intervals: number[] = [];
        intervals.push(window.setInterval(listener, 100));
        intervals.push(window.setInterval(listener, 500));
        intervals.push(window.setInterval(listener, 1000));
        intervals.push(window.setInterval(listener, 2000));
        intervals.push(window.setInterval(listener, 5000));

        return () => {
            window.removeEventListener("resize", listener);
            for (const interval of intervals) window.clearInterval(interval);
        };
    }, [props.autofit]);

    const [storedTheme, setStoredTheme] = useState(isLightThemeStored());

    if (isLightThemeStored() !== storedTheme) {
        setStoredTheme(isLightThemeStored());
        term.options.theme = getTheme();
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
            background: "#2a313b",
            selectionForeground: "#44475a",
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
            background: "#fff",
            selectionForeground: "#d6d6d6",
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
