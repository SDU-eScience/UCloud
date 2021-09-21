import {BulkRequest, PaginationRequestV2} from "@/UCloud/index";
import {apiBrowse, apiCreate, apiDelete, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import {FileMetadataTemplate} from "@/UCloud/MetadataNamespaceApi";

export type FileMetadataDocumentOrDeleted = FileMetadataDocument | FileMetadataDocumentDeleted;

export interface FileMetadataDocument {
    type: "metadata";
    id: string;
    specification: FileMetadataDocumentSpecification;
    createdAt: number;
    status: FileMetadataDocumentStatus;
    createdBy: string;
}

export interface FileMetadataDocumentDeleted {
    type: "deleted";
    id: string;
    createdAt: number;
    status: FileMetadataDocumentStatus;
    createdBy: string;
    changeLog: string;
}

export interface FileMetadataDocumentSpecification {
    templateId: string;
    version: string;
    document: Record<string, any>;
    changeLog: string;
}

export interface FileMetadataDocumentStatus {
    approval: FileMetadataDocumentApproval;
}

export type FileMetadataDocumentApproval =
    {type: "approved", approvedBy: string} |
    {type: "pending"} |
    {type: "rejected", rejectedBy: string} |
    {type: "not_required"};

export interface FileMetadataRetrieveAllResponse {
    metadata: {path: string, metadata: FileMetadataDocument}[];
}

export interface FileMetadataHistory {
    templates: Record<string, FileMetadataTemplate>;
    metadata: Record<string, FileMetadataDocumentOrDeleted[]>;
}

export interface FileMetadataAttached {
    path: string;
    metadata: FileMetadataDocument;
}

class MetadataDocumentApi {
    private baseContext = "/api/files/metadata";

    create(request: BulkRequest<{fileId: string, metadata: FileMetadataDocumentSpecification}>) {
        return apiCreate(request, this.baseContext);
    }

    move(request: BulkRequest<{oldFileId: string, newFileId: string}>) {
        return apiUpdate(request, this.baseContext, "move");
    }

    delete(request: BulkRequest<{id: string, changeLog: string}>) {
        return apiDelete(request, this.baseContext);
    }

    retrieveAll(request: {fileId: string}) {
        return apiRetrieve(request, this.baseContext, "all");
    }

    approve(request: BulkRequest<{id: string}>) {
        return apiUpdate(request, this.baseContext, "approve");
    }

    reject(request: BulkRequest<{id: string}>) {
        return apiUpdate(request, this.baseContext, "reject");
    }

    browse(request: {
        filterTemplate?: string,
        filterVersion?: string,
        filterActive: boolean,
    } & PaginationRequestV2) {
        return apiBrowse(request, this.baseContext)
    }
}

export default new MetadataDocumentApi();