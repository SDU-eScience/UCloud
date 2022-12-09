export function style(style) {
    const result = document.createElement("style");
    result.textContent = style;
    return result;
}
