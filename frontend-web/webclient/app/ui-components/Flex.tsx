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
  FlexWrapProps, FlexDirectionProps, FlexProps, ZIndexProps, zIndex
} from 'styled-system'
import theme from "./theme"
import { Cursor } from './Types';


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
  ZIndexProps &
  { cursor?: Cursor }


const Flex = styled.div<FlexCProps>`
  cursor: ${props => props.cursor};
  display: flex;
  ${space} ${width} ${minWidth} ${height} ${zIndex}
  ${color} ${alignItems} ${justifyContent}
  ${flexDirection} ${flexWrap} ${flex}
`;

Flex.defaultProps = {
  theme,
  cursor: "inherit"
}


Flex.displayName = "Flex";

export default Flex;