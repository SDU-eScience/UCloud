import {BulkRequest} from "UCloud/index";
import {apiCreate, apiDelete, apiRetrieve, apiUpdate} from "Authentication/DataHook";
import {FileMetadataTemplate} from "UCloud/MetadataNamespaceApi";

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
    createdAt: number;
    status: FileMetadataDocumentStatus;
    createdBy: string;
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
    { type: "approved", approvedBy: string } |
    { type: "pending" } |
    { type: "rejected", rejectedBy: string } |
    { type: "not_required" };

export interface FileMetadataRetrieveAllResponse {
    metadata: { path: string, metadata: FileMetadataDocument }[];
}

export interface FileMetadataHistory {
    templates: Record<string, FileMetadataTemplate>;
    metadata: Record<string, FileMetadataDocumentOrDeleted[]>;
}

class MetadataDocumentApi {
    private baseContext = "/api/files/metadata";

    create(request: BulkRequest<{ id: string, metadata: FileMetadataDocumentSpecification}>) {
        return apiCreate(request, this.baseContext);
    }

    move(request: BulkRequest<{oldId: string, newId: string}>) {
        return apiUpdate(request, this.baseContext, "move");
    }

    delete(request: BulkRequest<{id: string, templateId: string}>) {
        return apiDelete(request, this.baseContext);
    }

    retrieveAll(request: { parentPath: string }) {
        return apiRetrieve(request, this.baseContext, "all");
    }
}

export default new MetadataDocumentApi();