import SDUCloud from "Authentication/lib";

export const saveAvatarQuery = "/avatar/update";
export const findAvatarQuery = "/avatar/find";
export const findAvatarBulk = (members: string[], cloud: SDUCloud) =>
    cloud.post(findAvatarBulkQuery, { usernames: members });

const findAvatarBulkQuery = "/avatar/find/bulk";
