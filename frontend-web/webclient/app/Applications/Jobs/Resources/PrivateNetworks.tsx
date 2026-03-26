import * as React from "react";
import {Box, Button, Card, Flex, Input, Label} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import Link from "@/ui-components/Link";
import BaseLink from "@/ui-components/BaseLink";
import {Widget} from "@/Applications/Jobs/Widgets";
import {Application, ApplicationParameter} from "@/Applications/AppStoreApi";
import AppRoutes from "@/Routes";
import {doNothing} from "@/UtilityFunctions";
import {peerResourceAllowed} from "@/Applications/Jobs/Resources/Peers";
import {Feature, hasFeature} from "@/Features";

export const PrivateNetworkResource: React.FunctionComponent<{
    application: Application;
    params: ApplicationParameter[];
    errors: Record<string, string>;
    setErrors: (errors: Record<string, string>) => void;
    onAdd: () => void;
    onRemove: (id: string) => void;
    provider?: string;
    dnsHostname: string;
    onDnsHostnameChange: (ev: React.SyntheticEvent) => void;
}> = ({
    application,
    params,
    errors,
    onAdd,
    onRemove,
    setErrors,
    provider,
    dnsHostname,
    onDnsHostnameChange,
}) => {
    if (!peerResourceAllowed(application) || !hasFeature(Feature.NEW_VM_UI)) return null;

    return (
        <Card>
            <Box>
                <Flex alignItems="center">
                    <Box flexGrow={1}>
                        <Heading.h4>Connect to other jobs</Heading.h4>
                    </Box>
                    <Button type={"button"} ml={"5px"} lineHeight={"16px"} onClick={onAdd}>Connect network</Button>
                </Flex>

                <Box mb={8} mt={8}>
                    {params.length !== 0 ? (
                        <Box mb={16}>
                            <Label>
                                Hostname
                                <Input
                                    mt={"8px"}
                                    style={{minWidth: "220px"}}
                                    value={dnsHostname}
                                    onChange={onDnsHostnameChange}
                                />
                            </Label>
                            <div style={{color: "var(--textSecondary)", fontStyle: "italic", marginTop: "8px"}}>
                                Your job will be identified by this name within the network.
                            </div>
                        </Box>
                    ) : (
                        <>
                            If you need to connect this job to a network of other jobs then click {" "}
                            <BaseLink
                                href="#"
                                onClick={e => {
                                    e.preventDefault();
                                    onAdd();
                                }}
                            >
                                &quot;Connect network&quot;
                            </BaseLink>
                            {" "}
                            to select one. You can manage networks in {" "}
                            <Link to={AppRoutes.resources.privateNetworks()} target="_blank">
                                private networks
                            </Link>
                            .
                        </>
                    )}
                </Box>

                {params.map(entry => (
                    <Box key={entry.name} mb={"7px"}>
                        <Widget
                            provider={provider}
                            parameter={entry}
                            errors={errors}
                            setErrors={setErrors}
                            application={application}
                            injectWorkflowParameters={doNothing}
                            onRemove={() => {
                                onRemove(entry.name);
                            }}
                        />
                    </Box>
                ))}
            </Box>
        </Card>
    );
};
