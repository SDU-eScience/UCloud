import * as React from "react"
import styled from "styled-components"
import Icon, { IconName } from "./Icon"
import Button from "./Button"
import theme from "./theme"
import { ButtonStyleProps } from "styled-system";

export interface IconbuttonProps extends ButtonStyleProps {
  name: IconName
  size: number | string
  color: string
}

const TransparentButton = styled(Button)`
  padding: 0;
  height: auto;
  background-color: transparent;
  color: inherit;

  &:hover {
    background-color: transparent;
  }
`;

const IconButton = ({ name, size, color, ...props }: IconbuttonProps) => (
  <TransparentButton {...props}>
    <Icon name={name} size={size} color={color} />
  </TransparentButton>
);

IconButton.displayName = "IconButton";

IconButton.defaultProps = {
  theme: theme
}

export default IconButton;
