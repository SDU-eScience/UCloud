import Uploader from "./Uploader";
export { Uploader };
import { Dispatch} from "redux";


export interface Upload {
    file: File
    isUploading: boolean
    progressPercentage: number
    extractArchive: boolean
    uploadXHR?: XMLHttpRequest
}

export interface UploaderProps {
    error?: string
    visible: boolean
    uploads: Upload[]
    allowMultiple?: boolean
    location: string
    onFilesUploaded?: (path: string) => void
    dispatch: Dispatch
}
