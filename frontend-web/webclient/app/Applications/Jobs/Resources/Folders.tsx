import * as React from "react";
import * as UCloud from "@/UCloud"
import {Box, Button, Card, Flex} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import Link from "@/ui-components/Link";
import BaseLink from "@/ui-components/BaseLink";
import {Widget} from "@/Applications/Jobs/Widgets";
import {compute} from "@/UCloud";
import ApplicationParameter = compute.ApplicationParameter;
import Warning from "@/ui-components/Warning";
import {anyFolderDuplicates} from "../Widgets/GenericFiles";

export function folderResourceAllowed(app: UCloud.compute.Application): boolean {
    if (app.invocation.allowAdditionalMounts != null) return app.invocation.allowAdditionalMounts;

    // noinspection RedundantIfStatementJS
    if (app.invocation.applicationType !== "BATCH" && app.invocation.tool.tool!.description.backend === "DOCKER") {
        return true;
    }

    return false;
}

export const FolderResource: React.FunctionComponent<{
    application: UCloud.compute.Application;
    params: ApplicationParameter[];
    errors: Record<string, string>;
    setErrors: (errors: Record<string, string>) => void;
    warning: string;
    setWarning: (warning: string) => void;
    onAdd: () => void;
    onRemove: (id: string) => void;
}> = ({application, params, errors, onAdd, onRemove, warning, setWarning, setErrors}) => {
    return !folderResourceAllowed(application) ? null : (
        <Card>
            <Box>
                <Flex alignItems="center">
                    <Box flexGrow={1}>
                        <Heading.h4>Select folders to use</Heading.h4>
                    </Box>
                    <Button type={"button"} ml={"5px"} lineHeight={"16px"} onClick={onAdd}>Add folder</Button>
                </Flex>

                <Box my="6px">
                    <Warning warning={warning} clearWarning={() => setWarning("")} />
                </Box>

                <Box mb={8} mt={8}>
                    {params.length !== 0 ? (
                        <>
                            Your files will be available at <code>/work/</code>.
                        </>
                    ) : (
                        <>
                            If you need to use your {" "}
                            <Link
                                to={"/drives/"}
                                target="_blank"
                            >
                                files
                            </Link>
                            {" "}
                            in this job then click {" "}
                            <BaseLink
                                href="#"
                                onClick={e => {
                                    e.preventDefault();
                                    onAdd();
                                }}
                            >
                                &quot;Add folder&quot;
                            </BaseLink>
                            {" "}
                            to select the relevant
                            files.
                        </>
                    )}
                </Box>

                {params.map(entry => (
                    <Box key={entry.name} mb={"7px"}>
                        <Widget
                            parameter={entry}
                            errors={errors}
                            setWarning={setWarning}
                            setErrors={setErrors}
                            onRemove={() => {
                                onRemove(entry.name);
                                if (!anyFolderDuplicates()) {
                                    setWarning("");
                                }
                            }}
                        />
                    </Box>
                ))}
            </Box>
        </Card>
    );
};
