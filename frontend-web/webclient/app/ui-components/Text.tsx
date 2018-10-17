import styled from "styled-components";
import {
  textStyle,
  fontSize,
  fontWeight,
  textAlign,
  lineHeight,
  space,
  color, 
  SpaceProps, TextAlignProps, FontSizeProps, ColorProps
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

// FIXME Consqeuence of updated Style-system?
//const align = style('text-align', 'align')
const align = "";
interface TextProps extends SpaceProps, TextAlignProps, FontSizeProps, ColorProps {
  caps?: boolean
  regular?: boolean
  italic?: boolean
  bold?: boolean
}

const Text = styled("div")<TextProps>`
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
  `
export const div = Text;
export const TextSpan = Text.withComponent("span");
export const TextP = Text.withComponent("p");
export const TextS = Text.withComponent("s");

Text.defaultProps = {
  theme: theme
};

export default Text;
