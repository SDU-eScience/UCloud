import styled from "styled-components";
import {
  textStyle,
  fontSize,
  fontWeight,
  textAlign,
  lineHeight,
  space,
  color,
  SpaceProps, TextAlignProps, FontSizeProps, ColorProps, alignContent
} from 'styled-system'
import theme from "./theme";
import { TextAlign } from "./Types";

export const caps = props =>
  props.caps
    ? {
      textTransform: "uppercase"
    }
    : null

export const regular = props =>
  props.regular ? { fontWeight: props.theme.regular } : null

export const bold = props =>
  props.bold ? { fontWeight: props.theme.bold } : null

export const italic = props => (props.italic ? { fontStyle: 'italic' } : null)

interface TextProps extends SpaceProps, TextAlignProps, FontSizeProps, ColorProps {
  align?: "left" | "right"
  caps?: boolean
  regular?: boolean
  italic?: boolean
  bold?: boolean
  cursor?: string
}

const Text = styled("div") <TextProps>`
  cursor: ${props => props.cursor};
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
export const div = Text;
export const TextSpan = Text.withComponent("span");
export const TextP = Text.withComponent("p");
export const TextS = Text.withComponent("s");

interface EllipsedTextProps extends TextProps { width: number }
export const EllipsedText = styled(Text) <EllipsedTextProps>`
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  width: ${props => props.width}px;
  display: inline-block;
`;


Text.defaultProps = {
  cursor: "auto",
  theme: theme
};

export default Text;
