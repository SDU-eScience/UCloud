import styled from "styled-components";
import {
  BorderColorProps,
  BorderProps,
  borderRadius,
  BorderRadiusProps,
  boxShadow,
  BoxShadowProps,
  height,
  HeightProps
} from "styled-system";
import Box, {BoxProps} from "./Box";
import Icon from "./Icon";
import theme, {Theme} from "./theme";

const boxBorder = (props: {theme: Theme, borderWidth: number | string, borderColor: string}) => ({
  border: `${props.borderWidth}px solid ${props.theme.colors[props.borderColor]}`
});

export interface CardProps extends
  HeightProps,
  BoxProps,
  BorderColorProps,
  BoxShadowProps,
  BorderProps,
  BorderRadiusProps {
  borderWidth?: number | string;
}

export const Card = styled(Box) <CardProps>`
  ${height} ${boxShadow} ${boxBorder} ${borderRadius};
`;

Card.defaultProps = {
  borderColor: "borderGray",
  borderRadius: 1,
  borderWidth: 1,
  theme
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
