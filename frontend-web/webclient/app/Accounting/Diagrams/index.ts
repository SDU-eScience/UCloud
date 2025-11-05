export interface ChartLabel {
    child: string;
    color: string;
}

export const colorNames: string[] = (() => {
    const colorStrength = [40, 80, 60];
    const shades = ["blue", "purple", "orange", "green", "red", "yellow", "pink"];
    return colorStrength.flatMap(str => shades.map(shade => `var(--${shade}-${str})`));
})();
