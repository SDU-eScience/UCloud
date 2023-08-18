import ReactDatePicker, {ReactDatePickerProps} from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
import theme from "@/ui-components/theme";
import {injectStyle} from "@/Unstyled";
import * as React from "react";
import Box, {BoxProps} from "@/ui-components/Box";

export const DatePickerClass = injectStyle("date-picker", k => `
    ${k} {
        appearance: none;
        display: block;
        font-family: inherit;
        color: inherit;
        font-size: ${theme.fontSizes[1]}px;
        background-color: transparent;
        border-radius: ${theme.radius};
        border-width: 0;
        border-style: solid;
        border-color: var(--borderGray, #f00);
        padding: 8px 12px;
        margin: 0;
        width: 100%;
        font-size: 18px;
    }
    
    ${k}::placeholder {
        color: var(--gray, #f00);
    }
    
    ${k}::-ms-clear {
        display: none;
    }
    
    ${k}:disabled {
        background-color: var(--lightGray, #f00);
    }
`);

export const DatePicker: React.FunctionComponent<ReactDatePickerProps<never, true> & BoxProps> = props => {
    return <Box {...props}><ReactDatePicker {...props} className={DatePickerClass} /></Box>;
};

DatePicker.displayName = "DatePicker";

export const SlimDatePickerClass = injectStyle("slim-date-picker", k => `
    ${k} .react-datepicker__day-name, ${k} .react-datepicker__day, ${k} .react-datepicker__time-name {
        width: 1.4rem;
        line-height: 1.4rem;
    }  
`);
