import format from "date-fns/esm/format";

// Could potentially cause issues with time if user is outside CEST
export const dateToString = (date: number) => format(date, "HH:mm dd/MM/yyyy");
