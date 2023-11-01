import * as React from "react";
import {TextH1, TextH2, TextH3, TextH4, TextH5, TextH6} from "./Text";

export const h1 = ({children, ...props}): JSX.Element =>
    (<TextH1 bold={true} fontSize={"32px"} m={0} {...props}>{children}</TextH1>);
export const h2 = ({children, ...props}): JSX.Element =>
    (<TextH2 bold={true} fontSize={"25px"} m={0} {...props}>{children}</TextH2>);
export const h3 = ({children, ...props}): JSX.Element =>
    (<TextH3 regular={true} fontSize={"20px"} m={0} {...props}>{children}</TextH3>);
export const h4 = ({children, ...props}): JSX.Element =>
    (<TextH4 regular={true} fontSize={"16px"} m={0} {...props}>{children}</TextH4>);
export const h5 = ({children, ...props}): JSX.Element =>
    (<TextH5 bold={props.bold ?? true} fontSize={"14px"} m={0} {...props}>{children}</TextH5>);
export const h6 = ({children, ...props}): JSX.Element =>
    (<TextH6 bold={true} caps={true} fontSize={"12px"} m={0} {...props}>{children}</TextH6>);

export default h3;
