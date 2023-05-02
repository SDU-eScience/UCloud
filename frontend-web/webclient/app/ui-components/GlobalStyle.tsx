import theme from "./theme";
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

const UIGlobalStyle = `

/*  /files/metadata/templates/create/ START */

div.modal.fade.show { 
    max-width: 800px;
    background-color: var(--lightGray, #f00);
    border-color: 2px solid var(--black, #f00);
}

div.tooltip.show.bs-tooltip-auto {
    color: var(--text, #f00);
    background-color: var(--white);
    border: 1px solid var(--midGray);
    border-radius: 5px;
    padding: 2px 5px 2px 5px;
}

div#form-builder_add_popover.popover-inner h3.popover-header {
    margin-top: 0px;
    border-bottom: none;
}

div.popover.show.bs-popover-auto {
    background-color: var(--lightGray);
    border: 2px solid var(--blue);
    border-radius: 10px;
    padding-left: 4px;
    padding-right: 4px;
    padding-bottom: 4px;
    padding-top: 4px;
}

span.toggle-collapse {
    display: hidden;
}

div.popover.show.bs-popover-auto > div.popover-inner > h3.popover-header {
    border-bottom: none;
    margin-top: 0px;
}

div > div > div.modal { 
    margin-left: calc(50% - 400px);
    padding: 10px 10px 10px 10px;
    background-color: var(--white, #f00);
    border: 1px solid var(--gray, #f00);
    border-radius: 5px;
}


div.modal-footer > button {
    margin-right: 5px;
}

div.action-buttons > button.btn, button.btn.btn-primary, button.btn.btn-secondary {
    font-smoothing: antialiased;
    display: inline-flex;
    justify-content: center;
    align-items: center;
    text-align: center;
    text-decoration: none;
    font-family: inherit;
    font-weight: ${theme.bold};
    cursor: pointer;
    border-radius: ${theme.radius};
    background-color: var(--blue, #f00);
    color: var(--white, #f00);
    border-width: 0;
    border-style: solid;
    line-height: 1.5;
    width: 100px;
    height: 40px;
}


/* /files/metadata/templates/create/ END */

/* Colors */
html {

    /* REWRITE-VARS */
    --sidebarWidth: 64px;
    --secondarySidebarWidth: 220px;
    --popInWidth: 368px;
    --sidebarColor: #2c68f6;
    --sidebarSecondaryColor: #5c89f4;
    /* LIGHT */
    --gradientStart: #B6D8FB;
    --gradientEnd: #fff;    
    --inputColor: #E9E9E9;

    /* FONT-SIZES */
    --secondaryText: 12px;
    --buttonText: 14px;
    --breadText: 16px;
    --interactiveElementsSize: 20px;
    /* FONT-SIZES end */
    

    /* REWRITE-VARS end */



    --black: #000;
    --white: #fff;
    --textBlack: #1e252e;
    --lightGray: #f5f7f9;
    --midGray: #c9d3df;
    --gray: #8393A7;
    --darkGray: #53657d;
    --lightBlue: #D9E9FF;
    --lightBlue2: #cdf;
    --blue: #006aff;
    --darkBlue: #049;
    --lightGreen: #00ff77;
    --green: #00C05A;
    --darkGreen: #00823c;
    --lightRed: #fcc;
    --red: #c00;
    --darkRed: #800;
    --orange: #ff6400;
    --darkOrange: #ff5722;
    --lightPurple: #ecf;
    --purple: #70b;
    --yellow: #ffed33;
    --text: var(--textBlack, #f00);
    --textHighlight: var(--blue, #f00);
    --headerText: var(--white, #f00);
    --headerBg: #006aff;
    --headerIconColor: #fff;
    --headerIconColor2: #c9d3df;
    --borderGray: var(--midGray, #f00);
    --paginationHoverColor: var(--lightBlue, #f00);
    --paginationDisabled: var(--lightGray, #f00);
    --iconColor: var(--darkGray, #f00);
    --iconColor2: var(--gray, #f00);
    --FtIconColor: #f5f7f9;
    --FtIconColor2: #c9d3df;
    --FtFolderColor: var(--gray, #f00);
    --FtFolderColor2: var(--midGray, #f00);
    --spinnerColor: var(--blue, #f00);
    --tableRowHighlight: var(--lightBlue, #f00);
    --appCard: #fafbfc;
    --wayfGreen: #c8dd51;
    --appStoreFavBg: #e8f1fc
    --invertedThemeColor: #fff;
    --fixedBlack: #000;
    --fixedWhite: #fff;
    --activeSpreadsheet: #dcebf6;
    --lightOrange: #ffc107;    
    font-feature-settings: "cv05" on, "cv09" on, "cv02" on, "calt" on, "ss03" on;
}

html.light {
    --white: #fff;
    --tableRowHighlight: var(--lightBlue, #f00);
    --black: #000;
    --text: #1e252e;
    --lightGray: #f5f7f9;
    --lightBlue: #E6F1FF;
    --midGray: #c9d3df;
    --paginationDisabled: var(--lightGray, #f00);
    --paginationHoverColor: var(--lightBlue, #f00);
    --appCard: #fafbfc;
    --borderGray: var(--midGray, #f00);
    --invertedThemeColor: #000;
    --projectHighlight: #dfffee;
    --appStoreFavBg: #e8f1fc;
    --activeSpreadsheet: #dcebf6;
    --modalShadow: rgba(255, 255, 255, 0.75);
}

html.dark {
    --white: #282c35;
    --tableRowHighlight: #000;
    --black: #a4a5a9;
    --text: #e5e5e6;
    --lightGray: #111;
    --lightBlue: #000;
    --midGray: #555;
    --paginationDisabled: #111;
    --paginationHoverColor: #444;
    --appCard: #1d1d1d;
    --borderGray: #111;
    --invertedThemeColor: #fff;
    --projectHighlight: #00c05a;
    --appStoreFavBg: #00204d;
    --activeSpreadsheet: #000;
    --modalShadow: rgba(0, 0, 0, 0.75);
    --gradientStart: #375BB1;
    --gradientEnd: #282C33;
    --sidebarColor: #141414;
    --sidebarSecondaryColor: #1D1D1D;
    --inputColor: #000;
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
    ${theme.fontFamily},
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

  line-height: ${theme.lineHeights.standard}; /* 3 */
  font-weight: ${theme.fontWeights.regular};
  color: var(--text, #f00);
  -moz-tab-size: 4; /* 4 */
  tab-size: 4; /* 4 */
  -ms-text-size-adjust: 100%; /* 5 */
  -webkit-text-size-adjust: 100%; /* 5 */
  /* word-break: break-word;  */ /* 6 */
}

div.ReactModal__Content.ReactModal__Content--after-open {
  background-color: var(--white, #f00);
}

/* Sections
 * ========================================================================== */

/**
 * Remove the margin in all browsers (opinionated).
 */

body {
  margin: 0;
  background-color: var(--white, #f00);
}

/**
 * Correct the font size and margin on h1 elements within section and
 * article contexts in Chrome, Firefox, and Safari.
 */

h1 {
  font-size: 2em;
  margin: 0.67em 0;
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

main {
  display: block;
}

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
svg,
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
  outline: 1px dotted ButtonText;
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
    box-shadow: ${theme.shadows.sm};
    position: absolute;
    margin-left: 50px;
    padding: 5px 5px 5px 5px;
    width: 350px;
    height: auto;
    display: none;
    color: var(--black);
    background-color: var(--white);
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

`;

export default UIGlobalStyle;
