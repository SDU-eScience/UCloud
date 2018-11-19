import styled from "styled-components";
import { space, width, minWidth, color, textAlign, SpaceProps, WidthProps, MinWidthProps, ColorProps, AlignItemsProps, TopProps, top } from "styled-system";
import theme from "./theme";


export type BoxProps = SpaceProps & WidthProps & MinWidthProps & ColorProps & AlignItemsProps & TopProps;

const Box = styled("div") <BoxProps>`
  ${space} ${width} ${minWidth} ${color} ${textAlign} ${top};
`

Box.displayName = "Box";

Box.defaultProps = {
  theme
}

export default Box;