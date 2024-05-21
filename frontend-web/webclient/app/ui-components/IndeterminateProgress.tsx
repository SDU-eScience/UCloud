import * as React from "react";
import Flex from "./Flex";
import Text from "./Text";
import {ThemeColor} from "./theme";
import {injectStyle, unbox} from "@/Unstyled";

interface ProgressBaseProps {
    height?: number | string;
    value?: number | string;
    color?: ThemeColor;
    children?: React.ReactNode;
}

const ProgressBaseClass = injectStyle("progress-base", k => `
    ${k} {
        border-radius: 5px;
        background-color: var(--borderColor, #f00);
        position: relative;
        overflow: hidden;
        width: 100%;
        --progressColor: var(--successMain);
    }
    
    ${k}:after {
        content: " ";
        display: block;
        width: 120px;

        border-radius: 5px;
        position: absolute;
        height: 100%;

        z-index: 2;
        background-color: var(--progressColor, #f00);

        animation: movingbox 3s linear infinite;
    }
    
    @keyframes movingbox {
        from {
            left: -120px;
        }

        to {
            left: 100%;
        }
    }
`);

const ProgressBase: React.FunctionComponent<ProgressBaseProps> = ({
  color = "successMain",
  height = "30px",
  ...props
}) => {
    const style = unbox({height, ...props});
    if (color) style["--progressColor"] = `var(--${color})`;
    return <div className={ProgressBaseClass} style={style} children={props.children}/>;
};

interface Progress {
    color: ThemeColor;
    label: string;
}

const Progress = ({color, label}: Progress): React.ReactNode => (
    <>
        <ProgressBase height="30px" color={color}/>
        {label ? <Flex justifyContent="center"><Text>{label}</Text></Flex> : null}
    </>
);

ProgressBase.displayName = "ProgressBase";

export default Progress;
