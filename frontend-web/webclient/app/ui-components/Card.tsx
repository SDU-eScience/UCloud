import * as React from "react";
import styled from "styled-components";
import Box, { BoxProps } from "./Box";
import theme from "./theme";
import { borderRadius, BoxShadowProps, BorderProps, BorderRadiusProps, BorderColorProps, HeightProps, height } from "styled-system";
import Icon from "./Icon";

const boxShadow = props => {
  const boxShadows = {
    sm: {
      "box-shadow": props.theme.boxShadows[0]
    },
    md: {
      "box-shadow": props.theme.boxShadows[1]
    },
    lg: {
      "box-shadow": props.theme.boxShadows[2]
    },
    xl: {
      "box-shadow": props.theme.boxShadows[3]
    }
  }
  return boxShadows[props.boxShadowSize]
}

const boxBorder = props => ({
  border: `${props.borderWidth}px solid ${
    props.theme.colors[props.borderColor]
    }`
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
  theme: theme,
  height: 336.8
};

export const CardGroup = styled.div`
  display: flex;
  flex-wrap: wrap;
  & > div {
    margin: 5px 5px 5px 5px;
    height: 212px;
    width: 252px;
    flex-shrink: 0;
  }
`;

export const PlayIconBase = styled(Icon)`
  transition: ease 0.3s;

  &:hover {
    filter: saturate(5);
    transition: ease 0.3s;
  }
`;

export const PlayIcon = () => (<PlayIconBase cursor="pointer" name="play" size={38} />);

Card.displayName = "Card";

export default Card;
