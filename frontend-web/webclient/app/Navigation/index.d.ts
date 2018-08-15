import { HeaderSearchType} from "DefaultObjects";

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