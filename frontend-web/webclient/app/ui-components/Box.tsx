import styled from "styled-components";
import { space, width, minWidth, color, textAlign, SpaceProps, WidthProps, MinWidthProps, ColorProps, AlignItemsProps, TopProps, minHeight, MinHeightProps, HeightProps, height, MaxHeightProps, MaxWidthProps, maxWidth, maxHeight, TextAlignProps } from "styled-system";
import theme from "./theme";

export type BoxProps =
  SpaceProps &
  WidthProps &
  MinWidthProps &
  ColorProps &
  AlignItemsProps &
  TopProps &
  HeightProps &
  MinHeightProps &
  MaxHeightProps &
  MaxWidthProps &
  FlexGrowProps &
  FlexShrinkProps &
  TextAlignProps;

interface FlexGrowProps {
  flexGrow?: number
}

interface FlexShrinkProps {
  flexShrink?: number;
}

const flexGrow = ({ flexGrow }: FlexGrowProps) => flexGrow ? { flexGrow } : null;
const flexShrink = ({ flexShrink }: FlexShrinkProps) => flexShrink ? { flexShrink } : null;

const Box = styled.div<BoxProps>`
  ${flexGrow}
  ${flexShrink}
  ${space} 
  ${width} 
  ${minWidth} 
  ${maxWidth} 
  ${height} 
  ${minHeight} 
  ${maxHeight} 
  ${color} 
  ${textAlign};
`

Box.displayName = "Box";

Box.defaultProps = {
  theme
}

export default Box;