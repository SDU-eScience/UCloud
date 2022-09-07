import * as React from "react";
import {ItemRenderer} from "@/ui-components/Browse";
import {ListRowStat} from "@/ui-components/List";
import {dateToString} from "@/Utilities/DateUtilities";
import {BulkRequest, FindByStringId, PaginationRequestV2} from "@/UCloud/index";
import {apiBrowse, apiCreate, apiDelete, apiRetrieve} from "@/Authentication/DataHook";
import { Operation } from "@/ui-components/Operation";
import { ResourceBrowseCallbacks } from "@/UCloud/ResourceApi";
import { Icon } from "@/ui-components";

export interface SSHKey {
    id: string;
    owner: string;
    createdAt: number;
    specification: SSHKeySpec;
}

export interface SSHKeySpec {
    title: string;
    key: string;
}

class SshKeyApi {
    public baseContext = "/api/ssh";
    public title = "SSH key"
    public titlePlural = "SSH keys"

    public renderer: ItemRenderer<SSHKey> = {
        Icon(props) {
            return <Icon name={"key"} size={props.size} />;
        },

        MainTitle({resource}) {
            return <>{resource?.specification?.title ?? ""}</>;
        },

        Stats({resource}) {
            if (resource == null) return null;
            return (
                <ListRowStat icon="calendar">{dateToString(resource.createdAt)}</ListRowStat>
            );
        }
    };

    public create(request: BulkRequest<SSHKeySpec>): APICallParameters {
        return apiCreate(request, this.baseContext);
    }

    public retrieve(request: FindByStringId): APICallParameters {
        return apiRetrieve(request, this.baseContext);
    }

    public browse(request: PaginationRequestV2) {
        return apiBrowse(request, this.baseContext);
    }

    public delete(request: BulkRequest<FindByStringId>) {
        return apiDelete(request, this.baseContext);
    }

    public retrieveOperations(): Operation<SSHKey, {}>[] {
        return [];
    }
}

export default new SshKeyApi();