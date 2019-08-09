import * as React from "react";
import styled from "styled-components";
import Box, {BoxProps} from "./Box";
import theme, {Theme} from "./theme";
import {
  borderRadius,
  BorderProps,
  BorderRadiusProps,
  BorderColorProps,
  HeightProps,
  height,
  boxShadow,
  BoxShadowProps,
} from "styled-system";
import Icon from "./Icon";

const boxBorder = (props: {theme: Theme, borderWidth: number | string, borderColor: string}) => ({
  border: `${props.borderWidth}px solid ${props.theme.colors[props.borderColor]}`
});

export interface CardProps extends HeightProps, BoxProps, BorderColorProps, BoxShadowProps, BorderProps, BorderRadiusProps {
  borderWidth?: number | string
}

export const Card = styled(Box) <CardProps>`
  ${height} ${boxShadow} ${boxBorder} ${borderRadius};
`;

Card.defaultProps = {
  borderColor: "borderGray",
  borderRadius: 1,
  borderWidth: 1,
  theme: theme
};

export const PlayIconBase = styled(Icon)`
  transition: ease 0.3s;

  &:hover {
    filter: saturate(5);
    transition: ease 0.3s;
  }
`;

Card.displayName = "Card";

export default Card;
