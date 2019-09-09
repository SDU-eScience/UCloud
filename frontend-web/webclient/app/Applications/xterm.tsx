import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {Terminal} from "xterm";
import "xterm/dist/xterm.css";

export function useXTerm(): [React.RefObject<HTMLDivElement>, (textToAppend: string) => void, () => void] {
    const [didMount, setDidMount] = useState(false);

    const [term] = useState(() => new Terminal({
        theme: {
            background: "#f5f7f9",
            foreground: "#073642",
            black: "#073642",
            brightBlack: "#002b36",
            brightGreen: "#586e75",
            brightYellow: "#657b83",
            brightBlue: "#839496",
            brightCyan: "#93a1a1",
            white: "#eee8d5",
            brightWhite: "#fdf6e3",
            yellow: "#b58900",
            brightRed: "#cb4b16",
            red: "#dc322f",
            magenta: "#d33682",
            brightMagenta: "#6c71c4",
            blue: "#268bd2",
            cyan: "#2aa198",
            green: "#859900",
            cursor: "#eee8d5",
            cursorAccent: "#eee8d5"
        }
    }));
    const elem = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (elem.current && !didMount) {
            term.resize(100, 40);
            term.open(elem.current);
            setDidMount(true);
        } else if (elem.current === null) {
            setDidMount(false);
        }
    });

    return [
        elem,
        textToAppend => {
            const remainingString = textToAppend.replace(/\n/g, "\r\n");
            term.write(remainingString);
        },
        () => {
            term.reset();
        }
    ];
}
