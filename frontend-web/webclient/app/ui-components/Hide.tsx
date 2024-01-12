// TODO: cleanup the media selectors below (maybe put in theme.tsx ?)
const breakpoints = () => ({
  xs: `@media screen and (max-width: 512px)`,
  sm: `@media screen and (min-width: 512px) and (max-width: 640px)`,
  md: `@media screen and (min-width: 640px) and (max-width: 768px)`,
  lg: `@media screen and (min-width: 768px) and (max-width: 1024px)`,
  xl: `@media screen and (min-width: 1024px) and (max-width: 1280px)`,
  xxl: `@media screen and (min-width: 1280pxk)`
});

export type Sizes = "xs" | "sm" | "md" | "lg" | "xl" | "xxl";

export const device = (key: Sizes): string => {
  return `${breakpoints()[key]}`;
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
