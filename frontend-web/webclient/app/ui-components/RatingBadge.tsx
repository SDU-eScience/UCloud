import styled from "styled-components";
import {
  borderRadius,
  BorderRadiusProps,
  fontWeight,
  FontWeightProps
} from "styled-system";
import Box, {BoxProps} from "./Box";
import theme from "./theme";

export type RatingBadgeProps = BoxProps & FontWeightProps & BorderRadiusProps;

const RatingBadge = styled(Box) <RatingBadgeProps>`
  display: inline-block;
  line-height: 1.5;
  ${fontWeight} ${borderRadius};
`;

RatingBadge.defaultProps = {
  fontWeight: "bold",
  px: 2,
  color: "white",
  bg: "blue",
  borderRadius: 1,
  theme
};

RatingBadge.displayName = "RatingBadge";

export default RatingBadge;
