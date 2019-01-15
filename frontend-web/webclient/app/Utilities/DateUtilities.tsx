import { tz } from "moment-timezone";

// Could potentially cause issues with time if user is outside CEST
export const dateToString = (date: number) => tz(date, "Europe/Copenhagen").format("HH:mm L");

export const dateComparison = (d1: Date, d2: Date) => ((d1 && d1.getTime()) || 0) - ((d2 && d2.getTime()) || 0)