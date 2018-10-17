import styled from 'styled-components'
import { space, width, color, SpaceProps, textAlign } from 'styled-system'
import { NumberOrStringOrArray } from "./Types";
import theme from './theme'


export interface BoxProps extends SpaceProps {
  color?: string
  bg?: string
  width?: NumberOrStringOrArray
  w?: NumberOrStringOrArray
  align?: any
  mr?: any
  ml?: any
  mt?: any
}

const Box = styled("div")<BoxProps>`
  ${space} ${width} ${color} ${textAlign};
`

Box.displayName = 'Box'

Box.defaultProps = {
  theme: theme
}

export default Box
