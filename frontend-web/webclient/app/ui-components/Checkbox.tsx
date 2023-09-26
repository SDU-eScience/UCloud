import * as React from "react";
import styled from "styled-components";
import {Box, Icon} from ".";
import {BoxProps} from "./Box";

interface CheckboxProps extends React.InputHTMLAttributes<HTMLInputElement> {
    disabled?: boolean;
    handleWrapperClick?: () => void;
}

function Checkbox(props: CheckboxProps): JSX.Element {
    const {disabled, size} = props;
    return (
        <CheckBoxWrapper disabled={!!disabled} onClick={props.handleWrapperClick}>
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
  color: var(--${props => props.disabled ? "borderGray" : "gray"}, #f00);
  margin-right: .5em;
  svg[data-name="checked"] {
    display: none;
  }
  > input:checked {
    & ~ svg[data-name="checked"] {
      display: inline-block;
      color: var(--${props => props.disabled
        ? "borderGray"
        : "blue"}, #f00);
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
    disabled: false

    // NOTE(Dan): The line below has been removed since it forced all checkboxes to be controlled even when this
    // wasn't the intended behavior.
    // checked: false,
};

export default Checkbox;
