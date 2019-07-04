import styled from 'styled-components';
import {
  fontWeight,
  borderRadius, 
  FontWeightProps, 
  BorderRadiusProps
} from 'styled-system';
import Box, { BoxProps } from './Box';
import theme from './theme';

export interface RatingBadgeProps extends BoxProps, FontWeightProps, 
    BorderRadiusProps {
}

const RatingBadge = styled(Box)<RatingBadgeProps>`
  display: inline-block;
  line-height: 1.5;
  ${fontWeight} ${borderRadius};
`;

RatingBadge.defaultProps = {
  fontWeight: 'bold',
  px: 2,
  color: 'white',
  bg: 'blue',
  borderRadius: 1,
  theme: theme
};

RatingBadge.displayName = "RatingBadge";

export default RatingBadge;
