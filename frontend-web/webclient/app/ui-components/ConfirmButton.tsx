import * as React from "react";
import {ButtonProps} from "ui-components/Button";
import {ButtonHTMLAttributes, useRef, useState} from "react";
import {Button} from "ui-components/index";
import styled from "styled-components";

export interface ConfirmButtonProps extends ButtonProps, ButtonHTMLAttributes<HTMLButtonElement> {
}

const Dialog = styled.div`
    position: relative;
    top: 30px;
    left: 30px;
`;

const DialogInner = styled.div`
    position: absolute;
    padding: 16px;
    background: red;
    color: white;
    border: 1px solid black;
`;

const ConfirmButton: React.FunctionComponent<ConfirmButtonProps> = props => {
    const [dialogOpen, setDialogOpen] = useState<boolean>(false);
    const buttonRef = useRef<HTMLButtonElement>(null);
    const newProps = {...props};
    // eslint-disable-next-line
    delete newProps["ref"];
    newProps.onClick = () => {
        setDialogOpen(true);
    };
    return <div>
        <Button ref={buttonRef} {...newProps}>{props.children}</Button>
        <Dialog>
            <DialogInner>
                Testing
            </DialogInner>
        </Dialog>
    </div>;
};

export default ConfirmButton;
