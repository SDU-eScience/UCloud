import styled from "styled-components";
import {
  bottom,
  BottomProps,
  left,
  LeftProps,
  right,
  RightProps,
  top,
  TopProps,
  zIndex,
  ZIndexProps
} from "styled-system";
import Box, {BoxProps} from "./Box";

interface AbsoluteProps extends BoxProps, TopProps, BottomProps, LeftProps, RightProps, ZIndexProps {
}

const Absolute = styled(Box) <AbsoluteProps>`
  position: absolute;
  ${top} ${bottom} ${left} ${right}
  ${zIndex}
`;

Absolute.displayName = "Absolute";

export default Absolute;
