import {IconName} from "./Icon";
import {ThemeColor} from "./theme";

export type Filter = FilterWithOptions | FilterCheckbox | FilterInput | MultiOptionFilter;

export interface FilterInput {
    type: "input",
    key: string;
    text: string;
    icon: IconName;
}

export interface FilterWithOptions {
    type: "options";
    key: string;
    text: string;
    clearable: boolean;
    options: FilterOption[];
    icon: IconName;
}

export interface FilterCheckbox {
    type: "checkbox";
    key: string;
    text: string;
    icon: IconName;
}

export interface FilterOption {
    text: string;
    icon: IconName;
    color: ThemeColor;
    value: string;
}

export interface MultiOptionFilter {
    type: "multi-option";
    keys: [string, string];
    text: string;
    clearable: boolean;
    options: MultiOption[];
    icon: IconName;
}

export interface MultiOption {
    text: string;
    icon: IconName;
    color: ThemeColor;
    values: [string, string];
}

export function filterOption(text: string, value: string, icon: IconName, color: ThemeColor): FilterOption {
    return {
        text, icon, color, value
    };
}

export const SORT_BY = "sortBy";
export const SORT_DIRECTION = "sortDirection";
export const ASC = "ascending";
export const DESC = "descending";