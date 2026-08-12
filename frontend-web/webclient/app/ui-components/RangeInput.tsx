import * as React from "react";
import {DataAttributes, injectStyle, unboxDataTags} from "@/Unstyled";

type RangeInputProps = {
    value: number;
    autoFocus?: boolean;
    onChange: (value: number) => void;
    min?: number;
    max: number;
    background?: string | undefined;
    thumbColor?: string | undefined;
    markers?: string[];
    dividerAt?: number;
} & DataAttributes;

const MarkerWrapperStyle = injectStyle("range-input-markers", cl => `
    ${cl} {
        --rangeInputHeight: 38px;
        --rangeTrackHeight: 18px;
        --rangeThumbSize: 28px;
        --rangeMarkerSize: 6px;
        --rangeAnimationDuration: 120ms;
        --displayProgress: var(--rangeProgress);
        position: relative;
        width: 100%;
    }

    ${cl}[data-pointer-active="true"] {
        --displayProgress: var(--pointerProgress);
    }

    ${cl} > .marker-marks {
        position: absolute;
        z-index: 3;
        inset: calc(var(--rangeInputHeight) / 2) calc(var(--rangeThumbSize) / 2) auto;
        display: flex;
        justify-content: space-between;
        align-items: center;
        transform: translateY(-50%);
        pointer-events: none;
    }

    ${cl} > .range-thumb-track {
        position: absolute;
        z-index: 2;
        top: calc(var(--rangeInputHeight) / 2);
        left: calc(var(--rangeThumbSize) / 2);
        right: calc(var(--rangeThumbSize) / 2);
        pointer-events: none;
    }

    ${cl} > .range-divider-track {
        position: absolute;
        z-index: 1;
        top: calc((var(--rangeInputHeight) - var(--rangeTrackHeight)) / 2 - 8px);
        left: calc(var(--rangeThumbSize) / 2);
        right: calc(var(--rangeThumbSize) / 2);
        height: calc(var(--rangeTrackHeight) + 16px);
        pointer-events: none;
    }

    ${cl} .range-divider {
        position: absolute;
        top: 0;
        bottom: 0;
        left: var(--rangeDivider);
        width: 6px;
        box-sizing: border-box;
        border: 1px solid var(--borderColor);
        border-radius: 999px;
        background: var(--secondaryMain);
        transform: translateX(-50%);
    }

    ${cl} .range-thumb {
        position: absolute;
        left: var(--displayProgress);
        width: var(--rangeThumbSize);
        height: var(--rangeThumbSize);
        box-sizing: border-box;
        border: 2px solid var(--thumbBorder);
        border-radius: 50%;
        background: var(--fixedWhite);
        transform: translate(-50%, -50%) scale(1);
        transition: left var(--rangeAnimationDuration) ease-out,
            transform var(--rangeAnimationDuration) ease-out;
    }

    ${cl}:has(input:hover) .range-thumb {
        transform: translate(-50%, -50%) scale(1.08);
    }

    ${cl}:has(input:active) .range-thumb {
        transform: translate(-50%, -50%) scale(1.16);
    }

    ${cl}[data-pointer-active="true"] .range-thumb {
        transition: none;
    }

    ${cl}[data-pointer-active="true"] input::-webkit-slider-runnable-track {
        transition: none;
    }

    ${cl}[data-pointer-active="true"] input::-moz-range-track {
        transition: none;
    }

    ${cl} .marker-mark {
        width: var(--rangeMarkerSize);
        height: var(--rangeMarkerSize);
        border-radius: 50%;
        background-color: color-mix(in srgb, var(--fixedWhite) 75%, transparent);
        cursor: pointer;
        pointer-events: auto;
        transition: transform var(--rangeAnimationDuration) ease-out;
    }

    ${cl} .marker-mark:hover {
        transform: scale(1.4);
    }

    ${cl} > .marker-labels {
        display: flex;
        justify-content: space-between;
        margin: 12px calc(var(--rangeThumbSize) / 2 - 10px) 0;
    }

    ${cl} .marker-text {
        text-align: center;
        width: 20px;
        cursor: pointer;
        min-height: 20px;
        white-space: nowrap;
    }
`);

export default function RangeInput(props: RangeInputProps): React.ReactNode {
    const [pointerActive, setPointerActive] = React.useState(false);
    const wrapperRef = React.useRef<HTMLDivElement>(null);
    const pointerStart = React.useRef<{
        id: number;
        x: number;
        boundsLeft: number;
        thumbInset: number;
        usableWidth: number;
    } | null>(null);
    const min = props.min ?? 0;
    const range = props.max - min;
    const progress = range <= 0 ? 0 : Math.min(100, Math.max(0, (props.value - min) / range * 100));
    const style: Record<string, string> = {
        "--trackBackground": props.background ?? "var(--secondaryMain)",
        "--trackFill": props.thumbColor ?? "var(--primaryLight)",
        "--rangeProgress": `${progress}%`,
        "--thumbBorder": "var(--borderColorHover)",
        "--rangeDivider": `${Math.min(100, Math.max(0, (props.dividerAt ?? 0) * 100))}%`,
    };

    const markers = props.markers?.length ? props.markers : null;
    const pointerMetrics = (element: HTMLInputElement): {bounds: DOMRect; thumbInset: number; usableWidth: number} => {
        const bounds = element.getBoundingClientRect();
        const wrapper = element.parentElement;
        const thumbInset = wrapper == null ? 0 : parseFloat(getComputedStyle(wrapper).getPropertyValue("--rangeThumbSize")) / 2;
        return {bounds, thumbInset, usableWidth: Math.max(1, bounds.width - thumbInset * 2)};
    };
    const updatePointerProgress = (clientX: number): void => {
        const start = pointerStart.current;
        if (start == null) return;
        const progress = Math.min(100, Math.max(0, (clientX - start.boundsLeft - start.thumbInset) / start.usableWidth * 100));
        wrapperRef.current?.style.setProperty("--pointerProgress", `${progress}%`);
    };
    const stopPointerTracking = (): void => {
        pointerStart.current = null;
        setPointerActive(false);
        requestAnimationFrame(() => wrapperRef.current?.style.removeProperty("--pointerProgress"));
    };

    return <div ref={wrapperRef} className={MarkerWrapperStyle} style={style} data-pointer-active={pointerActive}>
        <input {...unboxDataTags(props)} value={props.value} autoFocus={props.autoFocus}
            onPointerDown={event => {
                const {bounds, thumbInset, usableWidth} = pointerMetrics(event.currentTarget);
                event.currentTarget.setPointerCapture(event.pointerId);
                pointerStart.current = {
                    id: event.pointerId,
                    x: event.clientX,
                    boundsLeft: bounds.left,
                    thumbInset,
                    usableWidth,
                };
            }}
            onPointerMove={event => {
                const start = pointerStart.current;
                if (start?.id !== event.pointerId || !event.currentTarget.hasPointerCapture(event.pointerId)) return;
                if (!pointerActive) {
                    if (Math.abs(event.clientX - start.x) < 4) return;
                    setPointerActive(true);
                }
                updatePointerProgress(event.clientX);
            }}
            onPointerUp={stopPointerTracking}
            onPointerCancel={stopPointerTracking}
            onBlur={stopPointerTracking}
            onChange={e => props.onChange(e.target.valueAsNumber)}
            className={RangeInputStyle} min={min} max={props.max} type="range" />
        {props.dividerAt == null ? null : <div className="range-divider-track" aria-hidden="true">
            <div className="range-divider" />
        </div>}
        <div className="range-thumb-track" aria-hidden="true">
            <div className="range-thumb" />
        </div>
        {!markers ? null : <>
            <div className="marker-marks">
                {markers.map((_, idx) => <div key={idx} className="marker-mark" onClick={() => props.onChange(idx)} />)}
            </div>
            <div className="marker-labels">
                {markers.map((v, idx) => <div
                    key={idx}
                    className="marker-text"
                    onClick={() => {
                        props.onChange(idx);
                    }}
                >{v}</div>)}
            </div>
        </>}
    </div>;
}

const RangeInputStyle = injectStyle("range-input-style", cl => `
    ${cl} {
        -webkit-appearance: none;
        appearance: none;
        display: block;
        box-sizing: border-box;
        width: 100%;
        height: var(--rangeInputHeight);
        padding: 10px 0;
        margin: 0;
        background: transparent;
        cursor: pointer;
    }

    ${cl}:focus {
        outline: none;
    }

    ${cl}::-webkit-slider-runnable-track {
        box-sizing: border-box;
        background-color: var(--trackBackground);
        background-image: linear-gradient(var(--trackFill), var(--trackFill));
        background-position: left center;
        background-repeat: no-repeat;
        background-size: var(--displayProgress) 100%;
        height: var(--rangeTrackHeight);
        border-radius: var(--rangeTrackHeight);
        border: 1px solid var(--borderColor);
        transition: background-size var(--rangeAnimationDuration) ease-out;
    }
    
    ${cl}::-moz-range-track {
        box-sizing: border-box;
        background-color: var(--trackBackground);
        background-image: linear-gradient(var(--trackFill), var(--trackFill));
        background-position: left center;
        background-repeat: no-repeat;
        background-size: var(--displayProgress) 100%;
        height: var(--rangeTrackHeight);
        border-radius: var(--rangeTrackHeight);
        border: 1px solid var(--borderColor);
        transition: background-size var(--rangeAnimationDuration) ease-out;
    }

    ${cl}::-moz-range-thumb {
        cursor: ew-resize;
        box-sizing: border-box;
        border: 0;
        height: var(--rangeThumbSize);
        width: var(--rangeThumbSize);
        background: transparent;
    }
    
    ${cl}::-webkit-slider-thumb {
        cursor: ew-resize;
        box-sizing: border-box;
        border: 0;
        height: var(--rangeThumbSize);
        width: var(--rangeThumbSize);
        background: transparent;
        -webkit-appearance: none;
        appearance: none;
        margin-top: calc((var(--rangeTrackHeight) - var(--rangeThumbSize)) / 2);
    }

    ${cl}:active::-webkit-slider-thumb {
        cursor: grabbing;
    }

    ${cl}:active::-moz-range-thumb {
        cursor: grabbing;
    }
`);
