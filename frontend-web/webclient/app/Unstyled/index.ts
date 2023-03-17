import {BoxProps} from "@/ui-components/Box";
import {CSSProperties} from "react";
import {ResponsiveValue} from "styled-system";
import * as React from "react";

export function extractSize(size: ResponsiveValue<any>): string {
    if (typeof size === "string") return size;
    return `${size}px`;
}

export function unbox(props: BoxProps): CSSProperties {
    let result: CSSProperties = {};

    const px = props.px ?? props.paddingX;
    const py = props.py ?? props.paddingY;
    const pl = props.pl ?? props.paddingLeft;
    const pr = props.pr ?? props.paddingRight;
    const pt = props.pt ?? props.paddingTop;
    const pb = props.pb ?? props.paddingBottom;
    const p = props.p ?? props.padding;

    if (px) {
        result.paddingLeft = extractSize(px);
        result.paddingRight = extractSize(px);
    }

    if (py) {
        result.paddingTop = extractSize(py);
        result.paddingBottom = extractSize(py);
    }

    if (pt) result.paddingTop = extractSize(pt);
    if (pr) result.paddingRight = extractSize(pr);
    if (pb) result.paddingBottom = extractSize(pb);
    if (pl) result.paddingLeft = extractSize(pl);
    if (p) result.padding = extractSize(p);

    const mx = props.mx ?? props.marginX;
    const my = props.my ?? props.marginY;
    const ml = props.ml ?? props.marginLeft;
    const mr = props.mr ?? props.marginRight;
    const mt = props.mt ?? props.marginTop;
    const mb = props.mb ?? props.marginBottom;
    const m = props.m ?? props.margin;

    if (mx) {
        result.marginLeft = extractSize(mx);
        result.marginRight = extractSize(mx);
    }

    if (my) {
        result.marginTop = extractSize(my);
        result.marginBottom = extractSize(my);
    }

    if (mt) result.marginTop = extractSize(mt);
    if (mr) result.marginRight = extractSize(mr);
    if (mb) result.marginBottom = extractSize(mb);
    if (ml) result.marginLeft = extractSize(ml);
    if (m) result.margin = extractSize(m);

    const bg = props.bg ?? props.background;
    if (bg) result.background = bg.toString();

    if (props.borderRadius) result.borderRadius = extractSize(props.borderRadius);
    if (props.zIndex) result.zIndex = props.zIndex.toString();
    if (props.color) result.color = props.color.toString();
    if (props.width) result.width = extractSize(props.width);
    if (props.height) result.height = extractSize(props.height);
    if (props.maxWidth) result.maxWidth = extractSize(props.maxWidth);
    if (props.minWidth) result.minWidth = extractSize(props.minWidth);
    if (props.maxHeight) result.maxHeight = extractSize(props.maxHeight);
    if (props.minHeight) result.minHeight = extractSize(props.minHeight);
    if (props.overflow) result.overflow = props.overflow.toString();
    if (props.overflowY) result.overflowY = props.overflowY.toString() as any;
    if (props.overflowX) result.overflowX = props.overflowX.toString() as any;
    if (props.alignItems) result.alignItems = props.alignItems.toString() as any;
    if (props.justifyContent) result.justifyContent = props.justifyContent.toString() as any;
    if (props.flexGrow) result.flexGrow = props.flexGrow.toString();
    if (props.flexShrink) result.flexShrink = props.flexShrink.toString();
    if (props.textAlign) result.textAlign = props.textAlign.toString() as any;
    if (props.cursor) result.cursor = props.cursor;

    return result;
}

let styleIdCounter = 0;
const styleTag = document.createElement("style");
document.head.append(styleTag);

export function injectStyle(title: string, fn: (k: string) => string): string {
    const className = `${title}${styleIdCounter++}`;
    const styleSheet = fn("." + className);
    styleTag.innerHTML += styleSheet;
    return className;
}

export function injectStyleSimple(title: string, fn: () => string): string {
    return injectStyle(title, (k) => `
        ${k} {
            ${fn()}
        }
    `)
}

export type WithEventHandlers = Omit<Omit<React.DOMAttributes<any>, "dangerouslySetInnerHTML">, "children">;
export function extractEventHandlers(props: WithEventHandlers): WithEventHandlers {
    const result: WithEventHandlers = {};
    for (const key of Object.keys(props)) {
        if (key.startsWith("on")) {
            result[key] = props[key];
        }
    }
    return result;
}
