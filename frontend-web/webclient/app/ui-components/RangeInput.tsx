import * as React from "react";
import {injectStyle} from "@/Unstyled";
import Flex from "./Flex";

interface RangeInputProps {
    value: number;
    autoFocus?: boolean;
    onChange: React.ChangeEventHandler<HTMLInputElement, HTMLInputElement>;
    min?: number;
    max: number;
    background?: string | undefined;
    thumbColor?: string | undefined;
    markers?: string[];
}

const MarkerWrapperStyle = injectStyle("thingy-style", cl => `
    ${cl} {
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        writing-mode: vertical-lr;
        width: 100%;
    }
`);

function MarkerWrapper(props: React.PropsWithChildren): React.ReactNode {
    return <Flex className={MarkerWrapperStyle}>
        {props.children}
    </Flex>
}

export default function RangeInput(props: RangeInputProps): React.ReactNode {
    const style: Record<string, string> = {
        "--trackBackground": props.background ?? "var(--primaryMain)",
        "--thumbColor": props.thumbColor ?? "var(--primaryMain)",
    };

    const markers = React.useMemo(() => {
        if (!props.markers?.length) return null;
        return <>
            <MarkerWrapper>
                {props.markers.map((v, idx, arr) =>
                    <div key={idx} style={{marginLeft: "9px", display: "block", content: "", width: "2px", height: "5px", backgroundColor: "rebeccapurple", marginRight: idx === arr.length - 1 ? "9px" : 0}} />
                )}
            </MarkerWrapper>
            <MarkerWrapper>
                {props.markers.map((v, idx) => {
                    const isSingleChar = v.toString().length === 1;
                    return <div key={idx} style={{textAlign: isSingleChar ? "center" : undefined, minHeight: "21px", transform: isSingleChar ? "rotate(0.75turn)" : "rotate(0.75turn)"}}>{v}</div>
                })}
            </MarkerWrapper>

        </>
    }, [props.markers]);

    return (<>
        <input value={props.value} style={style} autoFocus={props.autoFocus} onChange={props.onChange}
            className={RangeInputStyle} min={props.min ?? 0} max={props.max} type="range" list={markers ? "markers" : undefined} />
        {markers}
    </>);
}

const RangeInputStyle = injectStyle("range-input-style", cl => `
    ${cl} {
        -webkit-appearance: none;
        width: 100%;
        height: 12px;
        padding-top: 8px;
        padding-bottom: 8px;
        border-radius: 4px;
        background: transparent;
        cursor: pointer;
    }

    ${cl}:focus {
        outline: none;
    }

    ${cl}::-webkit-slider-runnable-track {
        background: var(--trackBackground);
        height: 8px;
        border-radius: 12px;
    }
    
    ${cl}::-moz-range-track {
        background: var(--trackBackground);
        height: 8px;
        border-radius: 12px;
    }

    ${cl}::-moz-range-thumb {
        cursor: ew-resize;
        border: 1px solid var(--textPrimary);
        height: 18px;
        width: 18px;
        border-radius: 12px;
        background: var(--thumbColor);
        -webkit-appearance: none;
    }
    
    ${cl}::-webkit-slider-thumb {
        cursor: ew-resize;
        border: 1px solid var(--textPrimary);
        height: 18px;
        width: 18px;
        border-radius: 12px;
        background: var(--thumbColor);
        -webkit-appearance: none;
        margin-top: -3px;
    }
`);