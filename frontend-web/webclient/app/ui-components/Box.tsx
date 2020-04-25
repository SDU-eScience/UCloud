import styled from "styled-components";
import {
  AlignItemsProps, color, ColorProps, height, HeightProps,
  maxHeight, MaxHeightProps, maxWidth, MaxWidthProps,
  minHeight, MinHeightProps, minWidth, MinWidthProps,
  overflow, OverflowProps, space, SpaceProps,
  textAlign, TextAlignProps, TopProps, width, WidthProps,
  zIndex, ZIndexProps, background, BackgroundProps
} from "styled-system";
import {cursor} from "Utilities/StyledComponentsUtilities";
import {Cursor} from "./Types";

export type BoxProps =
  SpaceProps &
  WidthProps &
  MinWidthProps &
  ColorProps &
  BackgroundProps &
  AlignItemsProps &
  TopProps &
  HeightProps &
  MinHeightProps &
  MaxHeightProps &
  MaxWidthProps &
  FlexGrowProps &
  FlexShrinkProps &
  ZIndexProps &
  TextAlignProps &
  OverflowProps &
  {cursor?: Cursor};

interface FlexGrowProps {
  flexGrow?: number;
}

interface FlexShrinkProps {
  flexShrink?: number;
}

const useFlexGrow = ({flexGrow}: FlexGrowProps) => flexGrow ? {flexGrow} : null;
const useFlexShrink = ({flexShrink}: FlexShrinkProps) => flexShrink ? {flexShrink} : null;

const Box = styled.div<BoxProps>`
  ${cursor}
  ${zIndex}
  ${useFlexGrow}
  ${useFlexShrink}
  ${space}
  ${width}
  ${minWidth}
  ${maxWidth}
  ${height}
  ${minHeight}
  ${maxHeight}
  ${color}
  ${textAlign}
  ${overflow}
  ${background}
`;

Box.displayName = "Box";

export default Box;
