export interface DataListItem {
    key: string;
    value: string;
    tags: string;
    unselectable?: boolean;
}

export interface KnownDepartmentGroup {
    faculty: string;
    departments?: string[];
    freetext?: boolean;
}

export type KnownDepartmentsEntry = "freetext" | KnownDepartmentGroup[];

export type KnownDepartmentsMap = Record<string, KnownDepartmentsEntry>;

export type OrganizationMapping = Record<string, string>;
