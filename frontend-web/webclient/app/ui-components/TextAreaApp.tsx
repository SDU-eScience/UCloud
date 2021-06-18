import styled from "styled-components";
import {margin, MarginProps, padding, PaddingProps, width, WidthProps, maxWidth, MaxWidthProps} from "styled-system";
import {borders} from "./Input";
import theme from "./theme";
import { TextArea } from "ui-components";

//extends TextArea
export const TextAreaApp = styled(TextArea)`
    width: 100%;
    height:300px;
    resize: vertical;
`;

TextAreaApp.displayName = "TextAreaApp";

export default TextAreaApp;