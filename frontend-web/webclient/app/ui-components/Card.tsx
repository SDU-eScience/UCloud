import * as React from "react";
import styled from "styled-components";
import Box, { BoxProps } from "./Box";
import theme from "./theme";
import { borderRadius, 
         BorderProps, 
         BorderRadiusProps, 
         BorderColorProps, 
         HeightProps, 
         height,
         boxShadow,
         BoxShadowProps,         
        } from "styled-system";
import Icon from "./Icon";

const boxBorder = props => ({
  border: `${props.borderWidth}px solid ${props.theme.colors[props.borderColor]}`
});

export interface CardProps extends HeightProps, BoxProps, BorderColorProps, BoxShadowProps, BorderProps, BorderRadiusProps {
  borderWidth?: number | string
}

export const Card = styled(Box) <CardProps>`
  ${height} ${boxShadow} ${boxBorder} ${borderRadius};
`;

// FIXME: Workaround, not a fix.
// @ts-ignore
Card.defaultProps = {
  borderColor: "borderGray",
  borderRadius: 1,
  borderWidth: 1,
  theme: theme
};

export const CardGroup = styled.div`
  display: flex;
  flex-wrap: wrap;
  flex-grow: 0;
  margin: -5px -5px -5px -5px;

  & > * {
    margin: 5px 5px 5px 5px;
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
