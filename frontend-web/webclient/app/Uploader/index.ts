import Uploader from "./Uploader";
export { Uploader };
import { Sensitivity } from "DefaultObjects";
import { File as SDUCloudFile } from "Files";
import { UploadPolicy } from "./api";


export interface Upload {
    file: File
    isUploading: boolean
    progressPercentage: number
    extractArchive: boolean
    sensitivity: Sensitivity
    uploadXHR?: XMLHttpRequest
    conflictFile?: SDUCloudFile
    resolution: UploadPolicy,
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