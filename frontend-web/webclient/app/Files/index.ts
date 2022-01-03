import * as UCloud from "@/UCloud";
import FileApi = UCloud.file.orchestrator;
import {GetElementType, PropType} from "@/UtilityFunctions";

export type FileType = NonNullable<PropType<FileApi.UFile, "type">>;
export type FilePermission = GetElementType<NonNullable<PropType<FileApi.UFileNS.Permissions, "myself">>>;
export type FileIconHint = NonNullable<PropType<FileApi.UFile, "icon">>;
