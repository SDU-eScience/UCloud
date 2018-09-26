import { HeaderSearchType } from "DefaultObjects";
import { Upload } from "Uploader";
declare module "*.png";

export type StatusLevel = "NO ISSUES" | "MAINTENANCE" | "UPCOMING MAINTENANCE" | "ERROR";

export interface Status {
    title: string
    level: StatusLevel
    body: string
}

interface HeaderStateToProps {
    sidebar: {
        open: boolean
    }
    header: {
        prioritizedSearch: HeaderSearchType
    }
}