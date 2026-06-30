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

const ThingyStyle = injectStyle("thingy-style", cl => `
    ${cl} {
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        writing-mode: vertical-lr;
        width: 100%;
        padding-left: 8px;
        padding-right: 11px;
    }

    ${cl} > div {
        display: block;
    }

    ${cl} > div > div:first-child {
        transform: translate(50%);
        display: block;
        content: '';
        background-color: var(--textPrimary);
        width: 2px;
        height: 8px;
        border-radius: 12px;
    }

    ${cl} > div > div:nth-child(2) {
        --offset: 0;
        width: 0;
        margin-top: 18px;
        /* margin-left: var(--offset); */
        transform: rotate(-60deg);
    }
`);

function CustomDataListThingy(props: React.PropsWithChildren): React.ReactNode {
    return <Flex className={ThingyStyle}>
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
        return <CustomDataListThingy>
            {props.markers.map((v, idx) => <div key={idx}>
                <div></div>
                <div style={{"--offset": `-${v.toString().length / 2}em`}}>{v}</div>
            </div>)}
        </CustomDataListThingy>
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