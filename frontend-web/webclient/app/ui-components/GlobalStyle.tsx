import monoFont from "@/Assets/JetBrainsMono-Regular.woff2";
import inter from "@/Assets/Inter.woff";

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
    function hueToRgb(p, q, t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1 / 6) return p + (q - p) * 6 * t;
        if (t < 1 / 2) return q;
        if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
        return p;
    }

    let r, g, b;

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

const colorsByTheme: { dark: ThemedColors; light: ThemedColors; } = {
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

const UIGlobalStyle = `
  html.light {
    --primaryMain: var(--blue-60);
    --primaryLight: var(--blue-50);
    --primaryDark: var(--blue-70);
    --primaryContrast: #ffffff;
    --primaryContrastAlt: #a6a8a9;
    --secondaryMain: var(--gray-10);
    --secondaryLight: var(--gray-5);
    --secondaryDark: var(--gray-20);
    --secondaryContrast: #000000;
    --secondaryContrastAlt: #222222;
    --errorMain: var(--red-60);
    --errorLight: var(--red-50);
    --errorDark: var(--red-70);
    --errorContrast: #ffffff;
    --errorContrastAlt: #a6a8a9;
    --warningMain: var(--orange-40);
    --warningLight: var(--orange-30);
    --warningDark: var(--orange-50);
    --warningContrast: #000000;
    --warningContrastAlt: #222222;
    --infoMain: var(--gray-70);
    --infoLight: var(--gray-60);
    --infoDark: var(--gray-80);
    --infoContrast: #ffffff;
    --infoContrastAlt: #a6a8a9;
    --successMain: var(--green-50);
    --successLight: var(--green-40);
    --successDark: var(--green-60);
    --successContrast: #ffffff;
    --successContrastAlt: #a6a8a9;
    --backgroundDefault: #ffffff;
    --borderColor: var(--gray-20);
    --borderColorHover: var(--gray-30);
    --backgroundCard: var(--backgroundDefault);
    --backgroundCardBorder: red;
    --backgroundCardBorderHover: red;
    --backgroundDisabled: var(--gray-5);
    --textPrimary: #212529;
    --textSecondary: #646669;
    --textDisabled: #909294;
    --rowHover: var(--gray-10);
    --rowActive: var(--blue-10);
    --gradientStart: var(--blue-30);
    --gradientEnd: var(--blue-20);
  }
  html.dark {
    --modalShadow: rgba(0, 0, 0, 0.75);
    --primaryMain: var(--blue-80);
    --primaryLight: var(--blue-70);
    --primaryDark: var(--blue-90);  
    // --primaryMain: red;
    // --primaryLight: #1058c4;
    // --primaryDark: #438bf7;
    --primaryContrast: #ffffff;
    --primaryContrastAlt: #a6a8a9;
    --secondaryMain: var(--gray-30);
    --secondaryLight: var(--gray-20);
    --secondaryDark: var(--gray-40);
    --secondaryContrast: #000000;
    --secondaryContrastAlt: #222222;
    --errorMain: var(--red-60);
    --errorLight: var(--red-50);
    --errorDark: var(--red-70);
    --errorContrast: #ffffff;
    --errorContrastAlt: #a6a8a9;
    --warningMain: var(--orange-40);
    --warningLight: var(--orange-30);
    --warningDark: var(--orange-50);
    --warningContrast: #000000;
    --warningContrastAlt: #222222;
    --infoMain: var(--gray-30);
    --infoLight: var(--gray-20);
    --infoDark: var(--gray-40);
    --infoContrast: #000000;
    --infoContrastAlt: #222222;
    --successMain: var(--green-50);
    --successLight: var(--green-40);
    --successDark: var(--green-60);
    --successContrast: #ffffff;
    --successContrastAlt: #a6a8a9;
    --backgroundDefault: var(--gray-100);
    --borderColor: var(--gray-80);
    --borderColorHover: var(--gray-70);
    --backgroundCard: var(--backgroundDefault);
    --backgroundCardBorder: red;
    --backgroundCardBorderHover: red;
    --backgroundDisabled: var(--gray-90);
    --textPrimary: #ffffff;
    --textSecondary: #bcbebf;
    --textDisabled: #909294;
    --rowHover: var(--gray-90);
    --rowActive: var(--blue-90);
    --gradientStart: #000;
    --gradientEnd: var(--blue-90);
  }

html.light {
    color-scheme: light;
}

html.dark {
    color-scheme: dark;
}

html {
    --modalShadow: rgba(255, 255, 255, 0.75);

    /* New color palette */
    --purple-5: #FCF9FC;
    --purple-10: #F5EBF5;
    --purple-20: #E6CDE6;
    --purple-30: #D8B0D8;
    --purple-40: #C993C9;
    --purple-50: #B972B9;
    --purple-60: #A74EA7;
    --purple-70: #993399;
    --purple-80: #870F87;
    --purple-90: #680068;
    --red-5: #FFF9F6;
    --red-10: #FFE9E1;
    --red-20: #FFC9B6;
    --red-30: #FFA78C;
    --red-40: #FF805F;
    --red-50: #FF4628;
    --red-60: #E11005;
    --red-70: #BD1809;
    --red-80: #961B0B;
    --red-90: #6C1A0C;
    --orange-5: #FFF9F3;
    --orange-10: #FFEAD7;
    --orange-20: #FFCA9A;
    --orange-30: #FFA95B;
    --orange-40: #FF8018;
    --orange-50: #E0680D;
    --orange-60: #B7540A;
    --orange-70: #9B4708;
    --orange-80: #7D3806;
    --orange-90: #5D2A05;
    --yellow-5: #FFFAE2;
    --yellow-10: #FFEE98;
    --yellow-20: #FFCF04;
    --yellow-30: #E6B704;
    --yellow-40: #CA9F04;
    --yellow-50: #AC8604;
    --yellow-60: #8E6C03;
    --yellow-70: #795B02;
    --yellow-80: #624802;
    --yellow-90: #493501;
    --green-5: #F2FDEF;
    --green-10: #D3F8C9;
    --green-20: #89EC6D;
    --green-30: #4BD823;
    --green-40: #42BD1F;
    --green-50: #389F1A;
    --green-60: #2D8215;
    --green-70: #266D12;
    --green-80: #1F580E;
    --green-90: #17410B;
    --gray-5: #F7F8F9;
    --gray-10: #ECEEF0;
    --gray-20: #D0D5DC;
    --gray-30: #B6BEC8;
    --gray-40: #9BA6B4;
    --gray-50: #7F8C9E;
    --gray-60: #627288;
    --gray-70: #4F6178;
    --gray-80: #404D60;
    // --gray-90: #2F3946;
    --gray-90: #2A313B;
    --gray-100: #21262D;
    // --gray-110: #191C20;
    --blue-5: #F7FAFE;
    --blue-10: #E4EFFC;
    --blue-20: #BCD7F7;
    --blue-30: #95C0F3;
    --blue-40: #6DA8EE;
    --blue-50: #3E8CE9;
    --blue-60: #096DE3;
    --blue-70: #035BC3;
    --blue-80: #03499D;
    --blue-90: #023774;
    /* New color palette END */

    --defaultShadow: rgba(0, 0, 0, 0.16) 0px 3px 6px, rgba(0, 0, 0, 0.10) 0px -3px 12px, rgba(0, 0, 0, 0.23) 0px 3px 6px;
    --sidebarWidth: 64px;
    --secondarySidebarWidth: 220px;
    --popInWidth: 368px;
    --sidebarColor: var(--blue-60);
    --sidebarSecondaryColor: var(--blue-70);
    --appLogoBackground: transparent;

    --secondaryText: 10px;
    --buttonText: 14px;
    --breadText: 14px;
    --interactiveElementsSize: 20px;
    font-size: 14px;

    --monospace: 'Jetbrains Mono', 'Ubuntu Mono', courier-new, courier, monospace;
    --sansSerif: 'Inter', sans-serif;
    
    --iconColor: var(--gray-80);
    // --iconColor2: var(--gray-60);
    --iconColor2: var(--blue-60);
    --FtIconColor: #f5f7f9;
    --FtIconColor2: #c9d3df;
    --FtFolderColor: #8393A7;
    --FtFolderColor2: #c9d3df;
    
    --fixedWhite: #ffffff;
    --fixedBlack: #000000;
    
    --wayfGreen: #c8dd51;
    
    font-feature-settings: "cv05" on, "cv09" on, "cv02" on, "calt" on, "ss03" on;
}

html.dark {
    --sidebarColor: var(--blue-90);
    --sidebarSecondaryColor: var(--blue-80);
    // --sidebarColor: var(--gray-100);
    // --sidebarSecondaryColor: var(--gray-90);
    --appLogoBackground: #ffffff;
}

/*! sanitize.css v7.0.3 | CC0 License | github.com/csstools/sanitize.css */

/* Document
 * ========================================================================== */

/**
 * 1. Remove repeating backgrounds in all browsers (opinionated).
 * 2. Add border box sizing in all browsers (opinionated).
 */

*,
::before,
::after {
  background-repeat: no-repeat; /* 1 */
  box-sizing: border-box; /* 2 */
  transition: 1s cubic-bezier(0,1,0,1);
  transition-property: background, background-color, background-image, border;
}

/**
 * 1. Add text decoration inheritance in all browsers (opinionated).
 * 2. Add vertical alignment inheritance in all browsers (opinionated).
 */

::before,
::after {
  text-decoration: inherit; /* 1 */
  vertical-align: inherit; /* 2 */
}

/**
 * 1. Use the default cursor in all browsers (opinionated).
 * 2. Use the default user interface font in all browsers (opinionated).
 * 3. Correct the line height in all browsers.
 * 4. Use a 4-space tab width in all browsers (opinionated).
 * 5. Prevent adjustments of font size after orientation changes in
 *    IE on Windows Phone and in iOS.
 * 6. Breaks words to prevent overflow in all browsers (opinionated) NB: This causes problems in Buttons. Removed
 */

html {
  cursor: default; /* 1 */
  font-family:
    'Inter',
    system-ui,
    /* macOS 10.11-10.12 */ -apple-system,
    /* Windows 6+ */ Segoe UI,
    /* Android 4+ */ Roboto,
    /* Ubuntu 10.10+ */ Ubuntu,
    /* Gnome 3+ */ Cantarell,
    /* KDE Plasma 4+ */ Oxygen,
    /* fallback */ sans-serif,
    /* macOS emoji */ "Apple Color Emoji",
    /* Windows emoji */ "Segoe UI Emoji",
    /* Windows emoji */ "Segoe UI Symbol",
    /* Linux emoji */ "Noto Color Emoji"; /* 2 */

  line-height: 1.5; /* 3 */
  font-weight: 400;
  color: var(--textPrimary, #f00);
  -moz-tab-size: 4; /* 4 */
  tab-size: 4; /* 4 */
  -ms-text-size-adjust: 100%; /* 5 */
  -webkit-text-size-adjust: 100%; /* 5 */
  /* word-break: break-word;  */ /* 6 */
}

div.ReactModal__Content.ReactModal__Content--after-open {
  background-color: var(--backgroundDefault, #f00);
}

/* Sections
 * ========================================================================== */

/**
 * Remove the margin in all browsers (opinionated).
 */

body {
  margin: 0;
  background-color: var(--backgroundDefault, #f00);
}

/**
 * Correct the font size and margin on h1 elements within section and
 * article contexts in Chrome, Firefox, and Safari.
 */

h1 {
  font-size: 2em;
}

/* Grouping content
 * ========================================================================== */

/**
 * 1. Add the correct sizing in Firefox.
 * 2. Show the overflow in Edge and IE.
 */

hr {
  height: 0; /* 1 */
  overflow: visible; /* 2 */
}

/**
 * Add the correct display in IE.
 */

/**
 * Remove the list style on navigation lists in all browsers (opinionated).
 */

nav ol,
nav ul {
  list-style: none;
}

/**
 * 1. Use the default monospace user interface font
 *    in all browsers (opinionated).
 * 2. Correct the odd em font sizing in all browsers.
 */

pre {
  font-family:
    /* macOS 10.10+ */ Menlo,
    /* Windows 6+ */ Consolas,
    /* Android 4+ */ Roboto Mono,
    /* Ubuntu 10.10+ */ Ubuntu Monospace,
    /* KDE Plasma 4+ */ Oxygen Mono,
    /* Linux/OpenOffice fallback */ Liberation Mono,
    /* fallback */ monospace; /* 1 */

  font-size: 1em; /* 2 */
}

/* Text-level semantics
 * ========================================================================== */

/**
 * Remove the gray background on active links in IE 10.
 */

a {
  background-color: transparent;
  text-decoration: none;
}

/**
 * Add the correct text decoration in Edge, IE, Opera, and Safari.
 */

abbr[title] {
  text-decoration: underline;
  text-decoration: underline dotted;
}

/**
 * Add the correct font weight in Chrome, Edge, and Safari.
 */

b,
strong {
  font-weight: bolder;
}

/**
 * 1. Use the default monospace user interface font
 *    in all browsers (opinionated).
 * 2. Correct the odd em font sizing in all browsers.
 */

code,
kbd,
samp {
  font-family: var(--monospace);

  font-size: 1em; /* 2 */
}

/**
 * Add the correct font size in all browsers.
 */

small {
  font-size: 80%;
}

/*
 * Remove the text shadow on text selections in Firefox 61- (opinionated).
 * 1. Restore the coloring undone by defining the text shadow
 *    in all browsers (opinionated).
 */

::-moz-selection {
  background-color: #b3d4fc; /* 1 */
  color: #000; /* 1 */
  text-shadow: none;
}

::selection {
  background-color: #b3d4fc; /* 1 */
  color: #000; /* 1 */
  text-shadow: none;
}

/* Embedded content
 * ========================================================================== */

/*
 * Change the alignment on media elements in all browsers (opinionated).
 */

audio,
canvas,
iframe,
img,
video {
  vertical-align: middle;
}

/**
 * Add the correct display in IE 9-.
 */

audio,
video {
  display: inline-block;
}

/**
 * Add the correct display in iOS 4-7.
 */

audio:not([controls]) {
  display: none;
  height: 0;
}

/**
 * Remove the border on images inside links in IE 10-.
 */

img {
  border-style: none;
}

/**
 * Change the fill color to match the text color in all browsers (opinionated).
 */

svg {
  fill: currentColor;
}

/**
 * Hide the overflow in IE.
 */

svg:not(:root) {
  overflow: hidden;
}

/* Tabular data
 * ========================================================================== */

/**
 * Collapse border spacing in all browsers (opinionated).
 */

table {
  border-collapse: collapse;
}

/* Forms
 * ========================================================================== */

/**
 * Inherit styling in all browsers (opinionated).
 */

button,
input,
select,
textarea {
  font-family: inherit;
  font-size: inherit;
  line-height: inherit;
}

/**
 * Remove the margin in Safari.
 */

button,
input,
select {
  margin: 0;
}

/**
 * 1. Show the overflow in IE.
 * 2. Remove the inheritance of text transform in Edge, Firefox, and IE.
 */

button {
  overflow: visible; /* 1 */
  text-transform: none; /* 2 */
}

/**
 * Correct the inability to style clickable types in iOS and Safari.
 */

button,
[type="button"],
[type="reset"],
[type="submit"] {
  -webkit-appearance: button;
}

/**
 * Correct the padding in Firefox.
 */

fieldset {
  padding: 0.35em 0.75em 0.625em;
}

/**
 * Show the overflow in Edge and IE.
 */

input {
  overflow: visible;
}

/**
 * 1. Correct the text wrapping in Edge and IE.
 * 2. Correct the color inheritance from fieldset elements in IE.
 */

legend {
  color: inherit; /* 2 */
  display: table; /* 1 */
  max-width: 100%; /* 1 */
  white-space: normal; /* 1 */
}

/**
 * 1. Add the correct display in Edge and IE.
 * 2. Add the correct vertical alignment in Chrome, Firefox, and Opera.
 */

progress {
  display: inline-block; /* 1 */
  vertical-align: baseline; /* 2 */
}

/**
 * Remove the inheritance of text transform in Firefox.
 */

select {
  text-transform: none;
}

/**
 * 1. Remove the margin in Firefox and Safari.
 * 2. Remove the default vertical scrollbar in IE.
 * 3. Change the resize direction on textareas in all browsers (opinionated).
 */

textarea {
  margin: 0; /* 1 */
  overflow: auto; /* 2 */
  resize: vertical; /* 3 */
}

/**
 * Remove the padding in IE 10-.
 */

[type="checkbox"],
[type="radio"] {
  padding: 0;
}

/**
 * 1. Correct the odd appearance in Chrome and Safari.
 * 2. Correct the outline style in Safari.
 */

[type="search"] {
  -webkit-appearance: textfield; /* 1 */
  outline-offset: -2px; /* 2 */
}

/**
 * Correct the cursor style of increment and decrement buttons in Safari.
 */

::-webkit-inner-spin-button,
::-webkit-outer-spin-button {
  height: auto;
}

/**
 * Correct the text style of placeholders in Chrome, Edge, and Safari.
 */

::-webkit-input-placeholder {
  color: inherit;
  opacity: 0.54;
}

/**
 * Remove the inner padding in Chrome and Safari on macOS.
 */

::-webkit-search-decoration {
  -webkit-appearance: none;
}

/**
 * 1. Correct the inability to style clickable types in iOS and Safari.
 * 2. Change font properties to inherit in Safari.
 */

::-webkit-file-upload-button {
  -webkit-appearance: button; /* 1 */
  font: inherit; /* 2 */
}

/**
 * Remove the inner border and padding of focus outlines in Firefox.
 */

::-moz-focus-inner {
  border-style: none;
  padding: 0;
}

/**
 * Restore the focus outline styles unset by the previous rule in Firefox.
 */

:-moz-focusring {
  outline: 1px dotted var(--button-text);
}

/* Interactive
 * ========================================================================== */

/*
 * Add the correct display in Edge and IE.
 */

details {
  display: block;
}

/*
 * Add the correct styles in Edge, IE, and Safari.
 */

dialog {
  background-color: white;
  border: solid;
  color: black;
  display: block;
  height: -moz-fit-content;
  height: -webkit-fit-content;
  height: fit-content;
  left: 0;
  margin: auto;
  padding: 1em;
  position: absolute;
  right: 0;
  width: -moz-fit-content;
  width: -webkit-fit-content;
  width: fit-content;
}

dialog:not([open]) {
  display: none;
}

/*
 * Add the correct display in all browsers.
 */

summary {
  display: list-item;
}

/* Scripting
 * ========================================================================== */

/**
 * Add the correct display in IE 9-.
 */

canvas {
  display: inline-block;
}

/**
 * Add the correct display in IE.
 */

template {
  display: none;
}

/* User interaction
 * ========================================================================== */

/*
 * 1. Remove the tapping delay on clickable elements
      in all browsers (opinionated).
 * 2. Remove the tapping delay in IE 10.
 */

a,
area,
button,
input,
label,
select,
summary,
textarea,
[tabindex] {
  -ms-touch-action: manipulation; /* 1 */
  touch-action: manipulation; /* 2 */
}

/**
 * Add the correct display in IE 10-.
 */

[hidden] {
  display: none;
}

/* Accessibility
 * ========================================================================== */

/**
 * Change the cursor on busy elements in all browsers (opinionated).
 */

[aria-busy="true"] {
  cursor: progress;
}

/*
 * Change the cursor on control elements in all browsers (opinionated).
 */

[aria-controls] {
  cursor: pointer;
}

/*
 * Change the cursor on disabled, not-editable, or otherwise
 * inoperable elements in all browsers (opinionated).
 */

[aria-disabled=true],
[disabled] {
  cursor: not-allowed;
}

/*
 * Change the display on visually hidden accessible elements
 * in all browsers (opinionated).
 */

[aria-hidden="false"][hidden]:not(:focus) {
  clip: rect(0, 0, 0, 0);
  display: inherit;
  position: absolute;
}

.ReactModal__Overlay {
    z-index: 100;
    height: auto;
}

div.tooltip-content {
    box-shadow: var(--defaultShadow);
    position: absolute;
    margin-left: 50px;
    padding: 5px 5px 5px 5px;
    width: 350px;
    height: auto;
    display: none;
    color: var(--textPrimary);
    background-color: var(--backgroundDefault);
    z-index: 1;
}

div.tooltip-content.centered {
    justify-content: center;
}

div.tooltip-content.centered.user-box {
    width: 350px;
    height: 190px;
}

div.tooltip-content.centered.user-box.centered {
    display: flex;
    justify-content: center;
}

div.tooltip:hover > div.tooltip-content {
    display: flex;
}

a {
    color: var(--textHighlight);
}

input.search-field {
    width: 250px;
    height: 35px;
    margin-left: 8px;
}

h1, h2, h3, h4, h5, h6 {
    margin: 0;
}

`;

export default UIGlobalStyle;
