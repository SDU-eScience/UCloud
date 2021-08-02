import {format} from "date-fns/esm";

// Could potentially cause issues with time if user is outside CEST
export const dateToStringNoTime = (date: number) => format(date, "dd/MM/yyyy");
export const dateToString = (date: number) => format(date, "HH:mm dd/MM/yyyy");
export const dateToTimeOfDayString = (date: number) => format(date, "HH:mm");
