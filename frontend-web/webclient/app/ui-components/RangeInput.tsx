import * as React from "react";
import {injectStyle, injectStyleSimple} from "@/Unstyled";

interface RangeInputProps {
    value: number;
    autoFocus?: boolean;
    onChange: React.ChangeEventHandler<HTMLInputElement, HTMLInputElement>;
    min?: number;
    max: number;
    background?: string | undefined;
    thumbColor?: string | undefined;
    markers?: number[];
}

export default function RangeInput(props: RangeInputProps): React.ReactNode {
    const style: Record<string, string> = {
        "--trackBackground": props.background ?? "var(--primaryMain)",
        "--thumbColor": props.thumbColor ?? "var(--primaryMain)",
    };

    const markers = React.useMemo(() => {
        if (!props.markers?.length) return null;
        return <datalist id="markers" className={DataListStyle}>
            {props.markers.map(idx => <option key={idx} value={idx} />)}
        </datalist>
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
        background: transparent;
    }

    ${cl}:focus {
        outline: none;
    }

    ${cl}::-webkit-slider-runnable-track {
        background: var(--trackBackground);
        height: 12px;
        border-radius: 12px;
    }
    
    ${cl}::-moz-range-track {
        background: var(--trackBackground);
        height: 12px;
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

const DataListStyle = injectStyleSimple("datalist-style", `
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    writing-mode: vertical-lr;
    width: 100%;
`);