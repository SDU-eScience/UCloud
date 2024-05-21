export function div(html: string): HTMLDivElement {
    const elem = document.createElement("div");
    elem.innerHTML = html;
    return elem;
}

export function image(src: string, opts?: {alt?: string; height?: number; width?: number;}): HTMLImageElement {
    const result = new Image();
    result.src = src;
    result.alt = opts?.alt ?? "Icon";
    if (opts?.height != null) result.height = opts.height;
    if (opts?.width != null) result.width = opts.width;
    return result;
}

export function findDomAttributeFromAncestors(target: EventTarget, attribute: string): string | null {
    const elem = target as HTMLElement;
    const attr = elem.getAttribute(attribute)
    if (attr) return attr;
    const parent = elem.parentElement;
    if (parent) return findDomAttributeFromAncestors(parent, attribute);
    return null;
}

export function measureTextWidth(text: string, fontSize: string): number {
    const span = document.createElement("span");
    span.style.fontSize = fontSize;
    span.innerText = text;
    span.style.display = "block";
    span.style.width = "max-content";
    document.body.appendChild(span)
    const width = span.clientWidth;
    document.body.removeChild(span);
    return width;
}
