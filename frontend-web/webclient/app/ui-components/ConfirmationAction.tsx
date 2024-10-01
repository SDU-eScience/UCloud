import * as React from "react";
import {Button} from "@/ui-components/index";
import {CSSProperties, useCallback, useLayoutEffect, useRef, useState} from "react";
import {ButtonClass, ButtonProps} from "@/ui-components/Button";
import Icon, {IconName} from "@/ui-components/Icon";
import {doNothing} from "@/UtilityFunctions";
import {selectContrastColor, selectHoverColor, ThemeColor} from "@/ui-components/theme";
import {classConcat, injectStyle} from "@/Unstyled";
import {div} from "@/Utilities/HTMLUtilities";

const ConfirmButtonClass = injectStyle("confirm-button", k => `
    ${k} {
        --progress-border: var(--backgroundDefault, #f00);
        --progress-active: var(--textPrimary, #f00);
        --progress-success: white;
        --color: var(--errorContrast);
        --background: var(--errorMain, #f00);
        --tick-stroke: white;
        
        outline: none;
        user-select: none;
        -webkit-user-select: none;
        cursor: pointer;
        backface-visibility: hidden;
        min-width: 200px;
        background: var(--background, #f00);
        font-size: 16px;
        font-weight: 500;
        transform: scale(1);
    }
    
    ${k}[data-square="true"]:hover {
        --progress-border: var(--hoverColor, var(--primaryMain));
        --background: var(--hoverColor, var(--primaryMain)) !important;
    }
    
    ${k}[data-square="true"] {
        border-radius: 0;
        min-width: 200px;
        font-weight: 400;
    }
    
    ${k}[data-no-text="true"] {
        min-width: 50px;
    }
    
    ${k} > .icons {
        border-radius: 50%;
        top: 9px;
        left: 15px;
        position: absolute;
        overflow-y: hidden;
        transition: transform .3s, opacity .2s;
        opacity: var(--icon-o, 0);
        transform: translateX(var(--icon-x, -4px));
    }
    
    ${k} > .icons:before {
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
    
    ${k} ul {
        padding: 0;
        margin: 0;
        pointer-events: none;
        list-style: none;
        min-width: 80%;
        backface-visibility: hidden;
        transition: transform .3s;
        position: relative;
        
        /* this has custom styling based on too many properties, set them inline */
    }
    
    ${k} .shaking {
        transform: translate3d(0, 0, 0);
        animation: button-shake 0.82s cubic-bezier(.36, .07, .19, .97) both;
    }
    
    ${k}:disabled:hover {
        background: var(--hoverColor);
    }

    ${k} ul li {
        backface-visibility: hidden;
        transform: translateY(var(--ul-y)) translateZ(0);
        transition: transform .3s ease .16s, opacity .2s ease .16s;
    }

    ${k} ul li:not(:first-child) {
        --o: 0;
        position: absolute;
        left: 0;
        right: 0;
    }
    
    ${k} ul li:nth-child(1) {
        opacity: var(--ul-o-1, 1);
    }

    ${k} ul li:nth-child(2) {
        top: 100%;
        opacity: var(--ul-o-2, 0);
    }

    ${k}.process {
        --icon-x: 0;
    }

    ${k}.process,
    ${k}.success {
        --icon-o: 1;
        --progress-array: 52;
    }

    ${k}.process > .ucloud-native-icons, ${k}.success > .ucloud-native-icons, ${k}.success .progress {
        opacity: 0;
    }

    ${k} .ucloud-native-icons {
        position: absolute;
        left: 15px;
    }
    
    ${k}[data-square="true"]:hover {
        transform: scale(1);
    }
    
    ${k}.success {
        --icon-x: 6px;
        --progress-border: none;
        --progress-scale: .11;
        --tick-stroke: white;
        --background-scale: 0;
        --tick-offset: 36;
        --ul-y: -100%;
        --ul-o-1: 0;
        --ul-o-2: 1;
    }

    ${k}.success > .icons svg.progress {
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
    align?: "left" | "center",
    actionKey?: string;
    onAction?: (actionKey?: string) => void;
    hoverColor?: ThemeColor;
    disabled?: boolean;
}> = props => {
    const buttonRef = useRef<HTMLButtonElement>(null);
    const timeout = useRef(-1);
    const timer = useRef(holdToConfirmTime);
    const [showHelp, setShowHelp] = useState(false);
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
            setShowHelp(true);
            setTimeout(() => {
                setShowHelp(false);
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
        button.style.setProperty("--progress-border", `var(--${selectHoverColor(colorOrDefault)})`)
        button.style.setProperty("--background", `var(--${colorOrDefault})`)
        button.style.removeProperty("background-color");
        button.setAttribute("data-no-text", (!props.actionText).toString());
    }, [buttonRef.current, props.actionText, props.hoverColor, props.color, props.textColor]);

    const passedProps = {...props};
    delete passedProps.onAction;

    const ulStyle: CSSProperties = {};
    if (props.align === "left" && props.asSquare) ulStyle.marginLeft = "34px";
    if (props.align !== "center") {
        ulStyle.textAlign = "left";
    } else {
        ulStyle.textAlign = "center";
    }

    return <Button
        {...passedProps}
        onMouseDown={start}
        onTouchStart={start}
        onMouseEnter={() => setShowHelp(true)}
        onMouseLeave={() => {
            setShowHelp(false);
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
        {!props.icon ? null : <div className={"ucloud-native-icons"}>
            <Icon name={props.icon} size={"20"} mb="3px" />
        </div>}
        <div className={"icons"}>
            <svg className="progress" viewBox="0 0 32 32">
                <circle r="8" cx="16" cy="16" />
            </svg>
            <svg className="tick" viewBox="0 0 24 24">
                <polyline points="18,7 11,16 6,12" />
            </svg>
        </div>
        {!props.actionText ? null : (
            <ul style={ulStyle}>
                <li>{showHelp ? "Hold to confirm" : props.actionText}</li>
                <li>Done</li>
            </ul>
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
        button.setAttribute("data-attached", "false");
        button.setAttribute("data-square", (!!opts.asSquare).toString());
        button.setAttribute("data-fullwidth", "false");
        button.setAttribute("data-size", "standard");

        const colorOrDefault = opts.color ?? "errorMain";

        button.style.setProperty("--duration", `${holdToConfirmTime}ms`);
        button.style.setProperty("--hoverColor", `var(--${opts.hoverColor ?? selectHoverColor(colorOrDefault)})`)
        button.style.setProperty("--color", `var(--${opts.textColor ?? selectContrastColor(colorOrDefault)})`)
        button.style.setProperty("--progress-border", `var(--${selectHoverColor(colorOrDefault)})`)
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
            const firstLi = button.querySelector("li");
            if (firstLi) firstLi.innerText = "Hold to confirm"
            setTimeout(() => {
                if (firstLi) firstLi.innerText = actionText ?? "";
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
    button.onmouseenter = () => {
        actionTextLi.innerText = "Hold to confirm";
    };
    button.onmouseleave = () => {
        actionTextLi.innerText = actionText;
        end();
    };
    button.onmouseup = end;
    button.ontouchend = end;
    button.onclick = e => e.stopImmediatePropagation();
    button.type = "button";

    const divEl = document.createElement("div");
    divEl.className = "ucloud-native-icons";
    divEl.append(icon);
    button.append(divEl);

    const icons = div(`
        <svg class="progress" viewBox="0 0 32 32">
            <circle r="8" cx="16" cy="16" />
        </svg>
        <svg class="tick" viewBox="0 0 24 24">
            <polyline points="18,7 11,16 6,12" />
        </svg>
    `);

    icons.classList.add("icons");
    button.append(icons);

    const ul = document.createElement("ul");
    if (opts.align === "left" && opts.asSquare) ul.style.marginLeft = "34px";
    if (opts.align !== "left") {
        ul.style.textAlign = "center";
    } else {
        ul.style.textAlign = "left";
    }

    ul.style.maxHeight = "40px";
    ul.style.overflowY = "hidden";

    const actionTextLi = document.createElement("li");
    actionTextLi.innerText = actionText;
    ul.append(actionTextLi);
    const doneTextLi = document.createElement("li");
    doneTextLi.innerText = "Done";
    ul.append(doneTextLi);
    button.append(ul);

    return button;
}
