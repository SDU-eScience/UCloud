import * as React from "react";
import Form, {FormProps} from "@rjsf/core";
import {theme} from "@/ui-components";
import {injectStyle} from "@/Unstyled";

export const JsonSchemaForm: React.FunctionComponent<FormProps<any>> = (props) => {
    return <div className={FormWrapper}><Form {...props} /></div>;
};

/* div */
const FormWrapper = injectStyle("form-wrapper", k => `
  ${k} label {
    display: block;
  }

  ${k} .required {
    color: var(--red, #f00);
  }
  
  ${k} fieldset {
    min-width: 0;
    margin: 0;
    padding: 0;
    border: 0;
  }

  ${k} > form > .form-group > fieldset {
    background-color: transparent;
    border: 0;
    padding: 16px 0 0;
  }

  ${k} > form > .form-group > fieldset > legend {
    font-size: ${theme.fontSizes[4]}px;
  }
  
  ${k} > form > .form-group > fieldset > * > fieldset {
    padding: 16px;
    border-top-left-radius: 10px;
    border-top-right-radius: 10px;
    background-color: var(--appStoreFavBg, #f00);
    display: grid;
    grid-gap: 16px;
    margin-bottom: 32px;
  }
  
  ${k} > form > .form-group > fieldset > * > fieldset fieldset {
    display: grid;
    grid-gap: 16px;
    margin-bottom: 32px;
  }
  
  ${k} > form > .form-group > fieldset > * > fieldset > legend, ${k} > form > .form-group > fieldset > *  legend {
    font-size: ${theme.fontSizes[3]}px;
  }

  ${k} legend {
    float: left;
    width: 100%;
  }
  
  ${k} label {
    font-weight: bold;
  }
  
  ${k} textarea {
    min-height: 120px;
  }

  ${k} input, ${k} textarea {
    display: block;
    font-family: inherit;
    color: var(--black, #f00);
    background-color: transparent;
    margin: 0;
    border-width: ${theme.borderWidth};
    border-color: var(--borderGray, #f00);
    border-style: solid;
    border-radius: 5px;
    padding: 7px 12px;
    width: 100%;
  }
    ${k} input:invalid, ${k} textarea:invalid {
        border-color: var(--red, #f00);
    }
      
  
    ${k} input::placeholder, ${k} textarea::placeholder {
        color: var(--gray, #f00);
    }
  
    ${k} input:focus, ${k} textarea:focus {
        outline: none;
        background-color: transparent;
        border-color: var(--primary);
    }
  
    ${k} input:disabled, ${k} textarea:focus {
        background-color: var(--lightGray, #f00);
    }

    ${k} input[type="checkbox"]{
        display: inline;
        width: auto;
        margin-right: 10px;
    }
`);