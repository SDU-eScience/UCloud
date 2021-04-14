import * as UCloud from "UCloud";
import FileApi = UCloud.file.orchestrator;
import {PropType} from "UtilityFunctions";

export type FileType = NonNullable<PropType<FileApi.UFile, "type">>;
export type FileIconHint = NonNullable<PropType<FileApi.UFile, "icon">>;
