import styled from 'styled-components';
import { fontSize, space, color, responsiveStyle, SpaceProps } from 'styled-system';
import theme from './theme';

export const caps = props =>
  props.caps
    ? {
      textTransform: 'uppercase'
    }
    : null

export const regular = props =>
  props.regular ? { fontWeight: props.theme.regular } : null

export const bold = props =>
  props.bold ? { fontWeight: props.theme.bold } : null

export const italic = props => (props.italic ? { fontStyle: 'italic' } : null)

// FIXME Consqeuence of updated Style-system?
//const align = responsiveStyle('text-align', 'align')
const align = "";
interface TextProps extends SpaceProps {
  fontSize?: any
  align?: "left" | "center" | "right" | "justify"
  caps?: boolean
  regular?: boolean
  italic?: boolean
  color?: string
  bold?: boolean
}

const Text = styled<TextProps, "div">("div")`
  ${italic} ${fontSize} ${space} ${color} ${caps} ${regular} ${bold} ${align};
`

Text.defaultProps = {
  theme: theme
}

export default Text
