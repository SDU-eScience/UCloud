import {buildQueryString} from "@/Utilities/URIUtilities";

export function searchPage(priority: string, options: string | Record<string, string>): string {
    let optionRecord: Record<string, string>;
    if (typeof options === "string") {
        optionRecord = {query: options};
    } else {
        optionRecord = options;
    }

    return buildQueryString(`/search/${encodeURIComponent(priority)}`, optionRecord);
}