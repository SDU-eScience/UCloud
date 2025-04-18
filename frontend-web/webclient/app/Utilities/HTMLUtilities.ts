export function divHtml(html: string): HTMLDivElement {
    const elem = document.createElement("div");
    elem.innerHTML = html;
    return elem;
}

export function divText(text: string): HTMLDivElement {
    const elem = document.createElement("div");
    elem.innerText = text;
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
