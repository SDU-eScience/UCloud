import {compRgbToHsl, hslToRgb} from "@/ui-components/GlobalStyle";
import * as AppStore from "@/Applications/AppStoreApi";
import {callAPI} from "@/Authentication/DataHook";
import React, {useEffect, useMemo, useState} from "react";
import Box from "@/ui-components/Box";
import {height} from "styled-system";

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
    /*
    const ids = [
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
    const ids = [30]

    document.querySelector("#output")?.remove();
    const output = document.createElement("output");
    output.id = "output";
    document.querySelector("[data-component=main]")?.append(output);

    for (const id of ids) {
        try {
            const group = await callAPI(AppStore.retrieveGroup({id}));
            const logo = await fetchImage(id);
            {
                const [canvas, color] = generateLogoWithText(group.specification.title, logo);
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

export const LogoWithText: React.FunctionComponent<{
    groupId: number;
    title: string;
    size: number;
    forceUnder?: boolean;
}> = ({title, groupId, size, forceUnder}) => {
    const [element, setElement] = useState<string | null>(null);
    useEffect(() => {
        let didCancel = false;

        if (hasTextInLogo[groupId]) {
            setElement(AppStore.retrieveGroupLogo({id: groupId}));
        } else {
            (async () => {
                const image = await fetchImage(groupId)
                const [, , logo] = generateLogoWithText(title, image, forceUnder);
                if (!didCancel) setElement(logo);
            })();
        }

        return () => {
            didCancel = true;
        };
    }, [groupId]);

    if (element === null) {
        return <Box height={size}/>;
    } else {
        return <img src={element} alt={title} style={{maxHeight: `calc(100% - 32px)`, maxWidth: "calc(100% - 32px)"}}/>;
    }
}

export function generateLogoWithText(
    title: string,
    input: HTMLImageElement,
    forceUnder?: boolean
): [HTMLCanvasElement, string, string] {
    const scaledImageCanvas = document.createElement("canvas");
    const aspectRatio = input.width / input.height;
    scaledImageCanvas.height = 128;
    scaledImageCanvas.width = aspectRatio * scaledImageCanvas.height;
    console.log(scaledImageCanvas.height, scaledImageCanvas.width)
    const gs = scaledImageCanvas.getContext("2d")!;

    gs.imageSmoothingEnabled = true;
    gs.imageSmoothingQuality = "high"

    gs.clearRect(0, 0, scaledImageCanvas.width, scaledImageCanvas.height);
    gs.drawImage(input, 0, 0, scaledImageCanvas.width, scaledImageCanvas.height);
    const bucketSize = 1;

    const histogram: Record<number, number> = {};
    const imageData = gs.getImageData(0, 0, scaledImageCanvas.width, scaledImageCanvas.height);
    let firstImageX = 10000000;
    let firstImageY = 10000000;
    let lastImageX = 0;
    let lastImageY = 0;
    for (let i = 0; i < imageData.data.length; i += 4) {
        let r = imageData.data[i];
        let g = imageData.data[i + 1];
        let b = imageData.data[i + 2];
        const a = imageData.data[i + 3];
        if (a <= 1) continue;
        if (a < 255 && a > 0) {
            const bgChannel = 255;

            const nA = a / 255;

            r = (1 - nA) * bgChannel + nA * r;
            g = (1 - nA) * bgChannel + nA * g;
            b = (1 - nA) * bgChannel + nA * b;
        }

        if (a < 255) continue;

        let x = Math.floor((i / 4) % imageData.width);
        let y = Math.floor((i / 4) / imageData.width);
        if (r !== 255 && g !== 255 && b !== 255) {
            if (x < firstImageX) firstImageX = x;
            if (y < firstImageY) firstImageY = y;

            if (x > lastImageX) lastImageX = x;
            if (y > lastImageY) lastImageY = y;
        }

        let [h, s, l] = compRgbToHsl(r, g, b);
        h = Math.floor(h * 255) / bucketSize;
        s = Math.floor(s * 255) / bucketSize;
        l = Math.floor(l * 255) / bucketSize;
        const key = (h << 16) | (s << 8) | l;
        histogram[key] = (histogram[key] ?? 0) + 1;
    }

    console.log(firstImageX, firstImageY, lastImageX, lastImageY, imageData.width, imageData.height);

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

            const looksLikeWhite = maxS < 0.15 && maxS > 0.8
            if (count > max && maxS > 0.10 && !looksLikeWhite) {
                maxHsl = [maxH, maxS, maxL];
                max = count;
            }
        }
    }

    if (max < 0.05 * sortedEntries[0][1]) {
        for (const [key, _] of sortedEntries) {
            const k = parseInt(key);
            const maxH = (((k >> 16) & 0xFF) * bucketSize) / 255;
            const maxS = (((k >> 8) & 0xFF) * bucketSize) / 255;
            const maxL = (((k >> 0) & 0xFF) * bucketSize) / 255;

            const looksLikeWhite = (maxS < 0.15 && maxL > 0.5) || maxL > 0.8
            if (looksLikeWhite) continue
            maxHsl = [maxH, maxS, maxL];
            break;
        }
    }


    let canPlaceUnderAtSize = forceUnder ? 40 : -1;
    const font = "Inter";
    /*
    const desiredFontSizes = [38, 40, 42];
    for (const size of desiredFontSizes) {
        g.font = `${size}px ${font}`;
        const width = g.measureText(title).width;
        if (width < canvas.width) {
            canPlaceUnderAtSize = size;
        }
    }
     */


    const canvas = document.createElement("canvas");
    if (canPlaceUnderAtSize > 0) {
        let textWidth = 0;
        {
            gs.font = `${canPlaceUnderAtSize}px ${font}`;
            textWidth = gs.measureText(title).width;
        }

        canvas.width = Math.max(textWidth, imageWidth);
        canvas.height = canvas.height + canPlaceUnderAtSize * 1.5;
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
        const size = 40;
        const paddingX = 16;

        console.log("imageWidth = ", imageWidth, "imageHeight = ", imageHeight)

        const titleWords = title.split(" ");
        let currentLine = "";
        let linesOfText: string[] = [];
        let maxWidth = 0;
        gs.font = `${size}px ${font}`;
        for (const word of titleWords) {
            currentLine += word + " ";
            const newWidth = gs.measureText(currentLine).width;
            if (newWidth > maxWidth) maxWidth = newWidth;
            if (newWidth > 200) {
                linesOfText.push(currentLine.trim());
                currentLine = "";
            }
        }
        if (currentLine) linesOfText.push(currentLine.trim());

        const textHeight = ((linesOfText.length - 1) * 1.1 * size) + size;
        console.log(title, textHeight);
        const textWidth = maxWidth;

        canvas.width = imageWidth + paddingX + textWidth;
        canvas.height = Math.max(imageHeight, textHeight);
        canvas.style.width = `${canvas.width}px`;
        canvas.style.height = `${canvas.height}px`;
        const g = canvas.getContext("2d")!;


        g.imageSmoothingEnabled = true;
        g.imageSmoothingQuality = "high";

        g.font = `${size}px ${font}`;
        g.fillStyle = "black";
        g.fillRect(0, 0, 10, 10);

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
    }
    const dataUrl = canvas.toDataURL("image/png");
    return [canvas, hslToRgb(maxHsl[0], maxHsl[1], maxHsl[2]), dataUrl];
}