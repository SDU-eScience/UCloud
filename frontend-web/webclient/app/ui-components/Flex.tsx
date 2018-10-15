import styled from 'styled-components'
import {
  space,
  width,
  color,
  alignItems,
  justifyContent,
  flexWrap,
  flexDirection,
  SpaceProps,
  WidthProps,
  ColorProps,
  AlignItemsProps,
  JustifyContentProps,
  FlexWrapProps,
  FlexDirectionProps
} from "styled-system";
import theme from "./theme"

const Flex = styled<FlexProps, "div">("div")`
  display: flex;
  ${space} ${width} ${color} ${alignItems} ${justifyContent} ${flexWrap} ${flexDirection};
`

Flex.defaultProps = {
  theme
}

interface FlexProps extends SpaceProps, WidthProps, ColorProps, AlignItemsProps, JustifyContentProps, FlexWrapProps, FlexDirectionProps {}

Flex.displayName = 'Flex'

export default Flex
