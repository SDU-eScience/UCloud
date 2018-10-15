import styled from 'styled-components';
import { fontSize, space, color, responsiveStyle } from 'styled-system';
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

const align = responsiveStyle('text-align', 'align')

interface TextProps {
  fontSize?: any
  align?: "left" | "center" | "right" | "justify"
  caps?: boolean
  regular?: boolean
  italic?: boolean
  color?: string
  bold?: boolean

  m?: number | string | any[]
  mt?: number | string | any[]
  mr?: number | string | any[]
  mb?: number | string | any[]
  ml?: number | string | any[]
  mx?: number | string | any[]
  my?: number | string | any[]

  /** Padding */
  p?: number | string | any[]
  pt?: number | string | any[]
  pr?: number | string | any[]
  pb?: number | string | any[]
  pl?: number | string | any[]
  px?: number | string | any[]
  py?: number | string | any[]
}

const Text = styled<TextProps, "div">("div")`
  ${italic} ${fontSize} ${space} ${color} ${caps} ${regular} ${bold} ${align};
`

Text.defaultProps = {
  theme: theme
}

export default Text
