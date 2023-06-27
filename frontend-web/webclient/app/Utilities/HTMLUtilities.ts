
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
