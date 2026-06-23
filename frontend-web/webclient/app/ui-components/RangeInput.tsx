import * as React from "react";
import {injectStyle, injectStyleSimple} from "@/Unstyled";

interface RangeInputProps {
    value: number;
    autoFocus?: boolean;
    onChange: React.ChangeEventHandler<HTMLInputElement, HTMLInputElement>;
    min?: number;
    max: number;
    background?: string | undefined;
    markers?: number[];
}

export default function RangeInput(props: RangeInputProps): React.ReactNode {
    const style: Record<string, string> = {
        "--background": props.background ?? "var(--primaryMain)"
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
        width: calc(100% - 16px);
        padding-top: 8px;
        padding-bottom: 8px;
        cursor: pointer;
    }

    ${cl}::-webkit-slider-runnable-track, ${cl}::-moz-range-track {
        background: var(--background);
    }
    
    input[type=range]::-webkit-slider-thumb {
        -webkit-appearance: none;
        margin-top: -14px; /* You need to specify a margin in Chrome, but in Firefox and IE it is automatic */
    }

    input[type=range]::-moz-range-thumb, input[type=range]::-webkit-slider-thumb {
        box-shadow: 1px 1px 1px #000000, 0px 0px 1px #0d0d0d;
        border: 1px solid #000000;
        height: 36px;
        width: 16px;
        border-radius: 3px;
        background: #ffffff;
        cursor: pointer;
    }
`);

const DataListStyle = injectStyleSimple("datalist-style", `
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    writing-mode: vertical-lr;
    width: 100%;
`);