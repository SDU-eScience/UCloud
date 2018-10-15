import styled from 'styled-components'
import { space, width, color, responsiveStyle } from 'styled-system'
import { NumberOrStringOrArray } from "./Types";
import theme from './theme'

//const align = responsiveStyle('text-align', 'align')

interface BoxProps {
  color?: string
  bg?: string
  width?: NumberOrStringOrArray
  w?: NumberOrStringOrArray
  
  /** Margin */
  m?: NumberOrStringOrArray
  mt?: NumberOrStringOrArray
  mr?: NumberOrStringOrArray
  mb?: NumberOrStringOrArray
  ml?: NumberOrStringOrArray
  mx?: NumberOrStringOrArray
  my?: NumberOrStringOrArray

  /** Padding */
  p?: NumberOrStringOrArray
  pt?: NumberOrStringOrArray
  pr?: NumberOrStringOrArray
  pb?: NumberOrStringOrArray
  pl?: NumberOrStringOrArray
  px?: NumberOrStringOrArray
  py?: NumberOrStringOrArray
}

const Box = styled<BoxProps, "div">("div")`
  ${space} ${width} ${color};
`

Box.displayName = 'Box'

Box.defaultProps = {
  theme: theme
}

export default Box
