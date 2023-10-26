import * as React from "react";
import Form, {FormProps} from "@rjsf/core";
import styled from "styled-components";
import {theme} from "@/ui-components";

export const JsonSchemaForm: React.FunctionComponent<FormProps<any>> = (props) => {
    return <FormWrapper><Form {...props}/></FormWrapper>;
};

const FormWrapper = styled.div`
  label {
    display: block;
  }

  .required {
    color: var(--red, #f00);
  }
  
  fieldset {
    min-width: 0;
    margin: 0;
    padding: 0;
    border: 0;
  }

  & > form > .form-group > fieldset {
    background-color: transparent;
    border: 0;
    padding: 16px 0 0;
  }

  & > form > .form-group > fieldset > legend {
    font-size: ${theme.fontSizes[4]}px;
  }
  
  & > form > .form-group > fieldset > * > fieldset {
    padding: 16px;
    border-top-left-radius: 10px;
    border-top-right-radius: 10px;
    background-color: var(--appStoreFavBg, #f00);
    display: grid;
    grid-gap: 16px;
    margin-bottom: 32px;
  }
  
  & > form > .form-group > fieldset > * > fieldset fieldset {
    display: grid;
    grid-gap: 16px;
    margin-bottom: 32px;
  }
  
  & > form > .form-group > fieldset > * > fieldset > legend, & > form > .form-group > fieldset > *  legend {
    font-size: ${theme.fontSizes[3]}px;
  }

  legend {
    float: left;
    width: 100%;
  }
  
  label {
    font-weight: bold;
  }
  
  textarea {
    min-height: 120px;
  }

  input, textarea {
    display: block;
    font-family: inherit;
    color: var(--black, #f00);
    background-color: transparent;

    margin: 0;

    &:invalid {
      border-color: var(--red, #f00);
    }

    ::placeholder {
      color: var(--gray, #f00);
    }

    &:focus {
      outline: none;
      background-color: transparent;
      border-color: var(--blue, #f00)
    }

    &:disabled {
      background-color: var(--lightGray, #f00);
    }

    border-width: ${theme.borderWidth};
    border-color: var(--borderGray, #f00);
    border-style: solid;
    border-radius: 5px;
    padding: 7px 12px;
    width: 100%;
  }

  input[type="checkbox"] {
    display: inline;
    width: auto;
    margin-right: 10px;
  }
`;