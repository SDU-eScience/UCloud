import * as React from "react";
import styled from "styled-components";
import Icon, {IconName} from "./Icon";
import {Flex} from "ui-components";
import {FontSizeProps, fontSize} from "styled-system";

const RadioTilesContainer = styled(Flex)`
  flex-wrap: nowrap;
  justify-content: center;
  align-items: center;
  overflow: hidden;
`;

const RadioTile = (props: RadioTileProps): JSX.Element => {
    const {height, label, icon, checked, disabled, fontSize, onChange, name} = props;

    return (
        <RadioTileWrap height={height} checked={checked} disabled={disabled}>
            <RadioTileInput type="radio" name={name}
                id={label} value={label}
                checked={checked} disabled={disabled}
                onChange={onChange} />
            <RadioTileIcon>
                <Icon name={icon} size={props.labeled ? "65%" : "85%"} />
                <RadioTileLabel htmlFor={label} fontSize={fontSize}>
                    {props.labeled ? label : undefined}
                </RadioTileLabel>
            </RadioTileIcon>
        </RadioTileWrap>
    );
};

interface RadioTileProps extends RadioTileWrapProps, FontSizeProps {
    label: string;
    icon: IconName;
    name: string;
    labeled?: boolean;
    onChange: (value: React.ChangeEvent<HTMLInputElement>) => void;
}

interface RadioTileWrapProps {
    height: number;
    checked: boolean;
    disabled?: boolean;
}


RadioTile.defaultProps = {
    labeled: true
};

const RadioTileIcon = styled.div`
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    border-radius: 5px;
    border: 1px solid var(--borderGray, #f00);
    color: var(--borderGray, #f00);
    transition: all 300ms ease;
`;

const RadioTileWrap = styled.div<RadioTileWrapProps>`
  position: relative;
  height:  ${props => props.height}px;
  width:  ${props => props.height}px;
  margin: 5px;
  transition: transform 300ms ease;

  &:hover {
    ${props => props.checked || props.disabled ? null :
        `
        transform: scale(1.2, 1.2);
      `
    }

  &:hover > ${RadioTileIcon} {
    ${props => props.checked || props.disabled ? null :
        `
        color: var(--blue, #f00); 
        border: 1px solid var(--blue, #f00);
      `
    };
  }
`;

const RadioTileInput = styled.input`
  opacity: 0;
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 100%;
  margin: 0;
  cursor: pointer;

  &:checked + ${RadioTileIcon} {
    background-color: var(--blue, #f00);
    border: 0px solid white;
    color: white;
    transform: scale(1.2, 1.2);
  }
`;

interface RadioTileLabelProps {
    fontSize: FontSizeProps;
}

const RadioTileLabel = styled.label<FontSizeProps>`
    text-align: center;
    ${fontSize};
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 1px;
    line-height: 1;
    padding-top: 0.1rem;
`;

export {RadioTilesContainer, RadioTile};
