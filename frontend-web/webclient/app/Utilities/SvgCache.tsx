import {AsyncCache} from "./AsyncCache";
import {useLayoutEffect, useRef} from "react";
import {createRoot} from "react-dom/client";
import * as React from "react";
import {getCssColorVar} from "@/Utilities/StyledComponentsUtilities";
import Icon, {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";

// NOTE(Dan): Now why are we doing all of this when we could just be using SVGs? Because they are slow. Not when we
// show off one or two SVGs, but when we start displaying 5 SVGs per item and the user is loading in hundreds of items.
// Then the entire thing becomes a mess really quickly. And this isn't just a matter of rendering fewer DOM elements, we
// are simply displaying too much vector graphics and the computer is definitely suffering under this. You could say
// that it is the job of the browser to do this operation for us, but it just seems like the browser is incapable of
// doing this well. This is most likely because the browser cannot guarantee that we do not attempt to update the SVG.
// However, we know for a fact that we are not modifying the SVG in any way, as a result, we know that it is perfectly
// safe to rasterize the entire thing and simply keep it as a bitmap.
export class SvgCache {
    private cache = new AsyncCache<string>();

    renderSvg(
        name: string,
        node: () => React.ReactElement,
        width: number,
        height: number,
        colorHint?: string,
    ): Promise<string> {
        return this.cache.retrieve(name, () => {
            // NOTE(Dan): This function is capable of rendering arbitrary SVGs coming from React and rasterizing them
            // to a canvas and then later a bitmap (image). It does this by creating a new React root, which is used
            // only to render the SVG once. Once the SVG has been rendered we send it to our rasterizer, cache the
            // result, and then tear down the React root along with any other associated resources.
            const fragment = document.createDocumentFragment();
            const root = createRoot(fragment);

            const promise = new Promise<string>((resolve, reject) => {
                const Component: React.FunctionComponent<{children: React.ReactNode}> = props => {
                    const div = useRef<HTMLDivElement | null>(null);
                    useLayoutEffect(() => {
                        const svg = div.current!.querySelector<SVGElement>("svg");
                        if (!svg) {
                            console.log("no svg!!!!");
                            reject();
                        } else {
                            this.rasterize(fragment.querySelector<SVGElement>("svg")!, width, height, colorHint)
                                .then(r => {
                                    resolve(r);
                                })
                                .catch(r => {
                                    reject(r);
                                });
                        }
                    }, []);

                    return <div ref={div}>{props.children}</div>;
                };

                root.render(<Component>{node()}</Component>);
            });

            return promise.finally(() => {
                root.unmount();
            });
        });
    }

    async renderIcon({name, color, color2, width, height}: {
        name: IconName,
        color: ThemeColor,
        color2: ThemeColor,
        width: number,
        height: number
    }): Promise<string> {
        const c1 = getCssColorVar(color);
        const c2 = color2 ? getCssColorVar(color) : undefined;
        return await this.renderSvg(
            `${name}-${c1}-${c2}-${width}-${height}`,
            () => <Icon name={name} color={c1} color2={c2} width={width} height={height} />,
            width,
            height,
            c1,
        );
    }

    private rasterize(data: SVGElement, width: number, height: number, colorHint?: string): Promise<string> {
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;

        // NOTE(Dan): For some reason, some of our SVGs don't have this. This technically makes them invalid, not sure
        // why the browsers allow it regardless.
        data.setAttribute("xmlns", "http://www.w3.org/2000/svg");

        // NOTE(Dan): CSS is not inherited, compute the real color.
        data.setAttribute("color", colorHint ?? window.getComputedStyle(data).color);

        // NOTE(Dan): The font-family is not (along with all other CSS) inherited into the image below. As a result,
        // we must embed all of these resources directly into the svg. For the font, we simply choose to use a simple
        // font similar to what we use. Note that it is complicated, although possible, to load the actual font. But
        // we would need to actually embed it in the style sheet (as in the font data, not just font reference).
        const svgNamespace = "http://www.w3.org/2000/svg";
        const svgStyle = document.createElementNS(svgNamespace, 'style');
        svgStyle.innerHTML = `
            text {
                font-family: Helvetica, sans-serif
            }
        `;
        data.prepend(svgStyle);

        const ctx = canvas.getContext("2d")!;

        const image = new Image();
        const svgBlob = new Blob([data.outerHTML], {type: "image/svg+xml;charset=utf-8"});
        const svgUrl = URL.createObjectURL(svgBlob);

        return new Promise((resolve, reject) => {
            image.onerror = (e) => {
                console.warn("SVG might be invalid.")
                reject(e);
            };

            image.onload = () => {
                ctx.drawImage(image, 0, 0);
                URL.revokeObjectURL(svgUrl);

                canvas.toBlob((canvasBlob) => {
                    if (!canvasBlob) {
                        reject();
                    } else {
                        resolve(URL.createObjectURL(canvasBlob));
                    }
                });
            };

            image.src = svgUrl;
        });
    }
}
