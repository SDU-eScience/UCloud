import * as React from "react";
import styled from "styled-components";
import {
  textStyle,
  fontSize,
  fontWeight,
  textAlign,
  lineHeight,
  space,
  color,
  SpaceProps,
  TextAlignProps,
  FontSizeProps,
  ColorProps,
  WidthProps,
  width,
  MaxWidthProps,
  maxWidth
} from "styled-system";
import { Theme } from "./theme";
import { Cursor } from "./Types";

export const caps = (props: { caps?: boolean }): { textTransform: "uppercase" } | null =>
  props.caps ? { textTransform: "uppercase" } : null;

export const regular = (props: { regular?: boolean, theme: Theme }) =>
  props.regular ? { fontWeight: props.theme.regular } : null;

export const bold = (props: { bold?: boolean, theme: Theme }) =>
  props.bold ? { fontWeight: props.theme.bold } : null;

export const italic = (props: { italic?: boolean }) => (props.italic ? { fontStyle: "italic" } : null)

export interface TextProps extends SpaceProps, TextAlignProps, FontSizeProps, ColorProps, WidthProps {
  align?: "left" | "right"
  caps?: boolean
  regular?: boolean
  italic?: boolean
  bold?: boolean
  cursor?: Cursor
}

const Text = styled.div<TextProps>`
  cursor: ${props => props.cursor};
  ${width}
  ${textStyle}
  ${fontSize}
  ${fontWeight}
  ${textAlign}
  ${lineHeight}
  ${space}
  ${color}
  ${caps}
  ${regular}
  ${bold}
  ${italic}
`;

export const TextDiv = Text;
export const TextSpan = (props) => <Text as="span" {...props} />;
export const TextP = (props) => <Text as="p" {...props} />;
export const TextS = (props) => <Text as="s" {...props} />;

interface EllipsedTextProps extends TextProps, WidthProps, MaxWidthProps { }
export const EllipsedText = styled(Text) <EllipsedTextProps>`
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  ${width};
  ${maxWidth};
  display: inline-block;
  vertical-align: bottom;
`;

Text.defaultProps = {
  cursor: "inherit"
};

export default Text;
