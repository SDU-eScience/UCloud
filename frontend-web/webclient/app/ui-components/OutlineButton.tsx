import styled from "styled-components";
import {ButtonStyleProps} from "styled-system";
import Button from "./Button";
import theme, {Theme} from "./theme";

export interface OutlineButtonProps extends ButtonStyleProps {hovercolor?: string;}

// Different from the one in button because of border size
const size = (p: {size: string, theme: Theme}) => {
    switch (p.size) {
        case "tiny":
            return {
                fontSize: `${p.theme.fontSizes[0]}px`,
                padding: "3px 10px"
            };
        case "small":
            return {
                fontSize: `${p.theme.fontSizes[0]}px`,
                padding: "5px 12px"
            };
        case "medium":
            return {
                fontSize: `${p.theme.fontSizes[1]}px`,
                padding: "7.5px 18px"
            };
        case "large":
            return {
                fontSize: `${p.theme.fontSizes[2]}px`,
                padding: "10px 22px"
            };
        default:
            return {
                fontSize: `${p.theme.fontSizes[1]}px`,
                padding: "7.5px 18px"
            };
    }
};

const OutlineButton = styled(Button) <OutlineButtonProps>`
    color: var(--${p => p.color ?? "blue"}, #f00);
    border: 2px solid var(--${p => p.color ?? "blue"}, #f00);
    border-radius: ${theme.radius};
    background-color: transparent;

    &:hover {
        color: ${p => (p.disabled ? null : (p.hovercolor ? `var(--${p.hovercolor}, #f00)` : null))};
        border: 2px solid ${p => p.hovercolor ? `var(--${p.hovercolor}, #f00)` : null};
        background-color: transparent;
        transition: ease 0.1s;
    }

    ${size}
`;

OutlineButton.displayName = "OutlineButton";

export default OutlineButton;
