import * as React from "react";
import {ItemRenderer, StandardCallbacks} from "@/ui-components/Browse";
import {ListRowStat} from "@/ui-components/List";
import {dateToString} from "@/Utilities/DateUtilities";
import {BulkRequest, FindByStringId, PaginationRequestV2} from "@/UCloud/index";
import {apiBrowse, apiCreate, apiDelete, apiRetrieve} from "@/Authentication/DataHook";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {Icon} from "@/ui-components";
import {bulkRequestOf} from "@/UtilityFunctions";

export interface SSHKey {
    id: string;
    owner: string;
    createdAt: number;
    fingerprint: string;
    specification: SSHKeySpec;
}

export interface SSHKeySpec {
    title: string;
    key: string;
}

interface ParsedFingerprint {
    keyLength: number;
    algorithmType: string;
    comment: string;
    hash: string;
}

function parseFingerprint(fingerprint: string): ParsedFingerprint | null {
    const tokens = fingerprint.trim().split(" ");
    if (tokens.length < 3) return null;

    // NOTE(Dan): Second half is doing a hacky check to make sure we are not parsing something stupid like "2aa" which
    // would result in 2 being returned from parseInt().
    const keyLength = parseInt(tokens[0]);
    if (isNaN(keyLength) || keyLength.toString().length !== tokens[0].length) return null;

    let algorithmType = tokens[tokens.length - 1];
    if (algorithmType.startsWith("(")) algorithmType = algorithmType.substring(1)
    if (algorithmType.endsWith(")")) algorithmType = algorithmType.substring(0, algorithmType.length - 1);

    let hash = tokens[1];
    if (hash.startsWith("SHA256:")) {
        hash = "SHA256: " + hash.substring(7);
    }

    let comment = "";
    for (let i = 2; i < tokens.length - 1; i++) {
        if (i != 2) comment += " ";
        comment += tokens[i];
    }

    return {keyLength, algorithmType, comment, hash};
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

        ImportantStats({resource}) {
            if (!resource) return null;
            const fingerprint = parseFingerprint(resource.fingerprint);
            if (!fingerprint) return null;

            return <code>{fingerprint.hash}</code>;
        },

        Stats({resource}) {
            if (resource == null) return null;
            const fingerprint = parseFingerprint(resource.fingerprint);
            return (
                <>
                    <ListRowStat icon="calendar">{dateToString(resource.createdAt)}</ListRowStat>
                    {!fingerprint ? null : <>
                        <ListRowStat icon={"key"}>{fingerprint.algorithmType} {fingerprint.keyLength}</ListRowStat>
                        {fingerprint.comment ? <ListRowStat icon={"edit"}>{fingerprint.comment}</ListRowStat> : null}
                    </>}
                </>
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

    public retrieveOperations(): Operation<SSHKey, StandardCallbacks<SSHKey>>[] {
        return [
            {
                icon: "upload",
                text: "Create SSH key",
                primary: true,
                enabled: (selected) => selected.length === 0,
                onClick: (selected, cb) => {
                    cb.navigate("/ssh-keys/create");
                },
                shortcut: ShortcutKey.N,
            },
            {
                icon: "trash",
                text: "Delete",
                color: "errorMain",
                enabled: (selected) => selected.length > 0,
                confirm: true,
                onClick: async (selected, cb) => {
                    await cb.invokeCommand(
                        this.delete(bulkRequestOf(
                            ...selected.map(it => ({id: it.id}))
                        ))
                    );

                    cb.reload();
                },
                shortcut: ShortcutKey.R
            }
        ];
    }
}

export default new SshKeyApi();
