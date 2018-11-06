import styled from 'styled-components'
import Box, { BoxProps } from './Box'
import { NumberOrStringOrArray } from "./Types";
import { top, right, bottom, left, zIndex } from 'styled-system'

interface RelativeProps extends BoxProps {
  top?: NumberOrStringOrArray
  bottom?: NumberOrStringOrArray
  left?: NumberOrStringOrArray
  right?: NumberOrStringOrArray
  zIndex?: number | string
}

const Relative = styled(Box)<RelativeProps>`
  position: relative;
  ${top} ${bottom} ${left} ${right}
  ${zIndex}
`

Relative.displayName = "Relative";

export default Relative
