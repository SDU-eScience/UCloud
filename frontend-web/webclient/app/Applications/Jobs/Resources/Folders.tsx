import * as React from "react";
import {Box, Card} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import Warning from "@/ui-components/Warning";
import {anyFolderDuplicates} from "../Widgets/GenericFiles";
import {Application, ApplicationParameter} from "@/Applications/AppStoreApi";
import {Dispatch, SetStateAction} from "react";
import {CompactResourceRows} from "@/Applications/Jobs/Resources/CompactResourceRows";

export function folderResourceAllowed(app: Application): boolean {
    if (app.invocation.allowAdditionalMounts != null) return app.invocation.allowAdditionalMounts;

    // noinspection RedundantIfStatementJS
    if (app.invocation.applicationType !== "BATCH" && app.invocation.tool.tool!.description.backend === "DOCKER") {
        return true;
    }

    return false;
}

export const FolderResource: React.FunctionComponent<{
    application: Application;
    params: ApplicationParameter[];
    errors: Record<string, string>;
    setErrors: Dispatch<SetStateAction<Record<string, string>>>;
    warning: string;
    setWarning: (warning: string) => void;
    onAdd: () => void;
    onRemove: (id: string) => void;
    sectionClassName?: string;
    cardId?: string;
    heading?: React.ReactNode;
}> = ({application, params, errors, onAdd, onRemove, warning, setWarning, setErrors, sectionClassName, cardId, heading}) => {
    return !folderResourceAllowed(application) ? null : (
        <Card className={sectionClassName} id={cardId}>
            {heading ?? <Heading.h4>Storage</Heading.h4>}
            <Warning warning={warning} clearWarning={() => setWarning("")} />
            <CompactResourceRows
                singularLabel="Folder"
                firstRowDescription={"Add directories to your job. Available in /work."}
                application={application}
                params={params}
                errors={errors}
                setErrors={setErrors}
                setWarning={setWarning}
                onAdd={onAdd}
                onRemove={id => {
                    onRemove(id);
                    if (!anyFolderDuplicates()) setWarning("");
                }}
            />
        </Card>
    );
};
