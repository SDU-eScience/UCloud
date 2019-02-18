import styled from "styled-components";
import Box, { BoxProps } from "./Box";
import { top, right, bottom, left, zIndex, TopProps, BottomProps, LeftProps, RightProps, ZIndexProps } from "styled-system";

interface AbsoluteProps extends BoxProps, TopProps, BottomProps, LeftProps, RightProps, ZIndexProps {}

const Absolute = styled(Box)<AbsoluteProps>`
  position: absolute;
  ${top} ${bottom} ${left} ${right}
  ${zIndex}
`;

Absolute.displayName = "Absolute";

export default Absolute
