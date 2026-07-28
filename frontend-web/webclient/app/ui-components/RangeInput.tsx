import * as React from "react";
import {injectStyle} from "@/Unstyled";
import Flex from "./Flex";

interface RangeInputProps {
    value: number;
    autoFocus?: boolean;
    onChange: (value: number) => void;
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

    ${cl} > div > .marker-mark {
        display: "block";
        content: "";
        width: 2px;
        height: 5px;
        border-radius: 12px;
        background-color: var(--textPrimary);
    }

    ${cl} > .marker-text {
        text-align: center;
        width: 20px;
        cursor: pointer;
        min-height: 20px;
        transform: rotate(0.75turn);
    }
`);

function MarkerWrapper(props: React.PropsWithChildren): React.ReactNode {
    return <Flex className={MarkerWrapperStyle}>
        {props.children}
    </Flex>
}

export default function RangeInput(props: RangeInputProps): React.ReactNode {
    const style: Record<string, string> = {
        "--trackBackground": props.background ?? "var(--secondaryMain)",
        "--thumbColor": props.thumbColor ?? "var(--primaryLight)",
    };

    const markers = React.useMemo(() => {
        if (!props.markers?.length) return null;
        return <>
            <MarkerWrapper>
                {props.markers.map((_, idx) =>
                    <Flex key={idx} width="20px" cursor="pointer" alignItems="center" onClick={() => {
                        props.onChange(idx);
                    }}>
                        <div className="marker-mark" />
                    </Flex>
                )}
            </MarkerWrapper>
            <MarkerWrapper>
                {props.markers.map((v, idx) => <div
                    key={idx}
                    className="marker-text"
                    onClick={() => {
                        props.onChange(idx);
                    }}
                >{v}</div>)}
            </MarkerWrapper>

        </>
    }, [props.markers]);

    return (<>
        <input value={props.value} style={style} autoFocus={props.autoFocus} onChange={e => props.onChange(e.target.valueAsNumber)}
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
        border: 1px solid var(--borderColor);
    }
    
    ${cl}::-moz-range-track {
        background: var(--trackBackground);
        height: 8px;
        border-radius: 12px;
        border: 1px solid var(--borderColor);
    }

    ${cl}::-moz-range-thumb {
        cursor: ew-resize;
        border: none;
        height: 18px;
        width: 18px;
        border-radius: 12px;
        background: var(--thumbColor);
        -webkit-appearance: none;
    }
    
    ${cl}::-webkit-slider-thumb {
        cursor: ew-resize;
        border: none;
        height: 18px;
        width: 18px;
        border-radius: 12px;
        background: var(--thumbColor);
        -webkit-appearance: none;
        margin-top: -5px;
    }
`);