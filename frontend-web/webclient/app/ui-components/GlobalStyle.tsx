import monoFont from "@/Assets/JetBrainsMono-Regular.woff2";
import inter from "@/Assets/Inter.woff2";

export function injectFonts(): void {
  const styleTag = document.createElement("style");
  styleTag.innerHTML = `
        /* Custom font */
        
        @font-face {
            font-family: 'Inter';
            src: url('${inter}');
            font-display: swap;
            -webkit-font-smoothing: antialiased;
            -moz-osx-font-smoothing: grayscale;
        }
        
        @font-face {
            font-family: "Jetbrains Mono";
            src: url("${monoFont}");
            font-display: swap;
        }
    `;
  document.head.appendChild(styleTag);
}

export function hexToRgb(color: string): [number, number, number] {
  const normalized = color.replace("#", "")
  const r = parseInt(normalized.substring(0, 2), 16);
  const g = parseInt(normalized.substring(2, 4), 16);
  const b = parseInt(normalized.substring(4, 6), 16);
  return [r, g, b];
}

export function mixColors(initialColor: string, endColor: string, percentage: number): string {
  const colorA = hexToRgb(initialColor);
  const colorB = hexToRgb(endColor);

  const diff = [colorB[0] - colorA[0], colorB[1] - colorA[1], colorB[2] - colorA[2]];

  const newR = Math.round(Math.min(255, colorA[0] + (diff[0] * percentage))).toString(16).padStart(2, '0');
  const newG = Math.round(Math.min(255, colorA[1] + (diff[1] * percentage))).toString(16).padStart(2, '0');
  const newB = Math.round(Math.min(255, colorA[2] + (diff[2] * percentage))).toString(16).padStart(2, '0');
  return "#" + newR + newG + newB;
}

export function colorDistanceRgb(color1Hex: string, color2Hex: string): number {
  const [r1, g1, b1] = hexToRgb(color1Hex);
  const [r2, g2, b2] = hexToRgb(color2Hex);
  return Math.sqrt(((r2 - r1) * (r2 - r1)) + ((g2 - g1) * (g2 - g1)) + ((b2 - b1) * (b2 - b1)));
}

export function grayScaleRgb(r: number, g: number, b: number): [number, number, number] {
  const avg = (r + g + b) / 3;
  return [avg, avg, avg];
}

export function invertColorRgb(r: number, g: number, b: number): [number, number, number] {
  return [255 - r, 255 - b, 255 - g];
}

export function rgbToHex(r: number, g: number, b: number): string {
  let result = "#";
  result += Math.min(255, r).toString(16).padStart(2, '0');
  result += Math.min(255, g).toString(16).padStart(2, '0');
  result += Math.min(255, b).toString(16).padStart(2, '0');
  return result;
}

export function shade(color: string, percentage: number): string {
  return mixColors(color, "#000000", percentage);
}

export function tint(color: string, percentage: number): string {
  return mixColors(color, "#ffffff", percentage);
}

export function compRgbToHsl(r: number, g: number, b: number): [number, number, number] {
  r /= 255;
  g /= 255;
  b /= 255;
  const vmax = Math.max(r, g, b);
  const vmin = Math.min(r, g, b);
  let h: number = 0;
  let s: number;
  let l: number;
  l = (vmax + vmin) / 2;

  if (vmax === vmin) {
    return [0, 0, l]; // achromatic
  }

  const d = vmax - vmin;
  s = l > 0.5 ? d / (2 - vmax - vmin) : d / (vmax + vmin);
  if (vmax === r) h = (g - b) / d + (g < b ? 6 : 0);
  if (vmax === g) h = (b - r) / d + 2;
  if (vmax === b) h = (r - g) / d + 4;
  h /= 6;

  return [h, s, l];
}

export function hslToRgb(h: number, s: number, l: number): string {
  function hueToRgb(p: number, q: number, t: number) {
    if (t < 0) t += 1;
    if (t > 1) t -= 1;
    if (t < 1 / 6) return p + (q - p) * 6 * t;
    if (t < 1 / 2) return q;
    if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
    return p;
  }

  let r: number, g: number, b: number;

  if (s === 0) {
    r = g = b = l; // achromatic
  } else {
    const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
    const p = 2 * l - q;
    r = hueToRgb(p, q, h + 1 / 3);
    g = hueToRgb(p, q, h);
    b = hueToRgb(p, q, h - 1 / 3);
  }

  return rgbToHex(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
}

export function rgbToHsl(rgbHex: string): [h: number, s: number, l: number] {
  const [r, g, b] = hexToRgb(rgbHex)
  return compRgbToHsl(r, g, b);
}

function luminance(r: number, g: number, b: number) {
  const RED = 0.2126;
  const GREEN = 0.7152;
  const BLUE = 0.0722;
  const GAMMA = 2.4;

  const a = [r, g, b].map((v) => {
    v /= 255;
    return v <= 0.03928
      ? v / 12.92
      : Math.pow((v + 0.055) / 1.055, GAMMA);
  });
  return a[0] * RED + a[1] * GREEN + a[2] * BLUE;
}

export function contrast(rgb1: string, rgb2: string) {
  const lum1 = luminance(...hexToRgb(rgb1));
  const lum2 = luminance(...hexToRgb(rgb2));
  const brightest = Math.max(lum1, lum2);
  const darkest = Math.min(lum1, lum2);
  return (brightest + 0.05) / (darkest + 0.05);
}

function generateColors(name: string, mainColor: string, darkTheme: boolean): string {
  const lightFn = darkTheme ? shade : tint;
  const darkFn = darkTheme ? tint : shade;

  let builder = "";
  builder += `--${name}Main: ${mainColor};\n`;
  builder += `--${name}Light: ${lightFn(mainColor, 0.2)};\n`
  builder += `--${name}Dark: ${darkFn(mainColor, 0.2)};\n`

  // NOTE(Dan): We grant a small advantage to white as a sort of tie-breaker.
  const contrastToWhite = contrast(mainColor, "#ffffff") + 0.2
  const contrastToBlack = contrast(mainColor, "#000000");
  if (contrastToWhite > contrastToBlack) {
    builder += `--${name}Contrast: #ffffff;\n`;
    builder += `--${name}ContrastAlt: #a6a8a9;\n`;
  } else {
    builder += `--${name}Contrast: #000000;\n`;
    builder += `--${name}ContrastAlt: #222222;\n`;
  }

  return builder;
}

const colors = {
  primary: "#146EF5",
  secondary: "#d3cdc8",
  error: "#d32f2f",
  warning: "#ed6c02",
  info: "#0288d1",
  success: "#198754"
};

interface ThemedColors {
  background: string;
  foreground: string;
}

const colorsByTheme: {dark: ThemedColors; light: ThemedColors;} = {
  dark: {
    background: "#212529",
    foreground: "#ffffff",
  },
  light: {
    background: "#ffffff",
    foreground: "#212529",
  }
};

// Note(Jonas): Do keep around.
function generatePalette(): string {
  let builder = "";

  function generateThemedColors(c: ThemedColors) {
    builder += `--backgroundDefault: ${c.background};\n`;
    builder += `--borderColor: ${mixColors(c.background, c.foreground, 0.20)};\n`;
    builder += `--borderColorHover: ${mixColors(c.background, c.foreground, 0.40)};\n`;

    builder += `--backgroundCard: ${mixColors(c.background, c.foreground, 0.030)};\n`;
    builder += `--backgroundCardBorder: ${mixColors(c.background, c.foreground, 0.25)};\n`;
    builder += `--backgroundCardBorderHover: ${mixColors(c.background, c.foreground, 0.60)};\n`;

    builder += `--textPrimary: ${c.foreground};\n`;
    builder += `--textSecondary: ${mixColors(c.foreground, c.background, 0.3)};\n`;
    builder += `--textDisabled: ${mixColors(c.foreground, c.background, 0.5)};\n`;

    builder += `--rowHover: ${mixColors(c.background, colors.primary, 0.15)};\n`;
    builder += `--rowActive: ${mixColors(c.background, colors.primary, 0.3)};\n`;

    const gradientStart = mixColors(c.background, colors.primary, 0.5);
    builder += `--gradientStart: ${gradientStart};\n`;
    builder += `--gradientEnd: ${mixColors(gradientStart, c.background, 0.75)};\n`;
  }

  builder += "html.light {\n"
  for (const [name, mainColor] of Object.entries(colors)) {
    builder += generateColors(name, mainColor, false);
  }
  generateThemedColors(colorsByTheme.light);
  builder += "}\n";

  builder += "html.dark {\n"
  for (const [name, mainColor] of Object.entries(colors)) {
    builder += generateColors(name, mainColor, true);
  }
  generateThemedColors(colorsByTheme.dark);
  builder += "}\n";
  return builder;
}

