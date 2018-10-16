import styled from 'styled-components'
import { NumberOrStringOrArray } from "./Types";
import Box, { BoxProps } from './Box'
import { responsiveStyle, style } from 'styled-system'

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
  numberToPx: false
})

interface RelativeProps extends BoxProps {
  top?: NumberOrStringOrArray
  bottom?: NumberOrStringOrArray
  left?: NumberOrStringOrArray
  right?: NumberOrStringOrArray
  zIndex?: number | string
}

const Relative = styled<{}, RelativeProps>(Box)`
  position: relative;
  ${top} ${bottom} ${left} ${right}
  ${zIndex}
`;

Relative.displayName = 'Relative'

export default Relative