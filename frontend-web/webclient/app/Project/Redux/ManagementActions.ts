import { Cloud } from "Authentication/SDUCloudObject";
import { viewProjectMembers } from "Utilities/ProjectUtilities";

export const fetchProjectMembers = (id: string): Promise<{ type: string }> =>
    Cloud.get(viewProjectMembers(id)).then(it => ({ type: "" })).catch(() => ({ type: "" }));