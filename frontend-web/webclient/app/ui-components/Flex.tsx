import styled from "styled-components";
import {
  alignItems, AlignItemsProps, color, ColorProps,
  flex, flexDirection, FlexDirectionProps, FlexProps,
  flexWrap, FlexWrapProps, height, HeightProps,
  justifyContent, JustifyContentProps, minWidth,
  MinWidthProps, space, SpaceProps, width, WidthProps,
  zIndex, ZIndexProps
} from "styled-system";
import theme from "./theme";
import {Cursor} from "./Types";


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
  {cursor?: Cursor;};


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
};


Flex.displayName = "Flex";

export default Flex;
