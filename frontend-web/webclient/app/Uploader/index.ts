import Uploader from "./Uploader";
export { Uploader };
import { Sensitivity } from "DefaultObjects";


export interface Upload {
    file: File
    isUploading: boolean
    progressPercentage: number
    extractArchive: boolean
    sensitivity: Sensitivity
    uploadXHR?: XMLHttpRequest
    uploadEvents: { progressInBytes: number, timestamp: number }[]
}

export interface UploaderStateProps {
    error?: string
    visible: boolean
    uploads: Upload[]
    allowMultiple?: boolean
    location: string
    onFilesUploaded?: (path: string) => void
}

export interface UploadOperations {
    setUploads: (uploads: Upload[]) => void
    setUploaderError: (err?: string) => void
    setUploaderVisible: (visible: boolean) => void
}

export type UploaderProps = UploadOperations & UploaderStateProps;