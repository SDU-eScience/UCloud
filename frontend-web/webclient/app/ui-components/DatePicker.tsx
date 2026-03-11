import ReactDatePicker, {DatePickerProps} from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
import {classConcat, injectStyle} from "@/Unstyled";
import * as React from "react";
import Box from "@/ui-components/Box";
import {BoxProps} from "./Types";

export const DatePickerClass = injectStyle("date-picker", k => `
    ${k} {
        appearance: none;
        display: block;
        font-family: inherit;
        font-size: inherit;
        color: inherit;
        background-color: transparent;
        border-radius: 5px;
        border-width: 1px;
        border-style: solid;
        border-color: var(--borderColor, #f00);
        padding: 7px 12px;
        margin: 0;
        height: 35px;
        width: 100%;
    }
    
    ${k}::placeholder {
        color: var(--borderColor, #f00);
    }

    
    ${k}:disabled {
        background: var(--backgroundDisabled);
        color: var(--textDisabled);
    }
`);

export const DatePicker: React.FunctionComponent<DatePickerProps & BoxProps> = props => {
    return <Box {...props}><ReactDatePicker {...props} className={classConcat(DatePickerClass, props.className)} /></Box>;
};

DatePicker.displayName = "DatePicker";

export const SlimDatePickerClass = injectStyle("slim-date-picker", k => `
    ${k} .react-datepicker__day-name, ${k} .react-datepicker__day, ${k} .react-datepicker__time-name {
        width: 1.4rem;
        line-height: 1.4rem;
    }  
`);
