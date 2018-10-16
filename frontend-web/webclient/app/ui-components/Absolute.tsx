import styled from 'styled-components'
import Box, { BoxProps } from './Box'
import { style, responsiveStyle } from "styled-system";
import { NumberOrStringOrArray } from "./Types";

const top = responsiveStyle({
  prop: 'top',
  cssProperty: 'top',
  numberToPx: true
})

const bottom = responsiveStyle({
  prop: 'bottom',
  cssProperty: 'bottom',
  numberToPx: true
})

const left = responsiveStyle({
  prop: 'left',
  cssProperty: 'left',
  numberToPx: true
})

const right = responsiveStyle({
  prop: 'right',
  cssProperty: 'right',
  numberToPx: true
})

const zIndex = style({
  prop: 'zIndex',
  cssProperty: 'z-index',
  numberToPx: true
})

interface AbsoluteProps extends BoxProps {
  top?: NumberOrStringOrArray
  bottom?: NumberOrStringOrArray
  left?: NumberOrStringOrArray
  right?: NumberOrStringOrArray
  zIndex?: string | number
}

const Absolute = styled<{}, AbsoluteProps>(Box)`
  position: absolute;
  ${top} ${bottom} ${left} ${right}
  ${zIndex}
`

export default Absolute
