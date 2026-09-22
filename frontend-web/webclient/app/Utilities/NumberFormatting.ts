export type DecimalSeparator = "." | ",";

const DECIMAL_SEPARATOR_STORAGE_KEY = "decimal-separator";

type DecimalSeparatorListener = () => void;

const decimalSeparatorListeners: Record<string, DecimalSeparatorListener> = {};

export function addDecimalSeparatorListener(key: string, op: DecimalSeparatorListener): void {
    decimalSeparatorListeners[key] = op;
}

export function removeDecimalSeparatorListener(key: string): void {
    delete decimalSeparatorListeners[key];
}

export function getDecimalSeparator(): DecimalSeparator {
    const stored = localStorage.getItem(DECIMAL_SEPARATOR_STORAGE_KEY);
    return stored === "," ? "," : ".";
}

export function setDecimalSeparator(separator: DecimalSeparator): void {
    localStorage.setItem(DECIMAL_SEPARATOR_STORAGE_KEY, separator);
    for (const listener of Object.values(decimalSeparatorListeners)) {
        listener();
    }
}

export interface NumberFormatOptions {
    precision?: number;
    removeTrailingZeros?: boolean;
    withThousandsSeparator?: boolean;
    threeDecimalsAs?: 2 | 4;
    minDecimalsAfterTrim?: number;
}

export function formatNumber(value: number, opts: NumberFormatOptions = {}): string {
    if (!Number.isFinite(value)) return value.toString(10);

    let precision = opts.precision;
    if (precision === 3) precision = opts.threeDecimalsAs ?? 2;

    let text = precision === undefined ? value.toString(10) : value.toFixed(precision);
    if (opts.removeTrailingZeros) {
        text = text.replace(/(\.\d*?)0+$/, "$1").replace(/\.$/, "");
        if (opts.minDecimalsAfterTrim !== undefined) {
            const dotIndex = text.indexOf(".");
            const decimals = dotIndex === -1 ? 0 : text.length - dotIndex - 1;
            if (decimals < opts.minDecimalsAfterTrim) {
                text = value.toFixed(opts.minDecimalsAfterTrim);
            }
        }
    }

    if (opts.withThousandsSeparator !== false) {
        text = applyNumberSeparators(text);
    }

    return text;
}

export function applyNumberSeparators(numericText: string): string {
    if (!/^-?\d+(\.\d*)?$/.test(numericText)) return numericText;

    const decimalSeparator = getDecimalSeparator();
    const thousandsSeparator = decimalSeparator === "." ? "," : ".";

    const dotIndex = numericText.indexOf(".");
    const integerPart = dotIndex === -1 ? numericText : numericText.substring(0, dotIndex);
    const fractionPart = dotIndex === -1 ? null : numericText.substring(dotIndex + 1);

    const isNegative = integerPart.startsWith("-");
    const digits = isNegative ? integerPart.substring(1) : integerPart;

    let grouped = "";
    for (let i = 0; i < digits.length; i++) {
        grouped += digits[i];
        const remaining = digits.length - i - 1;
        if (remaining > 0 && remaining % 3 === 0) grouped += thousandsSeparator;
    }

    let result = (isNegative ? "-" : "") + grouped;
    if (fractionPart !== null) result += decimalSeparator + fractionPart;
    return result;
}

export function formatPricePerMillionCredits(value: number, opts: {
    precision?: number;
    minDecimalsAfterTrim?: number;
} = {}): string {
    return formatNumber(value / 1_000_000, {
        precision: 6,
        removeTrailingZeros: true,
        withThousandsSeparator: true,
        ...opts,
    });
}
