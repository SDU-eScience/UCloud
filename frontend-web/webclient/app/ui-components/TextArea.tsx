import styled from "styled-components";
import {margin, MarginProps, padding, PaddingProps, width} from "styled-system";
import {borders} from "./Input";
import theme from "./theme";

export const TextArea = styled.textarea<{width?: string | number} & PaddingProps & MarginProps>`
    ${width}; ${borders};
    border-radius: 5px;
    border: ${theme.borderWidth} solid var(--borderGray, #f00);
    background-color: var(--white, #f00);
    color: var(--black, #f00);
    padding: 5px;
    resize: none;
    vertical-align: top;

    ${margin} ${padding}

    &:focus {
        outline: none;
    }

    &:disabled {
        background-color: var(--lightGray, #f00);
    }
`;

TextArea.displayName = "TextArea";

export default TextArea;
