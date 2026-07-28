export interface ChartLabel {
    child: string;
    color: string;
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
