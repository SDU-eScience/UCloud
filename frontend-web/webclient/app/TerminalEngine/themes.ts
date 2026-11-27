import {isLightThemeStored, toggleCssColors} from "@/UtilityFunctions";
import {addThemeListener, removeThemeListener} from "@/ui-components/theme";

export interface TerminalPalette {
    background: string;
    foreground: string;
    cursor: string;
    cursorAccent: string;
    selectionBackground: string;
    selectionForeground: string;
    black: string;
    red: string;
    green: string;
    yellow: string;
    blue: string;
    magenta: string;
    cyan: string;
    white: string;
    brightBlack: string;
    brightRed: string;
    brightGreen: string;
    brightYellow: string;
    brightBlue: string;
    brightMagenta: string;
    brightCyan: string;
    brightWhite: string;
}

export const terminalThemes: {light: TerminalPalette; dark: TerminalPalette} = {
    dark: {
        background: "#000000",
        foreground: "#f0f3f6",
        cursor: "#58a6ff",
        cursorAccent: "#000000",
        selectionBackground: "#264f78",
        selectionForeground: "#ffffff",
        black: "#1c2128",
        red: "#ff7b72",
        green: "#56d364",
        yellow: "#e3b341",
        blue: "#79c0ff",
        magenta: "#d2a8ff",
        cyan: "#56d4dd",
        white: "#c9d1d9",
        brightBlack: "#6e7681",
        brightRed: "#ffa198",
        brightGreen: "#7ee787",
        brightYellow: "#f2cc60",
        brightBlue: "#a5d6ff",
        brightMagenta: "#e2c5ff",
        brightCyan: "#7ce0e8",
        brightWhite: "#ffffff",
    },
    light: {
        background: "#ffffff",
        foreground: "#1f2328",
        cursor: "#0969da",
        cursorAccent: "#ffffff",
        selectionBackground: "#b6d7ff",
        selectionForeground: "#1f2328",
        black: "#24292f",
        red: "#cf222e",
        green: "#1a7f37",
        yellow: "#9a6700",
        blue: "#0969da",
        magenta: "#8250df",
        cyan: "#1b7c83",
        white: "#6e7781",
        brightBlack: "#57606a",
        brightRed: "#a40e26",
        brightGreen: "#2da44e",
        brightYellow: "#bf8700",
        brightBlue: "#218bff",
        brightMagenta: "#a475f9",
        brightCyan: "#3192aa",
        brightWhite: "#8c959f",
    },
};

export function isTerminalLightTheme(): boolean {
    if (typeof document !== "undefined") {
        if (document.documentElement.classList.contains("dark")) return false;
        if (document.documentElement.classList.contains("light")) return true;
    }
    return isLightThemeStored();
}

export function getActiveTerminalTheme(): TerminalPalette {
    return isTerminalLightTheme() ? terminalThemes.light : terminalThemes.dark;
}

let themeListenerSeq = 0;

export function subscribeTerminalThemeChange(listener: () => void): () => void {
    const key = `terminal-engine-theme-${++themeListenerSeq}`;
    addThemeListener(key, listener);

    let observer: MutationObserver | null = null;
    if (typeof document !== "undefined" && typeof MutationObserver !== "undefined") {
        observer = new MutationObserver(() => {
            listener();
        });
        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ["class"],
        });
    }

    const onStorage = (event: StorageEvent) => {
        if (event.key !== "theme" && event.key !== null) return;
        toggleCssColors(isLightThemeStored());
        listener();
    };
    if (typeof window !== "undefined") {
        window.addEventListener("storage", onStorage);
    }

    const mediaQuery = typeof window !== "undefined" && typeof window.matchMedia === "function"
        ? window.matchMedia("(prefers-color-scheme: dark)")
        : null;
    const onMediaChange = () => {
        if (window.localStorage.getItem("theme") !== null) return;
        toggleCssColors(isLightThemeStored());
        listener();
    };
    mediaQuery?.addEventListener?.("change", onMediaChange);

    return () => {
        removeThemeListener(key);
        observer?.disconnect();
        if (typeof window !== "undefined") {
            window.removeEventListener("storage", onStorage);
        }
        mediaQuery?.removeEventListener?.("change", onMediaChange);
    };
}
