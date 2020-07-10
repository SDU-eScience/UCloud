import styled from "styled-components";
import {width} from "styled-system";
import {borders} from "./Input";
import theme from "./theme";


export const TextArea = styled.textarea<{width?: string | number}>`
    ${width}; ${borders};
    border-radius: 5px;
    border: ${theme.borderWidth} solid var(--borderGray, #f00);
    background-color: var(--white, #f00);
    color: var(--black, #f00);
    padding: 5px;
    resize: none;
    vertical-align: top;
    &:focus {
        outline: none;
    }

    &:disabled {
        background-color: var(--FtFolderColor2, #f00);
    }
`;

TextArea.displayName = "TextArea";

export default TextArea;
