import * as React from "react";
import * as UCloud from "UCloud"
import {Box, Button, Flex} from "ui-components";
import * as Heading from "ui-components/Heading";
import Link from "ui-components/Link";
import {fileTablePage} from "Utilities/FileUtilities";
import {Client} from "Authentication/HttpClientInstance";
import BaseLink from "ui-components/BaseLink";
import {Widget} from "Applications/Jobs/Widgets";

export const FolderResource: React.FunctionComponent<{
    application: UCloud.compute.Application;
    ids: string[];
    errors: Record<string, string>;
    onAdd: () => void;
    onRemove: (id: string) => void;
}> = ({application, ids, errors, onAdd, onRemove}) => {
    return !application.invocation.shouldAllowAdditionalMounts ? null : (
        <Box>
            <Flex alignItems="center">
                <Box flexGrow={1}>
                    <Heading.h4>Select additional folders to use</Heading.h4>
                </Box>

                <Button type={"button"} ml={"5px"} lineHeight={"16px"} onClick={onAdd}>Add folder</Button>
            </Flex>

            <Box mb={8} mt={8}>
                {ids.length !== 0 ? (
                    <>
                        Your files will be available at <code>/work/</code>.
                    </>
                ) : (
                    <>
                        If you need to use your {" "}
                        <Link
                            to={fileTablePage(Client.homeFolder)}
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

            {ids.map(entry => (
                <Box key={entry} mb={"7px"}>
                    <Widget
                        parameter={{
                            type: "input_directory",
                            name: entry,
                            optional: true,
                            title: "",
                            description: ""
                        }}
                        errors={errors}
                        onRemove={() => {
                            onRemove(entry);
                        }}
                    />
                </Box>
            ))}
        </Box>
    );
};
