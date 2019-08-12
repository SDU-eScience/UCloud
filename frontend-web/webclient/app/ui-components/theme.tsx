const createMinMediaQuery = (n: number) => `@media screen and (min-width:${n}px)`;
const createMaxMediaQuery = (n: number) => `@media screen and (max-width:${n - 1}px)`;

const addAliases = (arr: any, aliases: any[]) =>
  aliases.forEach((key, i) =>
    Object.defineProperty(arr, key, {
      enumerable: false,
      get() {
        return this[i]
      }
    })
  );

// export const breakpoints = [32, 40, 48, 64, 80].map(n => n + 'em')
const bp = [512, 640, 768, 1024, 1280];
const aliases = ['xs', 'sm', 'md', 'lg', 'xl'];
export const breakpoints = bp.map(n => n + 'px');
export const responsiveBP = bp.map((n, i) => ({[aliases[i]]: n - 1})).reduce((obj, item) => ({...obj, ...item}), {});
//export const responsiveBP = { xs: 512-1, sm: 640-1, md: 768-1, lg: 1024-1, xl: 1280-1 } 

export const mediaQueryGT = bp.map(createMinMediaQuery);
export const mediaQueryLT = bp.map(createMaxMediaQuery);
addAliases(breakpoints, aliases);
addAliases(mediaQueryGT, aliases);
addAliases(mediaQueryLT, aliases);

export const space = [0, 4, 8, 16, 32, 64, 128];

export const fontFamily = `'IBM Plex Sans', sans-serif`;

export const fontSizes = [10, 14, 16, 20, 24, 32, 40, 56, 72];

export const medium = 300;
export const bold = 700;
export const regular = 400;

// styled-system's `fontWeight` function can hook into the `fontWeights` object
export const fontWeights = {
  medium,
  bold,
  regular
};

export const lineHeights = {
  standard: 1.5,
  display: 1.25
};

const letterSpacings = {
  normal: 'normal',
  caps: '0.025em'
};

export const textStyles = {
  display8: {
    fontSize: fontSizes[8] + 'px',
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.display
  },
  display7: {
    fontSize: fontSizes[7] + 'px',
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.display
  },
  display6: {
    fontSize: fontSizes[6] + 'px',
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.display
  },
  display5: {
    fontSize: fontSizes[5] + 'px',
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.display
  },
  display4: {
    fontSize: fontSizes[4] + 'px',
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.display
  },
  display3: {
    fontSize: fontSizes[3] + 'px',
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.display
  },
  display2: {
    fontSize: fontSizes[2] + 'px',
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.display
  },
  display1: {
    fontSize: fontSizes[1] + 'px',
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.display
  },
  display0: {
    fontSize: fontSizes[0] + 'px',
    fontWeight: fontWeights.bold,
    lineHeight: lineHeights.display,
    letterSpacing: letterSpacings.caps,
    textTransform: 'uppercase'
  },
  body2: {
    fontSize: fontSizes[2] + 'px',
    fontWeight: fontWeights.medium,
    lineHeight: lineHeights.standard
  },
  body1: {
    fontSize: fontSizes[1] + 'px',
    fontWeight: fontWeights.medium,
    lineHeight: lineHeights.standard
  },
  body0: {
    fontSize: fontSizes[0] + 'px',
    fontWeight: fontWeights.medium,
    lineHeight: lineHeights.standard
  }
};


// color palette
const black = "#000";
const white = "#fff";
const textBlack = "#1e252e";
//// Gray
//const lightGray = "#ebeff3";
const lightGray = "#f5f7f9";
const midGray = "#c9d3df";
const gray = "#8393A7";
const darkGray = "#53657d";
//// Blue
const lightBlue = "#f0f6ff";
const lightBlue2 = "#cdf";
// const blue = "#0055d5";
const blue = "#006aff";
// const blue = "#007bff"; 
const darkBlue = "#049";
//// Green
const lightGreen = "#00ff77";
const green = "#00C05A"; // secondary
const darkGreen = "#00823c";
//// Red
const lightRed = "#fcc";
const red = "#c00"; // secondary
const darkRed = "#800";
//// Orange
const orange = "#fa0"; // secondary
const darkOrange = "#a50";
//// Purple
const lightPurple = "#ecf";
const purple = "#70b"; // secondary

// Colors in the array come in 3 shades: light, medium , dark
// last color is for logo centers only
const appColors = [
  //["#0096ff", "#043eff"], // blue
  ["#F7D06A", "#E98C33", "#C46927"], // gold
  ["#EC6F8E", "#C75480", "#AA2457"], // salmon
  ["#B8D1E3", "#7C8DB3", "#5B698C"], // silver
  ["#83D8F9", "#3F80F6", "#2951BE"], // blue
  ["#AE83CF", "#9065D1", "#68449E"], // violet
  ["#E392CC", "#E2689D", "#B33B6D"], // pink
  ["#ECB08C", "#EA7B4B", "#BC4F33"], // bronze
  ["#90DCA1", "#69C97D", "#4D9161"], // green
  ["#F3B576", "#B77D50", "#7C4C3C"], // brown
  ["#D57AC5", "#E439C9", "#A1328F"], // purple
  ["#98E0F9", "#53A5F5", "#3E79C0"], // lightblue
  ["#DC6AA6", "#C62A5A", "#AA2457"], // red
  ["#C9D3DF", "#8393A7", "#53657D"], // gray colors from the theme
];

// Color Themes
// should use colors from the palette above
//// Light color theme
// const text = "#001833";
const text = textBlack;
const textHighlight = blue;
//const headerText = lightGray;
const headerText = white;
const headerBg = '#006aff';
const headerIconColor = headerText;
const headerIconColor2 = midGray;
// const borderGray = "#d1d6db";
const borderGray = midGray; //used for borders of cards, pagination, sidebar
//const paginationHoverColor = "#f7f7f7"
const paginationHoverColor = lightBlue;
const paginationDisabled = lightGray;
// Icons colors
const iconColor = darkGray;
const iconColor2 = gray;
const FtIconColor = lightGray;
const FtIconColor2 = midGray;
const FtFolderColor = gray;
const FtFolderColor2 = midGray;
const spinnerColor = blue;
// File table colors
const tableRowHighlight = lightBlue;
const appCard = "#ebeff3";


const colors = {
  black,
  white,
  lightGray,
  midGray,
  gray,
  darkGray,
  blue,
  lightBlue,
  lightBlue2,
  darkBlue,
  green,
  lightGreen,
  darkGreen,
  red,
  lightRed,
  darkRed,
  orange,
  darkOrange,
  purple,
  lightPurple,
  text,
  textHighlight,
  headerText,
  headerBg,
  headerIconColor,
  headerIconColor2,
  borderGray,
  paginationHoverColor,
  paginationDisabled,
  iconColor,
  iconColor2,
  FtIconColor,
  FtIconColor2,
  FtFolderColor,
  FtFolderColor2,
  spinnerColor,
  tableRowHighlight,
  wayfGreen: "#66b340",
  appCard
};

export const invertedColors = {
  ...colors,
  white: "#282c35",
  /* blue: "#ff9500", */
  tableRowHighlight: "#000",
  black: "#a4a5a9",
  text: "#e5e5e6",
  lightGray: "#111",
  lightBlue: "#000",
  midGray: "#555",
  paginationDisabled: "#111",
  paginationHoverColor: "#444",
  appCard: "#060707",
  borderGray: "#111"
};


export type ThemeColor = keyof typeof colors;


export {colors}

export const colorStyles = {
  whiteOnText: {
    color: colors.white,
    backgroundColor: colors.text
  },
  whiteOnGray: {
    color: colors.white,
    backgroundColor: colors.gray
  },
  textOnLightGray: {
    color: colors.text,
    backgroundColor: colors.lightGray
  },
  whiteOnBlue: {
    color: colors.white,
    backgroundColor: colors.blue
  },
  blueOnLightBlue: {
    color: colors.blue,
    backgroundColor: colors.lightBlue
  },
  whiteOnGreen: {
    color: colors.white,
    backgroundColor: colors.green
  },
  greenOnLightGreen: {
    color: colors.green,
    backgroundColor: colors.lightGreen
  },
  whiteOnRed: {
    color: colors.white,
    backgroundColor: colors.red
  },
  redOnLightRed: {
    color: colors.red,
    backgroundColor: colors.lightRed
  },
  textOnOrange: {
    color: colors.text,
    backgroundColor: colors.orange
  },
  whiteOnPurple: {
    color: colors.white,
    backgroundColor: colors.purple
  },
  purpleOnLightPurple: {
    color: colors.purple,
    backgroundColor: colors.lightPurple
  },
  textOnWhite: {
    color: colors.text,
    backgroundColor: colors.white
  },
  grayOnWhite: {
    color: colors.gray,
    backgroundColor: colors.white
  },
  blueOnWhite: {
    color: colors.blue,
    backgroundColor: colors.white
  },
  greenOnWhite: {
    color: colors.green,
    backgroundColor: colors.white
  },
  redOnWhite: {
    color: colors.red,
    backgroundColor: colors.white
  },
  purpleOnWhite: {
    color: colors.purple,
    backgroundColor: colors.white
  },
  whiteOnDarkOrange: {
    color: colors.white,
    backgroundColor: colors.darkOrange
  },
  // info: textOnLightGray
  info: {
    color: colors.text,
    backgroundColor: colors.lightGray
  },
  // success: whiteOnGreen
  success: {
    color: colors.white,
    backgroundColor: colors.green
  },
  //warning: textOnOrange
  warning: {
    color: colors.text,
    backgroundColor: colors.orange
  },
  // danger: whiteOnRed
  danger: {
    color: colors.white,
    backgroundColor: colors.red
  }
};

// styled-system's `borderRadius` function can hook into the `radii` object/array
export const radii = [0, 2, 6];
export const radius = '5px';

export const maxContainerWidth = '1280px';

// boxShadows: styled-systems hooks into shadows
// export const shadows = [
//   `0 0 2px 0 rgba(0,0,0,.08),0 1px 4px 0 rgba(0,0,0,.16)`,
//   `0 0 2px 0 rgba(0,0,0,.08),0 2px 8px 0 rgba(0,0,0,.16)`,
//   `0 0 2px 0 rgba(0,0,0,.08),0 4px 16px 0 rgba(0,0,0,.16)`,
//   `0 0 2px 0 rgba(0,0,0,.08),0 8px 32px 0 rgba(0,0,0,.16)`
// ]
// export const shadows = [
//   `0 1px 3px rgba(0,0,0,0.12), 0 1px 2px rgba(0,0,0,0.24)`,
//   `0 3px 6px rgba(0,0,0,0.16), 0 3px 6px rgba(0,0,0,0.23)`,
//   `0 10px 20px rgba(0,0,0,0.19), 0 6px 6px rgba(0,0,0,0.23)`,
//   `0 14px 28px rgba(0,0,0,0.25), 0 10px 10px rgba(0,0,0,0.22)`,
//   `0 19px 38px rgba(0,0,0,0.30), 0 15px 12px rgba(0,0,0,0.22)`
// ]
// from Material design: 1dp to 24dp elevations
const MDshadows = [
  `noshadow`,
  `0px  2px  1px -1px rgba(0,0,0,0.2), 0px  1px  1px 0px rgba(0,0,0,.14),0px 1px  3px 0px rgba(0,0,0,.12)`,
  `0px  3px  1px -2px rgba(0,0,0,0.2), 0px  2px  2px 0px rgba(0,0,0,.14),0px 1px  5px 0px rgba(0,0,0,.12)`,
  `0px  3px  3px -2px rgba(0,0,0,0.2), 0px  3px  4px 0px rgba(0,0,0,.14),0px 1px  8px 0px rgba(0,0,0,.12)`,
  `0px  2px  4px -1px rgba(0,0,0,0.2), 0px  4px  5px 0px rgba(0,0,0,.14),0px 1px 10px 0px rgba(0,0,0,.12)`,
  `0px  3px  5px -1px rgba(0,0,0,0.2), 0px  5px  8px 0px rgba(0,0,0,.14),0px 1px 14px 0px rgba(0,0,0,.12)`,
  `0px  3px  5px -1px rgba(0,0,0,0.2), 0px  6px 10px 0px rgba(0,0,0,.14),0px 1px 18px 0px rgba(0,0,0,.12)`,
  `0px  4px  5px -2px rgba(0,0,0,0.2), 0px  7px 10px 1px rgba(0,0,0,.14),0px 2px 16px 1px rgba(0,0,0,.12)`,
  `0px  5px  5px -3px rgba(0,0,0,0.2), 0px  8px 10px 1px rgba(0,0,0,.14),0px 3px 14px 2px rgba(0,0,0,.12)`,
  `0px  5px  6px -3px rgba(0,0,0,0.2), 0px  9px 12px 1px rgba(0,0,0,.14),0px 3px 16px 2px rgba(0,0,0,.12)`,
  `0px  6px  6px -3px rgba(0,0,0,0.2), 0px 10px 14px 1px rgba(0,0,0,.14),0px 4px 18px 3px rgba(0,0,0,.12)`,
  `0px  6px  7px -4px rgba(0,0,0,0.2), 0px 11px 15px 1px rgba(0,0,0,.14),0px 4px 20px 3px rgba(0,0,0,.12)`,
  `0px  7px  8px -4px rgba(0,0,0,0.2), 0px 12px 17px 2px rgba(0,0,0,.14),0px 5px 22px 4px rgba(0,0,0,.12)`,
  `0px  7px  8px -4px rgba(0,0,0,0.2), 0px 13px 19px 2px rgba(0,0,0,.14),0px 5px 24px 4px rgba(0,0,0,.12)`,
  `0px  7px  9px -4px rgba(0,0,0,0.2), 0px 14px 21px 2px rgba(0,0,0,.14),0px 5px 26px 4px rgba(0,0,0,.12)`,
  `0px  8px  9px -5px rgba(0,0,0,0.2), 0px 15px 22px 2px rgba(0,0,0,.14),0px 6px 28px 5px rgba(0,0,0,.12)`,
  `0px  8px 10px -5px rgba(0,0,0,0.2), 0px 16px 24px 2px rgba(0,0,0,.14),0px 6px 30px 5px rgba(0,0,0,.12)`,
  `0px  8px 11px -5px rgba(0,0,0,0.2), 0px 17px 26px 2px rgba(0,0,0,.14),0px 6px 32px 5px rgba(0,0,0,.12)`,
  `0px  9px 11px -5px rgba(0,0,0,0.2), 0px 18px 28px 2px rgba(0,0,0,.14),0px 7px 34px 6px rgba(0,0,0,.12)`,
  `0px  9px 12px -6px rgba(0,0,0,0.2), 0px 19px 29px 2px rgba(0,0,0,.14),0px 7px 36px 6px rgba(0,0,0,.12)`,
  `0px 10px 13px -6px rgba(0,0,0,0.2), 0px 20px 31px 3px rgba(0,0,0,.14),0px 8px 38px 7px rgba(0,0,0,.12)`,
  `0px 10px 13px -6px rgba(0,0,0,0.2), 0px 21px 33px 3px rgba(0,0,0,.14),0px 8px 40px 7px rgba(0,0,0,.12)`,
  `0px 10px 14px -6px rgba(0,0,0,0.2), 0px 22px 35px 3px rgba(0,0,0,.14),0px 8px 42px 7px rgba(0,0,0,.12)`,
  `0px 11px 14px -7px rgba(0,0,0,0.2), 0px 23px 36px 3px rgba(0,0,0,.14),0px 9px 44px 8px rgba(0,0,0,.12)`,
  `0px 11px 15px -7px rgba(0,0,0,0.2), 0px 24px 38px 3px rgba(0,0,0,.14),0px 9px 46px 8px rgba(0,0,0,.12)`
];
export const shadows = [
  MDshadows[3],
  MDshadows[6],
  MDshadows[12],
  MDshadows[18],
  MDshadows[24]
];
const BoxShadowsAliases = ['sm', 'md', 'lg', 'xl', 'xxl'];
addAliases(shadows, BoxShadowsAliases);

// animation duration
export const duration = {
  fastest: `100ms`,
  fast: `150ms`,
  normal: `300ms`,
  slow: `450ms`,
  slowest: `600ms`
};

// animation easing curves
const easeInOut = 'cubic-bezier(0.5, 0, 0.25, 1)';
const easeOut = 'cubic-bezier(0, 0, 0.25, 1)';
const easeIn = 'cubic-bezier(0.5, 0, 1, 1)';
const easeInQuint = 'cubic-bezier(0.755, 0.05, 0.855, 0.06)'; //This is a steep easeIn curve
const easeInQuintR = `cubic-bezier(${1 - 0.855}, ${1 - 0.06}, ${1 - 0.755}, ${1 - 0.05})`; //This is a steep easeIn curve
const easeOutQuint = 'cubic-bezier(0.23, 1, 0.32, 1)';
const stepStart = 'step-start';
const stepEnd = 'step-end';

const timingFunctions = {
  easeInOut,
  easeOut,
  easeIn,
  easeInQuint,
  easeInQuintR,
  easeOutQuint,
  stepStart,
  stepEnd,
};

// animation delay
const transitionDelays = {
  xsmall: `40ms`,
  small: `60ms`,
  medium: `160ms`,
  large: `260ms`,
  xLarge: `360ms`
};

const theme = {
  breakpoints,
  mediaQueryGT,
  mediaQueryLT,
  space,
  fontFamily,
  fontSizes,
  fontWeights,
  lineHeights,
  letterSpacings,
  regular,
  bold,
  textStyles,
  colors,
  colorStyles,
  appColors,
  radii,
  radius,
  shadows,
  maxContainerWidth,
  duration,
  timingFunctions,
  transitionDelays
};

export type Theme = typeof theme

export default theme
