import * as React from "react";
import {Button} from "@/ui-components/index";
import {useCallback, useLayoutEffect, useRef} from "react";
import {ButtonClass, ButtonProps} from "@/ui-components/Button";
import Icon, {IconName} from "@/ui-components/Icon";
import {doNothing} from "@/UtilityFunctions";
import {selectContrastColor, selectHoverColor, ThemeColor} from "@/ui-components/theme";
import {classConcat, injectStyle} from "@/Unstyled";
import {divHtml} from "@/Utilities/HTMLUtilities";

const ConfirmButtonClass = injectStyle("confirm-button", k => `
    ${k} {
        --color: var(--errorContrast);
        --background: var(--errorMain, #f00);
        --tick-stroke: white;

        position: relative;
        outline: none;
        user-select: none;
        -webkit-user-select: none;
        cursor: pointer;
        backface-visibility: hidden;
        min-width: 200px;
        justify-content: flex-start;
        gap: var(--icon-gap, 5px);
        background: var(--background, #f00);
    }

    ${k}[data-align="center"] {
        justify-content: center;
    }

    ${k}[data-no-text="true"] {
        justify-content: center;
        min-width: 50px;
    }

    ${k}[data-no-icon="true"]:not([data-no-text="true"]) {
        justify-content: center;
    }

    ${k}:hover .icons:before {
        background: var(--hoverColor, var(--background));
    }

    ${k} .icon-slot {
        position: relative;
        width: var(--icon-slot, 18px);
        height: var(--icon-slot, 18px);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
    }

    ${k}[data-no-icon="true"] .icon-slot {
        position: absolute;
        left: 1.2em;
        top: 0;
        bottom: 0;
        margin: auto 0;
    }

    ${k}[data-no-icon="true"][data-no-text="true"] .icon-slot {
        left: 0;
        right: 0;
        margin: auto;
    }

    ${k} .icon-slot svg,
    ${k} .icon-slot img {
        margin: 0;
    }

    ${k} .shaking {
        transform: translate3d(0, 0, 0);
        animation: button-shake 0.82s cubic-bezier(.36, .07, .19, .97) both;
    }

    ${k} .icons {
        border-radius: 50%;
        position: absolute;
        inset: 0;
        margin: auto;
        width: 20px;
        height: 20px;
        overflow-y: hidden;
        transition: transform .3s, opacity .2s;
        opacity: var(--icon-o, 0);
        transform: translateX(var(--icon-x, -4px));
    }

    ${k} .icons:before {
        content: '';
        width: 16px;
        height: 16px;
        left: 2px;
        top: 2px;
        z-index: 1;
        position: absolute;
        background: var(--background);
        border-radius: inherit;
        transform: scale(var(--background-scale, 1));
        transition: transform .32s ease;
    }

    ${k} .icons > svg {
        display: block;
        fill: none;
        width: 20px;
        height: 20px;
    }

    ${k} .icons > svg.progress {
        transform: rotate(-90deg) scale(var(--progress-scale, 1));
        transition: transform .5s ease;
    }

    ${k} .icons > svg.progress circle {
        stroke-dashoffset: 1;
        stroke-dasharray: var(--progress-array, 0) 52;
        stroke-width: 16;
        stroke: white;
        transition: stroke-dasharray var(--duration) linear;
    }

    ${k} .icons > svg.tick {
        left: -5px;
        top: 0;
        position: absolute;
        stroke-width: 3;
        stroke-linecap: round;
        stroke-linejoin: round;
        stroke: var(--tick-stroke);
        transition: stroke .3s ease .7s;
    }

    ${k} .icons > svg.tick polyline {
        stroke-dasharray: 18 18 18;
        stroke-dashoffset: var(--tick-offset, 18);
        transition: stroke-dashoffset .4s ease .7s;
    }

    ${k} .text-stack {
        display: inline-grid;
        overflow-y: hidden;
        pointer-events: none;
        text-align: center;
        white-space: nowrap;
    }

    ${k}[data-align="left"] .text-stack {
        text-align: left;
    }

    ${k} .text-stack > span {
        grid-row: 1;
        grid-column: 1;
        backface-visibility: hidden;
    }

    ${k}.success .text-stack > span,
    ${k}.process .text-stack > span {
        transition: transform .3s ease .16s, opacity .2s ease .16s;
    }

    ${k} .line-main {
        transform: translateY(var(--main-y, 0));
        opacity: var(--main-o, 1);
    }

    ${k} .line-hold {
        transform: translateY(var(--hold-y, 0));
        opacity: var(--hold-o, 0);
    }

    ${k} .line-done {
        transform: translateY(var(--done-y, 100%));
        opacity: var(--done-o, 0);
    }

    ${k}.hint:not(.success),
    ${k}:hover:not(.success):not(:disabled) {
        --main-o: 0;
        --hold-o: 1;
    }

    ${k}:disabled:hover {
        background: var(--hoverColor);
    }

    ${k}.process {
        --icon-x: 0;
    }

    ${k}.process,
    ${k}.success {
        --icon-o: 1;
        --progress-array: 52;
    }

    ${k}.process .icon-slot > :not(.icons),
    ${k}.success .icon-slot > :not(.icons),
    ${k}.success .icons > svg.progress {
        opacity: 0;
    }

    ${k}.success {
        --icon-x: 6px;
        --progress-scale: .11;
        --tick-stroke: white;
        --background-scale: 0;
        --tick-offset: 36;
        --main-y: -100%;
        --main-o: 0;
        --hold-y: -100%;
        --hold-o: 0;
        --done-y: 0;
        --done-o: 1;
    }

    ${k}.success .icons > svg.progress {
        animation: tick .3s linear forwards .4s;
    }

    @keyframes tick {
        100% {
            transform: rotate(-90deg) translate(0, -5px) scale(var(--progress-scale));
        }
    }

    @keyframes button-shake {
        10%, 90% {
            transform: translate3d(-1px, 0, 0);
        }

        20%, 80% {
            transform: translate3d(2px, 0, 0);
        }

        30%, 50%, 70% {
            transform: translate3d(-4px, 0, 0);
        }

        40%, 60% {
            transform: translate3d(4px, 0, 0);
        }
    }
`);

/*
  HACK(Jonas):
    This is not an ideal approach, but using a ref or variable through useState doesn't seem to work.
    Likely due to the callbacks wrapping the ref/variable in a stale manner.
    Adding them to the Dependency List doesn't work.
*/
const startedMap = {};
/* HACK(Jonas): End */

const actionDelay = 1000;
const holdToConfirmTime = 1000;
const shakeDelta = 100;
const tickRate = 50;

export const ConfirmationButton: React.FunctionComponent<ButtonProps & {
    actionText?: string,
    icon?: IconName,
    iconSize?: number,
    iconSpacing?: string,
    align?: "left" | "center",
    actionKey?: string;
    onAction?: (actionKey?: string) => Promise<void>;
    hoverColor?: ThemeColor;
    disabled?: boolean;
}> = props => {
    const buttonRef = useRef<HTMLButtonElement>(null);
    const timeout = useRef(-1);
    const timer = useRef(holdToConfirmTime);
    const [tempStartedKey] = React.useState(new Date().getTime());
    const wasReset = useRef(false);

    React.useEffect(() => {
        startedMap[tempStartedKey] = false;
        return () => {
            delete startedMap[tempStartedKey];
        }
    }, []);

    const success = useCallback(() => {
        const button = buttonRef.current;
        if (!button) return;
        timer.current -= tickRate;
        if (timer.current <= 0) {
            button.classList.add("success");
            timeout.current = window.setTimeout(countUp, tickRate);
            setTimeout(() => {
                if (props.onAction) props.onAction(props.actionKey);
            }, actionDelay);
        } else {
            timeout.current = window.setTimeout(success, tickRate);
        }
    }, [buttonRef.current, props.onAction, props.actionKey]);

    const countUp = useCallback(() => {
        const button = buttonRef.current;
        if (!button) return;
        timer.current += tickRate;
        if (timer.current >= holdToConfirmTime) {
            timer.current = holdToConfirmTime;
        } else {
            timeout.current = window.setTimeout(countUp, tickRate);
        }
    }, [buttonRef.current]);

    const start = useCallback(() => {
        const button = buttonRef.current;
        if (!button) return;
        if (button.classList.contains("process")) return;
        if (timeout.current !== -1) {
            clearTimeout(timeout.current);
            timeout.current = -1;
        }

        if (button.classList.contains("success")) {
            wasReset.current = true;
        }

        button.classList.remove("success");
        button.classList.add("process");
        startedMap[tempStartedKey] = true;
        timeout.current = window.setTimeout(success, tickRate);
    }, [buttonRef.current, success]);

    const end = useCallback(() => {
        const button = buttonRef.current;
        if (!button) return;
        button.classList.remove("process");
        if (timeout.current !== -1) {
            clearTimeout(timeout.current);
            timeout.current = window.setTimeout(countUp, tickRate);
        }

        if (timer.current > holdToConfirmTime - shakeDelta && !wasReset.current) {
            for (let i = 0; i < button.children.length; i++) {
                button.children.item(i)?.classList.add("shaking");
            }
            button.classList.add("hint");
            setTimeout(() => {
                button.classList.remove("hint");
                for (let i = 0; i < button.children.length; i++) {
                    button.children.item(i)?.classList.remove("shaking");
                }
            }, holdToConfirmTime - shakeDelta);
        }
        startedMap[tempStartedKey] = false;
        wasReset.current = false;
    }, [buttonRef.current, timeout]);

    useLayoutEffect(() => {
        const button = buttonRef.current;
        if (!button) return;

        const colorOrDefault = props.color ?? "errorMain";

        button.style.setProperty("--duration", `${holdToConfirmTime}ms`);
        button.style.setProperty("--hoverColor", `var(--${props.hoverColor ?? selectHoverColor(colorOrDefault)})`)
        button.style.setProperty("--color", `var(--${props.textColor ?? selectContrastColor(colorOrDefault)})`)
        button.style.setProperty("--background", `var(--${colorOrDefault})`)
        button.style.setProperty("--icon-slot", `${props.iconSize ?? 18}px`)
        button.style.setProperty("--icon-gap", props.iconSpacing ?? "5px")
        button.style.removeProperty("background-color");
        button.setAttribute("data-no-text", (!props.actionText).toString());
        button.setAttribute("data-no-icon", (!props.icon).toString());
        const alignOrDefault = props.align ?? (props.actionText && props.icon ? "left" : "center");
        button.setAttribute("data-align", alignOrDefault);
    }, [buttonRef.current, props.actionText, props.hoverColor, props.color, props.textColor, props.icon, props.align, props.iconSpacing, props.iconSize]);

    const passedProps = {...props};
    delete passedProps.onAction;

    return <Button
        {...passedProps}
        onMouseDown={start}
        onTouchStart={start}
        onMouseLeave={() => {
            if (startedMap[tempStartedKey]) end();
        }}
        onMouseUp={end}
        onTouchEnd={end}
        onClick={doNothing}
        btnRef={buttonRef}
        className={ConfirmButtonClass}
        data-tag={"confirm-button"}
        width={props.width}
    >
        <span className={"icon-slot"}>
            {props.icon ? <Icon name={props.icon} size={props.iconSize ?? 18} /> : null}
            <span className={"icons"}>
                <svg className="progress" viewBox="0 0 32 32">
                    <circle r="8" cx="16" cy="16" />
                </svg>
                <svg className="tick" viewBox="0 0 24 24">
                    <polyline points="18,7 11,16 6,12" />
                </svg>
            </span>
        </span>
        {!props.actionText ? null : (
            <span className={"text-stack"}>
                <span className="line-main">{props.actionText}</span>
                <span className="line-hold" aria-hidden={true}>Hold to confirm</span>
                <span className="line-done" aria-hidden={true}>Done</span>
            </span>
        )}
    </Button>;
};

export function ConfirmationButtonPlainHTML(
    icon: HTMLDivElement,
    actionText: string,
    action: () => void,
    opts: {
        align?: "left" | "center",
        asSquare?: boolean,
        color?: ThemeColor,
        hoverColor?: ThemeColor,
        textColor?: ThemeColor,
        disabled?: boolean,
    },
): HTMLElement {
    const button = document.createElement("button");

    {
        button.style.overflowY = "hidden";
        button.style.maxHeight = "40px";
        button.className = classConcat(ConfirmButtonClass, ButtonClass);
        button.setAttribute("data-no-text", (!actionText).toString());
        button.setAttribute("data-no-icon", (!icon).toString());
        button.setAttribute("data-align", opts.align ?? (actionText && icon ? "left" : "center"));
        button.setAttribute("data-attached", "false");
        button.setAttribute("data-square", (!!opts.asSquare).toString());
        button.setAttribute("data-fullwidth", "false");
        button.setAttribute("data-size", "standard");

        const colorOrDefault = opts.color ?? "errorMain";

        button.style.setProperty("--duration", `${holdToConfirmTime}ms`);
        button.style.setProperty("--hoverColor", `var(--${opts.hoverColor ?? selectHoverColor(colorOrDefault)})`)
        button.style.setProperty("--color", `var(--${opts.textColor ?? selectContrastColor(colorOrDefault)})`)
        button.style.setProperty("--background", `var(--${colorOrDefault})`)
        button.style.removeProperty("background-color");
        if (opts.disabled) {
            button.disabled = opts.disabled;
            button.style.setProperty("--hoverColor", `var(--${opts.color})`);
        } else {
            button.style.setProperty("--hoverColor", `var(--${opts.hoverColor ?? selectHoverColor(opts.color ?? "primaryMain")})`)
        }
    }


    const timeout = {id: -1};
    const timer = {time: holdToConfirmTime};
    const TEMP_STARTED_KEY = Math.random() + new Date().getTime();

    function end() {
        button.classList.remove("process");
        if (timeout.id !== -1) {
            window.clearTimeout(timeout.id);
        }

        if (timer.time > holdToConfirmTime - shakeDelta && startedMap[TEMP_STARTED_KEY]) {
            for (let i = 0; i < button.children.length; i++) {
                button.children.item(i)?.classList.add("shaking");
            }
            button.classList.add("hint");
            setTimeout(() => {
                button.classList.remove("hint");
                for (let i = 0; i < button.children.length; i++) {
                    button.children.item(i)?.classList.remove("shaking");
                }
            }, holdToConfirmTime - shakeDelta);
        }
        timer.time = holdToConfirmTime;
        startedMap[TEMP_STARTED_KEY] = false;
    }

    function start() {
        if (button.classList.contains("process")) return;
        if (timeout.id !== -1) {
            window.clearTimeout(timeout.id);
            timeout.id = -1;
        }

        button.classList.remove("success");
        button.classList.add("process");
        startedMap[TEMP_STARTED_KEY] = true;
        timeout.id = window.setTimeout(success, tickRate);
    }

    function success() {
        timer.time -= tickRate;
        if (timer.time <= 0) {
            button.classList.add("success");
            end();
            setTimeout(() => {
                action();
            }, actionDelay);
        } else {
            timeout.id = window.setTimeout(success, tickRate);
        }
    }

    button.onmousedown = start;
    button.ontouchstart = start;
    button.onmouseleave = end;
    button.onmouseup = end;
    button.ontouchend = end;
    button.onclick = e => e.stopImmediatePropagation();
    button.type = "button";

    const slotEl = document.createElement("span");
    slotEl.className = "icon-slot";
    slotEl.append(icon);

    const icons = divHtml(`
        <svg class="progress" viewBox="0 0 32 32">
            <circle r="8" cx="16" cy="16" />
        </svg>
        <svg class="tick" viewBox="0 0 24 24">
            <polyline points="18,7 11,16 6,12" />
        </svg>
    `);

    icons.classList.add("icons");
    slotEl.append(icons);
    button.append(slotEl);

    const stack = document.createElement("span");
    stack.className = "text-stack";

    const mainLine = document.createElement("span");
    mainLine.className = "line-main";
    mainLine.innerText = actionText;
    stack.append(mainLine);

    const holdLine = document.createElement("span");
    holdLine.className = "line-hold";
    holdLine.setAttribute("aria-hidden", "true");
    holdLine.innerText = "Hold to confirm";
    stack.append(holdLine);

    const doneLine = document.createElement("span");
    doneLine.className = "line-done";
    doneLine.setAttribute("aria-hidden", "true");
    doneLine.innerText = "Done";
    stack.append(doneLine);

    button.append(stack);

    return button;
}
