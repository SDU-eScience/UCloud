import styled from "styled-components";
import {
  textStyle,
  fontSize,
  fontWeight,
  textAlign,
  lineHeight,
  space,
  color, 
  SpaceProps
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
interface TextProps extends SpaceProps {
  fontSize?: number | string
  align?: TextAlign
  caps?: boolean
  regular?: boolean
  italic?: boolean
  color?: string
  bold?: boolean
  mx?: string | number
  ml?: string | number
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
export const span = Text.withComponent("span");
export const p = Text.withComponent("p");
export const s = Text.withComponent("s");

Text.defaultProps = {
  theme: theme
};

export default Text;
