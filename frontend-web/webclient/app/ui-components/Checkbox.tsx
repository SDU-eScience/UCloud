import * as React from "react";
import styled from "styled-components";
import {Box, Icon} from ".";
import {BoxProps} from "./Box";
import theme from "./theme";

interface CheckboxProps extends React.InputHTMLAttributes<HTMLInputElement> {
  disabled?: boolean;
}

function Checkbox(props: CheckboxProps) {
  const {disabled, size} = props;
  return (
    <CheckBoxWrapper disabled={!!disabled}>
      <StyledInput type="checkbox" {...props} />
      <Icon name="boxChecked" size={size} data-name="checked" />
      <Icon name="boxEmpty" size={size} data-name="empty" />
    </CheckBoxWrapper>
  );
}

interface CheckBoxWrapper extends BoxProps {
  disabled: boolean;
}

const CheckBoxWrapper = styled(Box) <CheckBoxWrapper>`
  display: inline-block;
  position: relative;
  vertical-align: middle;
  cursor: pointer;
  color: ${props => props.disabled ? props.theme.colors.borderGray : props.theme.colors.gray};
  svg[data-name="checked"] {
    display: none;
  }
  > input:checked {
    & ~ svg[data-name="checked"] {
      display: inline-block;
      color: ${props => props.disabled
    ? props.theme.colors.borderGray
    : props.theme.colors.blue};
    }
    & ~ svg[data-name="empty"] {
      display: none;
    }
  }
`;

const StyledInput = styled.input`
  appearance: none;
  opacity: 0;
  position: absolute;
`;

Checkbox.displayName = "Checkbox";

Checkbox.defaultProps = {
  size: 20,
  checked: false,
  disabled: false,
  theme
};

export default Checkbox;
