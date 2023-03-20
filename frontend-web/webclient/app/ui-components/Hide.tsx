import theme, {Theme} from "./theme";

const getMaxWidth = (px: string): string => (parseInt(px, 10) - 1) + "px";

// TODO: cleanup the media selectors below (maybe put in theme.tsx ?)
const breakpoints = (props: {theme: Theme}) => ({
  xs: `@media screen and (max-width: ${getMaxWidth(props.theme.breakpoints[0])})`,
  sm: `@media screen and (min-width: ${props.theme.breakpoints[0]}) and (max-width: ${getMaxWidth(props.theme.breakpoints[1])})`,
  md: `@media screen and (min-width: ${props.theme.breakpoints[1]}) and (max-width: ${getMaxWidth(props.theme.breakpoints[2])})`,
  lg: `@media screen and (min-width: ${props.theme.breakpoints[2]}) and (max-width: ${getMaxWidth(props.theme.breakpoints[3])})`,
  xl: `@media screen and (min-width: ${props.theme.breakpoints[3]}) and (max-width: ${getMaxWidth(props.theme.breakpoints[4])})`,
  xxl: `@media screen and (min-width: ${props.theme.breakpoints[4]})`
});

export type Sizes = "xs" | "sm" | "md" | "lg" | "xl" | "xxl";

export const device = (key: Sizes): string => {
  return `${breakpoints({theme})[key]}`;
};

export function deviceBreakpoint(props: {
  minWidth?: string,
  maxWidth?: string,
  minHeight?: string,
  maxHeight?: string
}): string {
  let builder = "@media screen";

  if (props.minWidth !== undefined) {
    builder += " and ";
    builder += `(min-width: ${props.minWidth})`;
  }

  if (props.maxWidth !== undefined) {
    builder += " and ";
    builder += `(max-width: ${props.maxWidth})`;
  }

  if (props.minHeight !== undefined) {
    builder += " and ";
    builder += `(min-height: ${props.minHeight})`;
  }

  if (props.maxHeight !== undefined) {
    builder += " and ";
    builder += `(max-height: ${props.maxHeight})`;
  }

  return builder;
}
