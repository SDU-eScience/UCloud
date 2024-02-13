import {compRgbToHsl, hexToRgb, hslToRgb, rgbToHex, rgbToHsl, tint} from "@/ui-components/GlobalStyle";
import * as AppStore from "@/Applications/AppStoreApi";

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

    document.querySelector("#output")?.remove();
    const output = document.createElement("output");
    output.id = "output";
    document.querySelector("[data-component=main]")?.append(output);

    for (const id of ids) {
        try {
            const logo = await fetchImage(id);
            {
                const [canvas, color] = generateLogoWithText(logo, false, id);
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
        } catch (e) {}
    }
}

export function generateLogoWithText(input: HTMLImageElement, debug: boolean, debugId?: number): [HTMLCanvasElement, string] {
    const canvas = document.createElement("canvas");
    const aspectRatio = input.width / input.height;
    canvas.width = aspectRatio * 256;
    canvas.height = 256;
    const g = canvas.getContext("2d")!;

    g.clearRect(0, 0, canvas.width, canvas.height);
    g.drawImage(input, 0, 0, canvas.width, canvas.height);
    const bucketSize = 5;

    const histogram: Record<number, number> = {};
    const imageData = g.getImageData(0, 0, canvas.width, canvas.width);
    for (let i = 0; i < imageData.data.length; i += 4) {
        let r = imageData.data[i];
        let g = imageData.data[i + 1];
        let b = imageData.data[i + 2];
        const a = imageData.data[i + 3];
        if (a <= 1) continue;
        if (a < 255 && a > 0) {
            const bgChannel = 255;

            const nA = a / 255;

            const oldR = r;
            const oldG = g;
            const oldB = b;
            r = (1 - nA) * bgChannel + nA * r;
            g = (1 - nA) * bgChannel + nA * g;
            b = (1 - nA) * bgChannel + nA * b;

            console.log(oldR, oldG, oldB, a, r, g, b);
        }
        if (a !== 255 && a !== 0) console.warn(debugId, "has alpha", a)
        if (a < 255) continue;

        let [h, s, l] = compRgbToHsl(r, g, b);
        h = Math.floor(h * 255) / bucketSize;
        s = Math.floor(s * 255) / bucketSize;
        l = Math.floor(l * 255) / bucketSize;

        const key = (h << 16) | (s << 8) | l;
        histogram[key] = (histogram[key] ?? 0) + 1;
    }

    document.querySelector("#logooutput")?.remove();

    const wrapper = document.createElement("div");
    wrapper.id = "logooutput";
    const flex = document.createElement("div");
    flex.style.display = "flex";
    flex.style.flexWrap = "wrap";
    wrapper.append(flex);
    let maxHsl: [number, number, number] = [0, 0, 0];
    let max = -1;
    let maxIdx = 0;
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


            const example = document.createElement("div");
            example.style.height = "50px";
            example.style.width = "50px";
            example.style.background = hslToRgb(maxH, maxS, maxL);
            example.append(count.toString());
            flex.append(example);

            if (count > max && maxS > 0.10 && maxL > 0.10) {
                maxHsl = [maxH, maxS, maxL];
                max = count;
                maxIdx = i - 1;
            }
        }
    }

    if (max < 0.1 * sortedEntries[0][1]) {
        const k = parseInt(sortedEntries[0][0]);
        const maxH = (((k >> 16) & 0xFF) * bucketSize) / 255;
        const maxS = (((k >> 8) & 0xFF) * bucketSize) / 255;
        const maxL = (((k >> 0) & 0xFF) * bucketSize) / 255;
        maxHsl = [maxH, maxS, maxL];
        maxIdx = 0;
        console.log(debugId, "is monochrome?");
    }

    if (debug) {
        document.querySelector("[data-component=main]")?.append(wrapper);

        wrapper.append(input);
        {
            const example = document.createElement("div");
            example.style.height = "100px";
            example.style.width = "100px";
            example.style.background = hslToRgb(maxHsl[0], maxHsl[1], maxHsl[2]);
            example.append(hslToRgb(maxHsl[0], maxHsl[1], maxHsl[2]));
            wrapper.append(example);
        }
    }

    // console.log(hslToRgb(maxHsl[0], maxHsl[1], maxHsl[2]))
    console.log(debugId, sortedEntries, maxIdx, imageData.colorSpace);
    return [canvas, hslToRgb(maxHsl[0], maxHsl[1], maxHsl[2])];
}