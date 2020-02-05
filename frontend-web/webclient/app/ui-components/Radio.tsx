import * as React from "react";
import styled from "styled-components";
import Icon from "./Icon";

const Radio = (props: RadioWrapProps & {onChange: (e: React.ChangeEvent<HTMLInputElement>) => void}): JSX.Element => {
    const {checked, disabled} = props;

    const radioIconName = checked ? "radioChecked" : "radioEmpty";

    return (
        <RadioWrap checked={checked} disabled={disabled}>
            <RadioInput type="radio" {...props} />
            <RadioIcon name={radioIconName} size={24} mr={".5em"} />
        </RadioWrap>
    );
};

interface RadioWrapProps {
    checked: boolean;
    disabled?: boolean;
}

const RadioWrap = styled.div<RadioWrapProps>`
    display: inline-block;
    color: var(--borderGray, #f00);
    &:hover {
      ${props => props.checked || props.disabled ? null : `color: var(--blue, #f00);`};
    }
`;

const RadioInput = styled.input`
    appearance: none;
    opacity: 0;
    position: absolute;
    z-index: 0;
    &:focus {
      box-shadow: none;
    }
    &:checked ~ svg {
      color: var(--blue, #f00);
    }
    &:disabled ~ svg {
      color: var(--borderGray, #f00);
    }
`;

const RadioIcon = styled(Icon)`
    vertical-align: middle;
`;

export default Radio;
