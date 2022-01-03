import * as React from "react";
import * as UCloud from "@/UCloud"
import {Box, Button, Flex} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import BaseLink from "@/ui-components/BaseLink";
import {Widget} from "@/Applications/Jobs/Widgets";
import {compute} from "@/UCloud";
import ApplicationParameter = compute.ApplicationParameter;

export const PeerResource: React.FunctionComponent<{
    application: UCloud.compute.Application;
    params: ApplicationParameter[];
    errors: Record<string, string>;
    onAdd: () => void;
    onRemove: (id: string) => void;
}> = ({application, params, errors, onAdd, onRemove}) => {
    const invocation = application.invocation;
    const tool = invocation.tool.tool!.description;
    const shouldShow = invocation.allowAdditionalPeers !== false && tool.backend !== "VIRTUAL_MACHINE";
    return !shouldShow ? null : (
        <Box>
            <Flex alignItems={"center"}>
                <Box flexGrow={1}>
                    <Heading.h4>Connect to other jobs</Heading.h4>
                </Box>
                <Button
                    type="button"
                    lineHeight="16px"
                    onClick={onAdd}
                >
                    Connect to job
                </Button>
            </Flex>
            <Box mb={8} mt={8}>
                {params.length !== 0 ? (
                    <>
                        You will be able contact the <b>job</b> using its <b>hostname</b>.
                    </>
                ) : (
                    <>
                        If you need to use the services of another job click{" "}
                        <BaseLink
                            href="#"
                            onClick={e => {
                                e.preventDefault();
                                onAdd();
                            }}
                        >
                            &quot;Connect to job&quot;.
                        </BaseLink>
                        {" "}
                        This includes networking.
                    </>
                )}
            </Box>

            {
                params.map(entry => (
                    <Box key={entry.name} mb={"7px"}>
                        <Widget
                            parameter={entry}
                            errors={errors}
                            onRemove={() => {
                                onRemove(entry.name);
                            }}
                        />
                    </Box>
                ))
            }
        </Box>
    );
};
