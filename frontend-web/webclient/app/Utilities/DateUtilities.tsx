import {format, isToday} from "date-fns";

// Could potentially cause issues with time if user is outside CEST
export const dateToStringNoTime = (date: number): string => format(date, "dd/MM/yyyy");
export const dateToString = (date: number): string => format(date, "HH:mm dd/MM/yyyy");
export const dateToTimeOfDayString = (date: number): string => format(date, "HH:mm");
export const dateToTimeOfDayStringDetailed = (date: number): string => format(date, "HH:mm:ss.SSS");
export function dateToDateStringOrTime(date: number): string {
    if (isToday(date)) return dateToTimeOfDayString(date);
    else return dateToStringNoTime(date);
}

export const getEndOfDay = (d: Date): Date => {
    const copy = new Date(d);
    copy.setHours(23);
    copy.setMinutes(59)
    copy.setSeconds(59);
    copy.setMilliseconds(999);
    return copy;
}

export const getStartOfDay = (d: Date): Date => {
    const copy = new Date(d);
    copy.setHours(0);
    copy.setMinutes(0);
    copy.setSeconds(0);
    copy.setMilliseconds(0);
    return copy;
};

export const getStartOfWeek = (d: Date): Date => {
    const day = d.getDay();
    const diff = d.getDate() - day + (day === 0 ? -6 : 1);

    const copy = new Date(d);
    copy.setDate(diff);
    copy.setHours(0);
    copy.setMinutes(0);
    copy.setSeconds(0);
    copy.setMilliseconds(0);
    return copy;
};

export const getStartOfMonth = (d: Date): Date => {
    const copy = new Date(d);
    copy.setDate(1);
    copy.setHours(0);
    copy.setMinutes(0);
    copy.setSeconds(0);
    copy.setMilliseconds(0);
    return copy;
}
