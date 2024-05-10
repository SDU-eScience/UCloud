import {BoxProps} from "@/ui-components/Types";
import * as React from "react";
import {ResponsiveValue, SpaceProps} from "styled-system";

export function extractSize(size: ResponsiveValue<any>): string {
    if (typeof size === "string") return size;
    if (size <= 1 && size >= 0) return `${size * 100}%`;
    return `${size}px`;
}

export function classConcat(baseClass: string, ...extra: (string | undefined)[]): string {
    return baseClass + " " + extra.filter(it => it).join(" ");
}

export function classConcatMult(baseClass: string, ...extra: (string | undefined)[]): string {
    return classConcat(baseClass, ...extra);
}

export function classConcatArray(baseClass: string, extra: string[]): string {
    return baseClass + " " + extra.join(" ");
}

// Note(Jonas): Graveyard of attempting to have typesafe sizes.
// type Percentage = `${number}%`;
// type Fraction = `${number}fr`;
// type ElementFontSize = `${number}em`;
// type RootElementFontSize = `${number}rem`;
// type Pixels = `${number}px`;
// type ViewHeight = `${number}vh`;
// type ViewWidth = `${number}vw`;
// type Zero = 0;
// type AnySize = Percentage | Fraction | ElementFontSize | RootElementFontSize | Pixels | ViewHeight | ViewWidth | Zero;
// type AllPaddingValue = AnySize | `${AnySize} ${AnySize}` | `${AnySize} ${AnySize} ${AnySize}` | `${AnySize} ${AnySize} ${AnySize} ${AnySize}`;
// export type AllMarginValue = AllPaddingValue;
// export type MarginValue = AnySize;
// export type PaddingValue = AnySize;
// export type WidthValue = AnySize;
// export type HeightValue = AnySize;

export function extractDataTags(props: object): Record<string, string> {
    const dataTags = Object.keys(props).filter(it => it.startsWith("data-"));
    const result: Record<string, string> = {};
    for (const t of dataTags) {
        result[t] = props[t];
    }
    return result;
}

export function unboxDataTags(props: Record<string, string>): Record<string, string> {
    const result = {};
    Object.entries(props).filter(([key]) => key.startsWith("data-")).forEach(it => {
        result[it[0]] = it[1];
    });
    return result;
}

export function unbox(props: BoxProps | SpaceProps): React.CSSProperties {
    let result: React.CSSProperties = {};

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

    if ("color" in props && props.color) {
        const stringified = props.color.toString();
        if (stringified.startsWith("#") || stringified.startsWith("rgb")) {
            result.color = stringified;
        } else {
            result.color = `var(--${stringified})`;
        }
    }
    if ("cursor" in props && props.cursor) result.cursor = props.cursor;

    if ("bg" in props || "background" in props) {
        const bg = props.bg ?? props.background;
        if (bg) result.background = bg.toString();
    }

    if ("borderRadius" in props && props.borderRadius) result.borderRadius = extractSize(props.borderRadius);
    if ("border" in props && props.border) result.border = props.border.toString();
    if ("zIndex" in props && props.zIndex) result.zIndex = props.zIndex.toString();
    if ("width" in props && props.width) result.width = extractSize(props.width);
    if ("height" in props && props.height) result.height = extractSize(props.height);
    if ("maxWidth" in props && props.maxWidth) result.maxWidth = extractSize(props.maxWidth);
    if ("minWidth" in props && props.minWidth) result.minWidth = extractSize(props.minWidth);
    if ("maxHeight" in props && props.maxHeight) result.maxHeight = extractSize(props.maxHeight);
    if ("minHeight" in props && props.minHeight) result.minHeight = extractSize(props.minHeight);
    if ("overflow" in props && props.overflow) result.overflow = props.overflow.toString();
    if ("overflowY" in props && props.overflowY) result.overflowY = props.overflowY.toString() as any;
    if ("overflowX" in props && props.overflowX) result.overflowX = props.overflowX.toString() as any;
    if ("alignItems" in props && props.alignItems) result.alignItems = props.alignItems.toString() as any;
    if ("justifyContent" in props && props.justifyContent) result.justifyContent = props.justifyContent.toString() as any;
    if ("flexGrow" in props && props.flexGrow) result.flexGrow = props.flexGrow.toString();
    if ("flexBasis" in props && props.flexBasis) result.flexBasis = props.flexBasis.toString();
    if ("flexShrink" in props && props.flexShrink != null) result.flexShrink = props.flexShrink.toString();
    if ("textAlign" in props && props.textAlign) result.textAlign = props.textAlign.toString() as any;
    if ("verticalAlign" in props && props.verticalAlign) result.verticalAlign = props.verticalAlign.toString() as any;
    if ("fontSize" in props && props.fontSize) result.fontSize = extractSize(props.fontSize);
    if ("backgroundColor" in props && props.backgroundColor) result.backgroundColor = props.backgroundColor.toString();
    if ("objectFit" in props && props.objectFit) result.objectFit = props.objectFit;
    if ("columnGap" in props && props.columnGap) result.columnGap = props.columnGap;
    if ("rowGap" in props && props.rowGap) result.rowGap = props.rowGap;

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

export function makeKeyframe(title: string, rules: string): string {
    const animationName = `${title}${styleIdCounter++}`;
    styleTag.innerHTML += `@keyframes ${animationName} {
        ${rules}
    }`;

    return animationName;
}

export function injectStyleSimple(title: string, css: string): string {
    return injectStyle(title, (k) => `
        ${k} {
            ${css}
        }
    `);
}

export function makeClassName(name: string) {
    return {
        get dot() {
            return `.${name}`
        },
        get class(): string {
            return name;
        }
    };
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

export function devModeStyling(cssRule: string): string {
    if (DEVELOPMENT_ENV) return cssRule;
    return "";
}
