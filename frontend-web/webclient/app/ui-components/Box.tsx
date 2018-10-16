import styled from 'styled-components'
import { space, width, color, SpaceProps } from 'styled-system'
import { NumberOrStringOrArray } from "./Types";
import theme from './theme'

//const align = responsiveStyle('text-align', 'align')

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

const Box = styled<BoxProps, "div">("div")`
  ${space} ${width} ${color};
`
//${align}

Box.displayName = 'Box'

Box.defaultProps = {
  theme: theme
}

export default Box
