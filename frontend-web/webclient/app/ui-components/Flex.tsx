import styled from 'styled-components'
import {
  space,
  width, minWidth,
  height,
  color,
  alignItems,
  justifyContent,
  flexWrap, flexDirection, flex,
  SpaceProps, WidthProps, MinWidthProps, HeightProps, 
  ColorProps, AlignItemsProps, JustifyContentProps, 
  FlexWrapProps, FlexDirectionProps, FlexProps
} from 'styled-system'
import theme from "./theme"


export type FlexCProps =
  SpaceProps &
  WidthProps &
  MinWidthProps &
  HeightProps &
  ColorProps &
  AlignItemsProps &
  JustifyContentProps &
  FlexWrapProps &
  FlexDirectionProps &
  FlexProps &
  { cursor?: string }


const Flex = styled.div<FlexCProps>`
  cursor: ${props => props.cursor};
  display: flex;
  ${space} ${width} ${minWidth} ${height} 
  ${color} ${alignItems} ${justifyContent}
  ${flexDirection} ${flexWrap} ${flex}
`

Flex.defaultProps = {
  theme,
  cursor: "inherit"
}


Flex.displayName = 'Flex'

export default Flex


/*
import styled from 'styled-components'
import * as StyledSystem from "styled-system";
import theme from "./theme"


interface FlexProps extends StyledSystem.SpaceProps, StyledSystem.WidthProps, StyledSystem.ColorProps,
  StyledSystem.AlignItemsProps, StyledSystem.JustifyContentProps, StyledSystem.FlexWrapProps, StyledSystem.FlexDirectionProps {
    align?: any
    width?: any
    mt?: any
    mr?: any
    ml?: any
    children?: any
    justify?: any
    bg?: any
}


const Flex = styled("div")<FlexProps>`
  display: flex;
  ${StyledSystem.space} ${StyledSystem.width} ${StyledSystem.color} ${StyledSystem.alignItems} ${StyledSystem.justifyContent} ${StyledSystem.flexWrap} ${StyledSystem.flexDirection};
`

Flex.defaultProps = {
  theme
}


Flex.displayName = 'Flex'

export default Flex
*/