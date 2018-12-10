import * as React from "react"
import styled from "styled-components"
import Icon, { IconName } from "./Icon"
import Button, { ButtonProps } from "./Button"
import theme from "./theme"

export interface IconButtonProps extends ButtonProps {
  name: IconName
  size?: number | string
  color?: string
  onClick?: (e?: React.SyntheticEvent<HTMLButtonElement>) => void
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

const IconButton = ({ name, size, ...props }: IconButtonProps) => (
  <TransparentButton {...props}>
    <Icon name={name} size={size} />
  </TransparentButton>
);

IconButton.displayName = "IconButton";

IconButton.defaultProps = {
  theme: theme
}

export default IconButton;
