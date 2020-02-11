import ReactDatePicker from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
import styled from "styled-components";
import {borders, space} from "styled-system";
import theme from "ui-components/theme";
import {InputProps} from "./Input";

export const DatePicker = styled(ReactDatePicker) <InputProps>`
    appearance: none;
    display: block;
    width: 100%;
    font-family: inherit;
    color: inherit;
    font-size: ${theme.fontSizes[1]}px;
    background-color: transparent;
    border-radius: ${theme.radius};
    border-width: 0px;
    border-style: solid;
    border-color: var(--borderGray, #f00);

    padding-top: 14px;
    padding-bottom: 14px;
    padding-left: 12px;
    padding-right: 12px;

    margin: 0;

    ::placeholder {
      color: var(--gray, #f00);
    }

    ::-ms-clear {
      display: none;
    }

    ${borders} ${space};

`;

DatePicker.displayName = "DatePicker";
