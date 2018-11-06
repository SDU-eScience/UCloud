import styled from 'styled-components'
import Box, { BoxProps } from './Box'
import { NumberOrStringOrArray } from "./Types";
import { top, right, bottom, left, zIndex } from "styled-system";

interface AbsoluteProps extends BoxProps {
  top?: NumberOrStringOrArray
  bottom?: NumberOrStringOrArray
  left?: NumberOrStringOrArray
  right?: NumberOrStringOrArray
  zIndex?: string | number
}

const Absolute = styled(Box)<AbsoluteProps>`
  position: absolute;
  ${top} ${bottom} ${left} ${right}
  ${zIndex}
`

Absolute.displayName = "Absolute";

export default Absolute
