import styled from "styled-components";
import {margin, MarginProps, padding, PaddingProps, width, WidthProps, maxWidth, MaxWidthProps} from "styled-system";
import {borders} from "./Input";
import theme from "./theme";

export const TextArea = styled.textarea<{error?: boolean} & PaddingProps & MarginProps & WidthProps & MaxWidthProps>`
    ${maxWidth}; ${width}; ${borders};
    border-radius: 5px;
    border: ${theme.borderWidth} solid var(--borderGray, #f00);
    background-color: transparent;
    color: var(--black, #f00);
    padding: 5px;
    resize: none;
    vertical-align: top;

    ${margin} ${padding}

    ${p => p.error ? "border-color: var(--red, #f00);" : null}

    &:focus {
        outline: none;
    }

    &:disabled {
        background-color: var(--lightGray, #f00);
    }
`;

TextArea.displayName = "TextArea";

export default TextArea;
