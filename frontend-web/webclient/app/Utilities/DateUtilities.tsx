import moment from "moment";
import { tz } from "moment-timezone";

// Could potentially cause issues with time if user is outside CEST
export const dateToString = (date: number) => tz(date, "Europe/Copenhagen").format("YYYY-M-D H:mm:ss");