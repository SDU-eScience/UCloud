import {GetElementType, PropType} from "UtilityFunctions";
import * as UCloud from "UCloud";

export {Browse} from "./Browse";
export type AclPermission = NonNullable<GetElementType<PropType<UCloud.compute.LicenseAclEntry, "permissions">>>;

