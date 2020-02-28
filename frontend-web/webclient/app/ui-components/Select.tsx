import * as React from "react";
import styled, {css} from "styled-components";
import {fontSize, space, SpaceProps, WidthProps} from "styled-system";
import Flex from "./Flex";
import Icon from "./Icon";
import theme from "./theme";

const ClickableIcon = styled(Icon)`
  pointer-events: none;
`;

const left = ({leftLabel}: {leftLabel?: boolean}) =>
    leftLabel ? css`border-top-left-radius: 0; border-bottom-left-radius: 0;` : "";
const right = ({rightLabel}: {rightLabel?: boolean}) =>
    rightLabel ? css`border-top-right-radius: 0; border-bottom-right-radius: 0;` : "";


interface SelectProps extends SpaceProps, WidthProps {
    fontSize?: number | string;
    leftLabel?: boolean;
    rightLabel?: boolean;
    showError?: boolean;
}

const SelectBase = styled.select<SelectProps>`
  appearance: none;
  display: block;
  width: 100%;
  font-family: inherit;
  color: inherit;

  & > option {
    color: black;
  }

  ${p => p.showError ? `&:invalid {
    border-color: var(--red, #f00);
  }` : null}

  background-color: transparent;
  border-radius: ${theme.radius};
  border-width: ${p => p.theme.borderWidth};
  border-style: solid;
  border-color: var(--borderGray, #f00);

  &:focus {
    outline: none;
    border-color: var(--blue, #f00);
  }

  ${space} ${fontSize}
  ${left} ${right}
`;

SelectBase.defaultProps = {
    m: 0,
    pl: 12,
    pr: 32,
    py: 7
};

type Props = SelectProps &
    React.SelectHTMLAttributes<HTMLSelectElement> &
{selectRef?: React.RefObject<HTMLSelectElement>};

const Select = styled((props: Props) => (
    <Flex width={1} alignItems="center">
        <SelectBase {...props} ref={props.selectRef} />
        <ClickableIcon ml={-32} name="chevronDown" color="gray" size="0.7em" />
    </Flex>
))``;

export default Select;
