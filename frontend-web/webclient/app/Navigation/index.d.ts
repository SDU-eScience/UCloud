import { HeaderSearchType } from "DefaultObjects";
import { Upload } from "Uploader";

export interface Status {
    title: string
    level: string
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