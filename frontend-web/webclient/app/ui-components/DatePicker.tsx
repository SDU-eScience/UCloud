import ReactDatePicker, {ReactDatePickerProps} from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
import {classConcat, injectStyle} from "@/Unstyled";
import * as React from "react";
import Box, {BoxProps} from "@/ui-components/Box";

export const DatePickerClass = injectStyle("date-picker", k => `
    ${k} {
        appearance: none;
        display: block;
        font-family: inherit;
        color: inherit;
        background-color: transparent;
        border-radius: 8px;
        border-width: 0;
        border-style: solid;
        border-color: var(--borderColor, #f00);
        padding: 8px 12px;
        margin: 0;
        width: 100%;
        font-size: 18px;
    }
    
    ${k}::placeholder {
        color: var(--borderColor, #f00);
    }
    
    ${k}::-ms-clear {
        display: none;
    }
    
    ${k}:disabled {
        background: var(--backgroundDisabled);
        color: var(--textDisabled);
    }
`);

export const DatePicker: React.FunctionComponent<ReactDatePickerProps<never, true> & BoxProps> = props => {
    return <Box {...props}><ReactDatePicker {...props} className={classConcat(DatePickerClass, props.className)} /></Box>;
};

DatePicker.displayName = "DatePicker";

export const SlimDatePickerClass = injectStyle("slim-date-picker", k => `
    ${k} .react-datepicker__day-name, ${k} .react-datepicker__day, ${k} .react-datepicker__time-name {
        width: 1.4rem;
        line-height: 1.4rem;
    }  
`);
