import styled from "styled-components";
import { space, width, color, textAlign, SpaceProps, WidthProps, ColorProps, AlignItemsProps, TopProps, top } from "styled-system";
import theme from "./theme";


export type BoxProps = SpaceProps & WidthProps & ColorProps & AlignItemsProps & TopProps;

const Box = styled("div") <BoxProps>`
  ${space} ${width} ${color} ${textAlign} ${top};
`

Box.displayName = "Box";

Box.defaultProps = {
  theme
}

export default Box;