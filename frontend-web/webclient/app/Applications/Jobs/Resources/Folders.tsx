import * as React from "react";
import * as UCloud from "@/UCloud"
import {Box, Button, Flex} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import Link from "@/ui-components/Link";
import BaseLink from "@/ui-components/BaseLink";
import {Widget} from "@/Applications/Jobs/Widgets";
import {compute} from "@/UCloud";
import ApplicationParameter = compute.ApplicationParameter;
import {GrayBox} from "../Create";

export const FolderResource: React.FunctionComponent<{
    application: UCloud.compute.Application;
    params: ApplicationParameter[];
    errors: Record<string, string>;
    onAdd: () => void;
    onRemove: (id: string) => void;
}> = ({application, params, errors, onAdd, onRemove}) => {
    return (application.invocation.allowAdditionalMounts === false || (application.invocation.allowAdditionalMounts == null && application.invocation.applicationType === "BATCH")) ? null : (
        <GrayBox>
            <Box>
                <Flex alignItems="center">
                    <Box flexGrow={1}>
                        <Heading.h4>Select additional folders to use</Heading.h4>
                    </Box>

                    <Button type={"button"} ml={"5px"} lineHeight={"16px"} onClick={onAdd}>Add folder</Button>
                </Flex>

                <Box mb={8} mt={8}>
                    {params.length !== 0 ? (
                        <>
                            Your files will be available at <code>/work/</code>.
                        </>
                    ) : (
                        <>
                            If you need to use your {" "}
                            <Link
                                to={"/files/"}
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
                            onRemove={() => {
                                onRemove(entry.name);
                            }}
                        />
                    </Box>
                ))}
            </Box>
        </GrayBox>
    );
};
