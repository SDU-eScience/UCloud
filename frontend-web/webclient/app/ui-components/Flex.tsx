import styled from "styled-components";
import {
    alignItems, AlignItemsProps, color, ColorProps,
    flex, flexDirection, FlexDirectionProps, flexGrow, FlexGrowProps, FlexProps, flexShrink, FlexShrinkProps,
    flexWrap, FlexWrapProps, height, HeightProps,
    justifyContent, JustifyContentProps, maxWidth,
    MaxWidthProps, minWidth, MinWidthProps, space,
    SpaceProps, width, WidthProps, zIndex, ZIndexProps
} from "styled-system";
import {cursor} from "Utilities/StyledComponentsUtilities";
import {Cursor} from "./Types";


export type FlexCProps =
    SpaceProps &
    WidthProps &
    MinWidthProps &
    MaxWidthProps &
    HeightProps &
    ColorProps &
    AlignItemsProps &
    JustifyContentProps &
    FlexWrapProps &
    FlexGrowProps &
    FlexShrinkProps &
    FlexDirectionProps &
    FlexProps &
    ZIndexProps &
    {cursor?: Cursor};


const Flex = styled.div<FlexCProps>`
  ${cursor}
  display: flex;
  ${space} ${width} ${minWidth} ${maxWidth} ${height} ${zIndex}
  ${color} ${alignItems} ${justifyContent}
  ${flexDirection} ${flexWrap} ${flex} ${flexGrow} ${flexShrink}
`;


Flex.displayName = "Flex";

export default Flex;
