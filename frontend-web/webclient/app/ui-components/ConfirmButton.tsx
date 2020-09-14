import * as React from "react";
import {ButtonProps} from "ui-components/Button";
import {ButtonHTMLAttributes, useEffect, useState} from "react";
import {Button} from "ui-components/index";
import styled from "styled-components";
import theme from "ui-components/theme";
import {width, WidthProps} from "styled-system";

export interface ConfirmButtonProps extends ButtonProps, ButtonHTMLAttributes<HTMLButtonElement> {
    dialog: JSX.Element;
    dialogWidth?: string | number;
}

const Dialog = styled.div`
    position: relative;
    top: 45px;
    
    &.open {
        display: block;
    }
    
    &.closed {
        display: none;
    }
`;

const DialogInner = styled.div<WidthProps>`
    position: absolute;
    padding: 16px;
    color: var(--black, #f00);
    background: var(--white, #f00);
    box-shadow: ${theme.shadows.sm};
    ${width};
    text-align: initial;
`;

const ConfirmButton: React.FunctionComponent<ConfirmButtonProps> = props => {
    const [dialogOpen, setDialogOpen] = useState<boolean>(false);
    const newProps = {...props};
    newProps.onClick = () => {
        setDialogOpen(true);
    };

    useEffect(() => {
        const listener = (): void => {
            setDialogOpen(false);
        };

        document.body.addEventListener("click", listener);
        return () => { document.body.removeEventListener("click", listener); };
    }, [setDialogOpen]);

    return <>
        <Dialog className={dialogOpen ? "open" : "closed"}>
            <DialogInner width={props.dialogWidth}>
                {props.dialog}
            </DialogInner>
        </Dialog>
        <Button {...newProps}>{props.children}</Button>
    </>;
};

export default ConfirmButton;
