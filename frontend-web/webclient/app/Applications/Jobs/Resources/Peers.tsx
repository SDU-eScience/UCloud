import * as React from "react";
import * as UCloud from "UCloud"
import {Box, Button, Flex} from "ui-components";
import * as Heading from "ui-components/Heading";
import BaseLink from "ui-components/BaseLink";
import {Widget} from "Applications/Jobs/Widgets";

export const PeerResource: React.FunctionComponent<{
    application: UCloud.compute.Application;
    ids: string[];
    errors: Record<string, string>;
    onAdd: () => void;
    onRemove: (id: string) => void;
}> = ({application, ids, errors, onAdd, onRemove}) => {
    return !application.invocation.shouldAllowAdditionalPeers ? null : (
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
                {ids.length !== 0 ? (
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
                ids.map(entry => (
                    <Box key={entry} mb={"7px"}>
                        <Widget
                            parameter={{
                                type: "peer",
                                name: entry,
                                optional: true,
                                title: "",
                                description: ""
                            }}
                            generation={0}
                            errors={errors}
                            onRemove={() => {
                                onRemove(entry);
                            }}
                        />
                    </Box>
                ))
            }
        </Box>
    );
};
