import styled from "styled-components";
import {bottom, left, right, top, TopProps, zIndex} from "styled-system";
import Box, {BoxProps} from "./Box";
import {NumberOrStringOrArray} from "./Types";

interface RelativeProps extends BoxProps, TopProps {
  bottom?: NumberOrStringOrArray;
  left?: NumberOrStringOrArray;
  right?: NumberOrStringOrArray;
}

const Relative = styled(Box) <RelativeProps>`
  position: relative;
  ${top} ${bottom} ${left} ${right}
  ${zIndex}
`;

Relative.displayName = "Relative";

export default Relative;
