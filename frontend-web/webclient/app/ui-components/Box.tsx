import styled from "styled-components";
import { space, width, color, textAlign, SpaceProps, WidthProps, ColorProps, AlignItemsProps } from "styled-system";
import theme from "./theme";


export interface BoxProps extends SpaceProps,
  WidthProps,
  ColorProps,
  AlignItemsProps { }

const Box = styled("div") <BoxProps>`
  ${space} ${width} ${color} ${textAlign};
`

Box.displayName = "Box";

Box.defaultProps = {
  theme
}

export default Box;