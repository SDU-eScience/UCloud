export interface ChartLabel {
    child: string;
    color: string;
}

export function makeColorMap(children: string[]): Map<string, string> {
    const sorted = [...children].sort();

    return new Map(
        sorted.map((child, index) => [
            child,
            colorNames[index % colorNames.length],
        ])
    );
}

export const colorNames: string[] = (() => {
    const colorStrength = ["main", "alt", "muted"];
    const shades = ["blue", "purple", "orange", "green", "red", "yellow", "pink"];
    return colorStrength.flatMap(str => shades.map(shade => `var(--chart-${shade}-${str})`));
})();

export const contrastColorNames: string[] = (() => {
    const colorStrength = ["main", "alt", "muted"];
    const colorsPerStrength = 7;
    return colorStrength.flatMap(str => Array(colorsPerStrength).fill(`var(--chart-${str}-contrast)`));
})();
