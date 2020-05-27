import {format, formatDistance} from "date-fns/esm";
import {sizeToString} from "Utilities/FileUtilities";
import {humanReadableNumber} from "UtilityFunctions";
import * as DataTypes from "./DataTypes";

export interface Usage {
    usage: number;
    quota?: number;
    dataType?: string;
    title?: string;
}

export interface AccountingEvent {
    title: string;
    description?: string;
    timestamp: number;
}

export function formatDataType(type: string, value: any): string {
    if (typeof value !== "number") return "" + value;

    switch (type) {
        case DataTypes.BYTES: {
            return sizeToString(value);
        }

        case DataTypes.DATE: {
            return format(new Date(value), "dd/MM");
        }

        case DataTypes.DATETIME: {
            return format(new Date(value), "dd/MM hh:mm");
        }

        case DataTypes.DURATION: {
            if (value < 60_000) return `${(value / 1000) | 0} seconds`;
            return formatDistance(0, value);
        }
    }

    return "" + value;
}

export function formatDataTypeLong(type: string, value: any): string {
    switch (type) {
        case DataTypes.BYTES: {
            return `${humanReadableNumber(value, ",", ".", 0)} bytes`;
        }

        default: {
            return formatDataType(type, value);
        }
    }
}
