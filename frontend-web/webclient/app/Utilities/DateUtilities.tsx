import * as moment from "moment";
import { tz } from "moment-timezone";

export const dateToString = (date: number) => tz(date, "Europe/Copenhagen").format("YYYY-M-D H:mm:ss");