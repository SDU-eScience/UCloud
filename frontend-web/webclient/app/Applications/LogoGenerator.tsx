import {
    colorDistanceRgb,
    compRgbToHsl,
    contrast, grayScaleRgb,
    hexToRgb,
    hslToRgb, invertColorRgb,
    rgbToHex,
    rgbToHsl
} from "@/ui-components/GlobalStyle";
import * as AppStore from "@/Applications/AppStoreApi";
import {callAPI} from "@/Authentication/DataHook";
import React, {useEffect, useMemo, useState} from "react";
import Box from "@/ui-components/Box";
import {height} from "styled-system";
import {selectContrastColor, useIsLightThemeStored} from "@/ui-components/theme";
import {isLightThemeStored} from "@/UtilityFunctions";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";

function fetchImage(id: number): Promise<HTMLImageElement> {
    const image = document.createElement("img");
    image.src = AppStore.retrieveGroupLogo({id}) + "&" + Math.random();
    return new Promise((resolve, reject) => {
        image.onload = () => {
            resolve(image);
        };

        image.onerror = () => {
            reject("No such image!");
        };
    })
}

export async function testGenerator() {
    /*const ids = [
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10,
        11,
        12,
        13,
        14,
        15,
        16,
        17,
        18,
        19,
        20,
        21,
        22,
        23,
        24,
        25,
        26,
        27,
        28,
        29,
        30,
        31,
        32,
        33,
        34,
        35,
        36,
        37,
        38,
        39,
        40,
        41,
        42,
        43,
        44,
        45,
        46,
        47,
        48,
        49,
        50,
        51,
        52,
        53,
        54,
        55,
        56,
        57,
        58,
        59,
        60,
        61,
        62,
        63,
        64,
        65,
        66,
        67,
        68,
        69,
        70,
        71,
        72,
        73,
        74,
        75,
        76,
        77,
        78,
        79,
        80,
        81,
        82,
        83,
        84,
        85,
        86,
        87,
        88,
        89,
        90,
        91,
        92,
        93,
        94,
        95,
        96,
    ];
     */
    const ids = [2];

    document.querySelector("#output")?.remove();
    const output = document.createElement("output");
    output.id = "output";
    document.querySelector("[data-component=main]")?.append(output);

    for (const id of ids) {
        try {
            const group = await callAPI(AppStore.retrieveGroup({id}));
            const logo = await fetchImage(id);
            {
                const [canvas, color] = generateLogoWithText(group.specification.title, logo, true);
                const wrap = document.createElement("div");
                wrap.style.display = "flex";
                wrap.style.gap = "16px";

                logo.style.height = "128px";
                wrap.append(logo);

                const colorBox = document.createElement("div");
                colorBox.style.width = "128px";
                colorBox.style.height = "128px";
                colorBox.style.background = color;
                colorBox.style.color = "white";
                colorBox.append(color);
                wrap.append(colorBox);
                wrap.append(canvas);
                output.append(wrap);
            }
        } catch (e) {
        }
    }
}

const hasTextInLogo: Record<number, true> = {
    66: true, // gromacs
    28: true, // indico
    39: true, // jupyter
    29: true, // lammps
    10: true, // macs
    40: true, // mariadb
    41: true, // mongodb
    75: true, // mysql
    13: true, // otree
    67: true, // postgresql
    1: true, // robot operating system
    17: true, // shiny
    33: true, // siesta
    19: true, // stata
    34: true, // tracer
    20: true, // voila
    84: true, // whisper
};

type Replacements = Record<number, Record<string, string | "grayscale-invert" | "invert">>;
const darkModeReplacements: Replacements = {
    18: {
        "#3c3a3e": "#ffffff",
    },
    13: {
        "#ffffff": "",
        "#333333": "invert",
    },
    84: {
        "#000000": "invert",
    },
    39: {
        "#4e4e4e": "invert",
        "#9e9e9e": "invert",
    },
    72: {
        "#303030": "#7e7e7e"
    },
    41: {
        "#4e3528": "invert"
    },
    91: {
        "#000000": "invert"
    },
    80: {
        "#383838": "invert"
    },
    67: {
        "#000000": "invert",
    },
    74: {
        "#153057": "grayscale-invert"
    },
    34: {
        "#000000": "invert"
    },
    25: {
        "#000000": "invert"
    }
};

const lightModeReplacements: Replacements = {
    13: {
        "#ffffff": "",
    }
};

export const LogoWithText: React.FunctionComponent<{
    id: string | number;
    title: string;
    size: number;
    forceUnder?: boolean;
    hasText?: boolean;
}> = ({title, id, size, forceUnder, hasText}) => {
    const isLight = useIsLightThemeStored();
    const [element, setElement] = useState<string | null>(null);
    const allReplacements = isLight ? lightModeReplacements : darkModeReplacements;
    const groupReplacements = allReplacements[id] ?? {};

    useEffect(() => {
        const newElement = typeof id === "string" ? AppStore.retrieveAppLogo({
            name: id,
            darkMode: !isLight, includeText: true, placeTextUnderLogo: forceUnder === true
        }) : AppStore.retrieveGroupLogo({
            id: id,
            darkMode: !isLight,
            includeText: true,
            placeTextUnderLogo: forceUnder === true,
        });

        setElement(newElement);
    }, [id, hasText, isLight]);

    if (element === null) {
        return <Box height={size}/>;
    } else {
        return <img
            src={element}
            alt={title}
            style={{
                maxHeight: forceUnder ? "calc(100% - 32px)" : hasText ? `${size * 1.50}px` : `${size}px`,
                maxWidth: "calc(100% - 32px)",
            }}
        />
    }
}

export function generateLogoWithText(
    title: string,
    input: HTMLImageElement,
    forceUnder?: boolean,
    colorReplacements: Record<string, string> = {},
): [HTMLCanvasElement, string, string] {
    const scaledImageCanvas = document.createElement("canvas");
    const aspectRatio = input.width / input.height;
    scaledImageCanvas.height = 256;
    scaledImageCanvas.width = aspectRatio * scaledImageCanvas.height;
    const gs = scaledImageCanvas.getContext("2d")!;

    gs.imageSmoothingEnabled = true;
    gs.imageSmoothingQuality = "high"

    gs.clearRect(0, 0, scaledImageCanvas.width, scaledImageCanvas.height);
    gs.drawImage(input, 0, 0, scaledImageCanvas.width, scaledImageCanvas.height);
    const bucketSize = 1;

    const histogram: Record<number, number> = {};
    const imageData = gs.getImageData(0, 0, scaledImageCanvas.width, scaledImageCanvas.height);
    let didModify = false;
    let firstImageX = 10000000;
    let firstImageY = 10000000;
    let lastImageX = 0;
    let lastImageY = 0;
    for (let i = 0; i < imageData.data.length; i += 4) {
        let r = imageData.data[i];
        let g = imageData.data[i + 1];
        let b = imageData.data[i + 2];
        let a = imageData.data[i + 3];

        const oldRgb = rgbToHex(r, g, b);
        let newColor: string | null = null;
        for (const [find, replace] of Object.entries(colorReplacements)) {
            if (colorDistanceRgb(oldRgb, find) < 80) {
                newColor = replace;
            }
        }
        if (newColor !== null) {
            if (newColor === "") {
                a = 0;
            } else {
                if (newColor === "grayscale-invert") {
                    const [gr, gg, gb] = grayScaleRgb(r, g, b);
                    const newColorRgb = invertColorRgb(gr, gg, gb);
                    r = newColorRgb[0];
                    g = newColorRgb[1];
                    b = newColorRgb[2];
                } else if (newColor === "invert") {
                    const newColorRgb = invertColorRgb(r, g, b);
                    r = newColorRgb[0];
                    g = newColorRgb[1];
                    b = newColorRgb[2];
                } else {
                    const newColorRgb = hexToRgb(newColor);
                    r = newColorRgb[0];
                    g = newColorRgb[1];
                    b = newColorRgb[2];
                }
            }

            imageData.data[i] = r;
            imageData.data[i + 1] = g;
            imageData.data[i + 2] = b;
            imageData.data[i + 3] = a;
            didModify = true;
        }

        if (a <= 1) continue;
        if (a < 255 && a > 0) {
            const bgChannel = 255;

            const nA = a / 255;

            r = (1 - nA) * bgChannel + nA * r;
            g = (1 - nA) * bgChannel + nA * g;
            b = (1 - nA) * bgChannel + nA * b;
        }

        let x = Math.floor((i / 4) % imageData.width);
        let y = Math.floor((i / 4) / imageData.width);
        if (!(r === 255 && g === 255 && b === 255)) {
            if (x < firstImageX) firstImageX = x;
            if (y < firstImageY) firstImageY = y;

            if (x > lastImageX) lastImageX = x;
            if (y > lastImageY) lastImageY = y;
        }

        if (a < 255) continue;

        let [h, s, l] = compRgbToHsl(r, g, b);
        h = Math.floor(h * 255) / bucketSize;
        s = Math.floor(s * 255) / bucketSize;
        l = Math.floor(l * 255) / bucketSize;
        const key = (h << 16) | (s << 8) | l;
        histogram[key] = (histogram[key] ?? 0) + 1;
    }

    if (didModify) {
        gs.clearRect(0, 0, scaledImageCanvas.width, scaledImageCanvas.height);
        gs.putImageData(imageData, 0, 0);
    }

    // const imageWidth = imageData.width;
    // const imageHeight = imageData.height;
    const imageWidth = (lastImageX - firstImageX) + 1;
    const imageHeight = (lastImageY - firstImageY) + 1;

    let maxHsl: [number, number, number] = [0, 0, 0];
    let max = -1;
    const sortedEntries = Object.entries(histogram).sort((a, b) => {
        const aCount = a[1];
        const bCount = b[1];
        if (aCount > bCount) return -1;
        if (aCount < bCount) return 1;
        return 0;
    })

    let i = 0;
    for (const [key, count] of sortedEntries) {
        i++;
        if (count > max || count > 50) {
            const k = parseInt(key);
            const maxH = (((k >> 16) & 0xFF) * bucketSize) / 255;
            const maxS = (((k >> 8) & 0xFF) * bucketSize) / 255;
            const maxL = (((k >> 0) & 0xFF) * bucketSize) / 255;

            const backgroundColor = isLightThemeStored() ? "#ffffff" : getCssPropertyValue("backgroundCard");
            const rgbColor = hslToRgb(maxH, maxS, maxL);
            let number = contrast(backgroundColor, rgbColor);
            if (number >= 3.0) {
                maxHsl = [maxH, maxS, maxL];
                max = count;
            }
        }
    }

    if (max === -1) {
        maxHsl = rgbToHsl(getCssPropertyValue("textPrimary"));
    }

    let canPlaceUnderAtSize = forceUnder ? 60 : -1;
    const font = "Inter";

    const canvas = document.createElement("canvas");
    if (canPlaceUnderAtSize > 0) {
        let textWidth = 0;
        {
            gs.font = `${canPlaceUnderAtSize}px ${font}`;
            textWidth = gs.measureText(title).width;
        }

        if (title === "") {
            textWidth = 0;
            canPlaceUnderAtSize = 0;
        }

        canvas.width = Math.max(textWidth, imageWidth);
        canvas.height = imageHeight + canPlaceUnderAtSize * 1.2;
        const g = canvas.getContext("2d")!;

        const logoPadding = textWidth > imageWidth ? (textWidth - imageWidth) / 2 : 0;
        const textPadding = imageWidth > textWidth ? (imageWidth - textWidth) / 2 : 0;

        g.clearRect(0, 0, canvas.width, canvas.height);
        g.drawImage(
            scaledImageCanvas,
            firstImageX,
            firstImageY,
            lastImageX - firstImageX + 1,
            lastImageY - firstImageY + 1,
            logoPadding,
            0,
            imageWidth,
            imageHeight
        );
        g.font = `${canPlaceUnderAtSize}px ${font}`;

        g.imageSmoothingEnabled = true;
        g.imageSmoothingQuality = "high"

        g.fillStyle = hslToRgb(maxHsl[0], maxHsl[1], maxHsl[2]);
        g.fillText(title, textPadding, imageHeight + canPlaceUnderAtSize * 1.1);
    } else {
        const sizes = [120, 110, 100, 90, 80, 70, 60, 50];
        for (const size of sizes) {
            const paddingX = 30;

            const titleWords = title.split(" ");
            let currentLine = "";
            let linesOfText: string[] = [];
            let maxWidth = 0;
            gs.font = `${size}px ${font}`;
            if (titleWords.length === 2) {
                linesOfText = titleWords;
                for (const word of titleWords) {
                    const newWidth = gs.measureText(word).width;
                    if (newWidth > maxWidth) maxWidth = newWidth;
                }
            } else {
                for (const word of titleWords) {
                    currentLine += word + " ";
                    const newWidth = gs.measureText(currentLine.trim()).width;
                    if (newWidth > maxWidth) maxWidth = newWidth;
                    if (newWidth > 250) {
                        linesOfText.push(currentLine.trim());
                        currentLine = "";
                    }
                }
            }
            if (currentLine) linesOfText.push(currentLine.trim());

            let textHeight = ((linesOfText.length - 1) * 1.1 * size) + size;
            let textWidth = maxWidth;
            if (title === "") {
                textHeight = 0;
                textWidth = 0;
            }

            if (linesOfText.length === 1 && textHeight > imageHeight * 0.55) {
                continue;
            }
            if (linesOfText.length > 1 && textHeight > imageHeight * 0.80) {
                continue;
            }

            canvas.width = imageWidth + paddingX + textWidth;
            canvas.height = Math.max(imageHeight, textHeight);
            canvas.style.width = `${canvas.width}px`;
            canvas.style.height = `${canvas.height}px`;
            const g = canvas.getContext("2d")!;


            g.imageSmoothingEnabled = true;
            g.imageSmoothingQuality = "high";

            g.font = `${size}px ${font}`;
            g.fillStyle = "black";

            g.fillStyle = hslToRgb(maxHsl[0], maxHsl[1], maxHsl[2]);
            const paddingY = textHeight > imageHeight ? 0 : ((canvas.height - textHeight) / 2);
            g.textBaseline = "bottom"

            let textY = size + paddingY;
            g.font = `${size}px ${font}`;
            g.clearRect(0, 0, canvas.width, canvas.height);

            g.drawImage(
                scaledImageCanvas,
                firstImageX,
                firstImageY,
                imageWidth,
                imageHeight,
                0,
                0,
                imageWidth,
                imageHeight,
            );
            g.fillStyle = hslToRgb(maxHsl[0], maxHsl[1], maxHsl[2]);

            for (const line of linesOfText) {
                g.fillText(line, imageWidth + paddingX, textY);
                textY += size * 1.1;
            }
            break;
        }
    }
    const dataUrl = canvas.toDataURL("image/png");
    return [canvas, hslToRgb(maxHsl[0], maxHsl[1], maxHsl[2]), dataUrl];
}