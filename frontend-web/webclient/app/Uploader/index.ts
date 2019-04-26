import Uploader from "./Uploader";
export { Uploader };
import { Sensitivity } from "DefaultObjects";
import { File as SDUCloudFile } from "Files";
import { UploadPolicy } from "./api";
import { AddSnackOperation } from "Snackbar/Snackbars";


export interface Upload {
    file: File
    isUploading: boolean
    progressPercentage: number
    extractArchive: boolean
    sensitivity: Sensitivity
    uploadXHR?: XMLHttpRequest
    conflictFile?: SDUCloudFile
    resolution: UploadPolicy
    uploadEvents: { progressInBytes: number, timestamp: number }[]
    isPending: boolean
    parentPath: string
    error?: string
}

export interface UploaderStateProps {
    activeUploads: Upload[]
    error?: string
    visible: boolean
    uploads: Upload[]
    allowMultiple?: boolean
    location: string
    loading: boolean
    onFilesUploaded?: (path: string) => void
}

export interface UploadOperations extends AddSnackOperation {
    setUploads: (uploads: Upload[]) => void
    setUploaderError: (err?: string) => void
    setUploaderVisible: (visible: boolean) => void
    setLoading: (loading: boolean) => void
}

export type UploaderProps = UploadOperations & UploaderStateProps;