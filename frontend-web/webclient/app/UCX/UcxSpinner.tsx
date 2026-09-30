import React, {useEffect, useState} from "react";
import {useIsLightThemeStored} from "@/ui-components/theme";

const ucxSpinnerFrames = [
    " ⣾ ", " ⣽ ", " ⣻ ", " ⢿ ", " ⡿ ", " ⣟ ", " ⣯ ", " ⣷ ",
    " ⠁ ", " ⠂ ", " ⠄ ", " ⡀ ", " ⢀ ", " ⠠ ", " ⠐ ", " ⠈ ",
];

export function UcxSpinner({size = 32, margin, color}: {size?: number; margin?: string; color?: string}): React.ReactNode {
    const [frame, setFrame] = useState(0);

    useEffect(() => {
        const interval = window.setInterval(() => {
            setFrame(current => (current + 1) % ucxSpinnerFrames.length);
        }, 70);
        return () => window.clearInterval(interval);
    }, []);

    const lightMode = useIsLightThemeStored();
    const spinnerColor = color ?? (lightMode ? "var(--primaryMain)" : "var(--foreground)");

    return <span
        data-tag="loading-spinner"
        aria-label="Loading"
        role="status"
        style={{
            width: size,
            height: size,
            margin,
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            color: spinnerColor,
            fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
            fontSize: Math.max(12, Math.round(size * 0.72)),
            lineHeight: 1,
            whiteSpace: "pre",
            userSelect: "none",
        }}
    >
        {ucxSpinnerFrames[frame]}
    </span>;
}
