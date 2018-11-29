import styled from "styled-components";
import { space, width, minWidth, color, textAlign, SpaceProps, WidthProps, MinWidthProps, ColorProps, AlignItemsProps, TopProps, top, minHeight, MinHeightProps, HeightProps, height, MaxHeightProps, MaxWidthProps, maxWidth, maxHeight } from "styled-system";
import theme from "./theme";


export type BoxProps = SpaceProps & WidthProps & MinWidthProps & ColorProps & AlignItemsProps & TopProps & HeightProps & MinHeightProps & MaxHeightProps & MaxWidthProps;

const Box = styled("div") <BoxProps>`
  ${space} ${width} ${minWidth} ${maxWidth} ${height} ${minHeight} ${maxHeight} ${color} ${textAlign} ${top};
`

Box.displayName = "Box";

Box.defaultProps = {
  theme
}

export default Box;