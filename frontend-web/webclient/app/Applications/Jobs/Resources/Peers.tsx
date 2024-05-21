import * as React from "react";
import * as UCloud from "@/UCloud"
import {Box, Button, Card, Flex} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import BaseLink from "@/ui-components/BaseLink";
import {Widget} from "@/Applications/Jobs/Widgets";
import {Application, ApplicationParameter} from "@/Applications/AppStoreApi";

export function peerResourceAllowed(app: Application) {
    const invocation = app.invocation;
    const tool = invocation.tool.tool!.description;
    return (invocation.allowAdditionalPeers !== false && tool.backend === "DOCKER") ||
        invocation.allowAdditionalPeers === true;
}

export const PeerResource: React.FunctionComponent<{
    application: Application;
    params: ApplicationParameter[];
    errors: Record<string, string>;
    setErrors: (errors: Record<string, string>) => void;
    onAdd: () => void;
    onRemove: (id: string) => void;
}> = ({application, params, errors, onAdd, onRemove, setErrors}) => {
    return !peerResourceAllowed(application) ? null : (
        <Card>
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
                        </>
                    )}
                </Box>

                {
                    params.map(entry => (
                        <Box key={entry.name} mb={"7px"}>
                            <Widget
                                parameter={entry}
                                errors={errors}
                                setErrors={setErrors}
                                onRemove={() => {
                                    onRemove(entry.name);
                                }}
                            />
                        </Box>
                    ))
                }
            </Box>
        </Card>);
};
