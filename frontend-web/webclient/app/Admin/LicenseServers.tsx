import * as UCloud from "@/UCloud";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import * as React from "react";
import styled from "styled-components";
import {Box, Button, Flex, Icon, Input, Text, Card, Grid} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useSidebarPage, SidebarPages} from "@/ui-components/Sidebar";
import {MutableRefObject, useCallback, useEffect, useRef, useState} from "react";
import {accounting, compute, PageV2} from "@/UCloud";
import KubernetesLicense = compute.ucloud.KubernetesLicense;
import licenseApi = compute.ucloud.licenses.maintenance;
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useProjectStatus} from "@/Project/cache";
import Wallet = accounting.Wallet;
import {useProjectId} from "@/Project";
import {TextSpan} from "@/ui-components/Text";
import MainContainer from "@/MainContainer/MainContainer";

const LeftAlignedTableHeader = styled(TableHeader)`
  text-align: left;
`;

const GrantCopies: React.FunctionComponent<{licenseServer: KubernetesLicense, onGrant: () => void}> = props => {
    const [loading, invokeCommand] = useCloudCommand();
    const project = useProjectStatus();
    const projectId = useProjectId();
    const projectName = project.fetch().membership.find(it => it.projectId === projectId)?.title

    const grantCopies = useCallback(async () => {
        if (loading || !projectId) return;
        const wallet: Wallet = {
            id: projectId,
            type: "PROJECT",
            paysFor: {
                provider: "ucloud",
                name: props.licenseServer.id
            }
        };

        // NOTE(Dan): We must initialize the wallet first, this is quite likely to fail if we are adding additional
        // copies.
        try {
            await invokeCommand(
                UCloud.accounting.wallets.setBalance({wallet, newBalance: 0, lastKnownBalance: 0}),
                {defaultErrorHandler: false}
            );
        } catch (ignored) {
            // Ignored
        }

        await invokeCommand(UCloud.accounting.wallets.addToBalance({
            credits: 1_000_000 * 1000,
            wallet
        }));
        props.onGrant();
    }, [props.onGrant, loading]);

    return <Grid gridTemplateColumns={"1fr"} gridGap={16}>
        <Heading.h3>Grant copies?</Heading.h3>
        <Box>
            This will add 1000 copies to your currently active project ({projectName}). Users will be able to apply
            from this project to receive access to the license.
        </Box>
        <Button onClick={grantCopies}>Grant copies</Button>
    </Grid>;
};

const LicenseServerTagsPrompt: React.FunctionComponent<{
    licenseServer: KubernetesLicense;
    onUpdate?: () => void;
}> = ({licenseServer, onUpdate}) => {
    const [tagList, setTagList] = useState<string[]>(licenseServer.tags);
    useEffect(() => {
        setTagList(licenseServer.tags);
    }, [licenseServer]);

    const [, invokeCommand] = useCloudCommand();
    const newTagField = useRef<HTMLInputElement>(null);

    return (
        <Box>
            <div>
                <Flex alignItems={"center"}>
                    <Heading.h3>
                        <TextSpan color="gray">Tags for</TextSpan> {licenseServer?.id}
                    </Heading.h3>
                </Flex>
                <Box mt={16} mb={30}>
                    <form
                        onSubmit={async e => {
                            e.preventDefault();

                            const tagValue = newTagField.current?.value;
                            if (tagValue === undefined || tagValue === "") return;
                            const newTagList = [...tagList, tagValue]
                            setTagList(newTagList);
                            newTagField.current!.value = "";
                            await invokeCommand(licenseApi.update(bulkRequestOf({...licenseServer, tags: newTagList})));
                            if (onUpdate) onUpdate();
                        }}
                    >
                        <Flex height={45}>
                            <Input
                                rightLabel
                                type="text"
                                ref={newTagField}
                                placeholder="Name of tag"
                            />
                            <Button
                                attached
                                width="200px"
                                type={"submit"}
                            >
                                Add tag
                            </Button>
                        </Flex>
                    </form>
                </Box>
                {tagList.length > 0 ? (
                    <Box maxHeight="80vh">
                        <Table width="500px">
                            <LeftAlignedTableHeader>
                                <TableRow>
                                    <TableHeaderCell>Tag</TableHeaderCell>
                                    <TableHeaderCell width={50}>Delete</TableHeaderCell>
                                </TableRow>
                            </LeftAlignedTableHeader>
                            <tbody>
                                {tagList.map(tagEntry => (
                                    <TableRow key={tagEntry}>
                                        <TableCell>{tagEntry}</TableCell>
                                        <TableCell textAlign="right">
                                            <Button
                                                color={"red"}
                                                type={"button"}
                                                paddingLeft={10}
                                                paddingRight={10}
                                                onClick={async () => {
                                                    const newTagList = tagList.filter(it => it !== tagEntry);
                                                    setTagList(newTagList);
                                                    await invokeCommand(
                                                        licenseApi.update(
                                                            bulkRequestOf({...licenseServer, tags: newTagList})
                                                        )
                                                    );
                                                    if (onUpdate) onUpdate();
                                                }}
                                            >
                                                <Icon size={16} name="trash" />
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </tbody>
                        </Table>
                    </Box>
                ) : (
                    <Text textAlign="center">No tags found</Text>
                )}
            </div>
        </Box>
    );
}

interface InputHook<T = HTMLInputElement> {
    ref: MutableRefObject<T | null>;
    hasError: boolean;
    setHasError: (err: boolean) => void;
}

export function useInput<T = HTMLInputElement>(): InputHook<T> {
    const ref = useRef<T>(null);
    const [hasError, setHasError] = useState(false);
    return {ref, hasError, setHasError};
}

const LicenseServers: React.FunctionComponent = () => {
    const [licenses, fetchLicenses] = useCloudAPI<PageV2<KubernetesLicense>>({noop: true}, emptyPageV2);
    const [loading, invokeCommand] = useCloudCommand();
    const [infScroll, setInfScroll] = useState(0);
    const [editing, setEditing] = useState<KubernetesLicense | null>(null);
    const [granting, setGranting] = useState<KubernetesLicense | null>(null);

    const [isAvailable, setAvailable] = React.useState(true);
    // const [paymentModel, setPaymentModel] = React.useState<PaymentModel>("PER_ACTIVATION");

    const projectId = useProjectId();

    const reload = useCallback(() => {
        fetchLicenses(licenseApi.browse({}));
        setInfScroll(s => s + 1);
    }, []);
    useEffect(reload, [reload]);

    const loadMore = useCallback(() => {
        fetchLicenses(licenseApi.browse({next: licenses.data.next}));
    }, [licenses.data]);

    const nameInput = useInput();
    const portInput = useInput();
    const addressInput = useInput();
    const licenseInput = useInput();
    const pricePerUnitInput = useInput();
    const priorityInput = useInput();
    const reasonInput = useInput();
    const [hiddenInGrantApplicationsInput, setHiddenInGrantApplicationsInput] = useState(false);
    const descriptionTextArea = useInput<HTMLTextAreaElement>();

    useTitle("UCloud/Compute: License servers");
    useSidebarPage(SidebarPages.Admin);
    useRefreshFunction(reload);

    /*
    async function submit(e: React.SyntheticEvent): Promise<void> {
        e.preventDefault();

        const name = nameInput.ref.current!.value;
        const port = parseInt(portInput.ref.current!.value, 10);
        const address = addressInput.ref.current!.value;
        const license = licenseInput.ref.current!.value;
        const priority = parseInt(priorityInput.ref.current!.value, 10);
        const pricePerUnit = parseInt(pricePerUnitInput.ref.current!.value, 10) * 1_000_000;
        const reason = reasonInput.ref.current?.value ?? "";
        const hiddenInGrantApplications = hiddenInGrantApplicationsInput;
        const description = descriptionTextArea.ref.current?.value ?? "";

        let error = false;

        if (name === "") {
            nameInput.setHasError(true);
            error = true;
        }
        if (address === "") {
            addressInput.setHasError(true);
            error = true;
        }
        if (!description) {
            descriptionTextArea.setHasError(true);
            error = true;
        }

        if (isNaN(port)) {
            portInput.setHasError(true);
            error = true;
        }

        if (isNaN(priority)) {
            priorityInput.setHasError(true);
            error = true;
        }

        if (isNaN(pricePerUnit)) {
            pricePerUnitInput.setHasError(true);
            error = true;
        }


        if (!error) {
            if (loading) return;
            const request: KubernetesLicense = {
                id: name,
                port,
                address,
                license: license !== "" ? license : undefined,
                tags: [],

                availability: isAvailable ? {type: "available"} : {type: "unavailable", reason},
                category: {
                    name: name,
                    provider: "ucloud"
                },
                description,
                hiddenInGrantApplications,
                paymentModel,
                pricePerUnit,
                priority
            };
            try {
                await invokeCommand(licenseApi.create(bulkRequestOf(request)), {defaultErrorHandler: false});
                snackbarStore.addSuccess(`License server '${name}' successfully added`, true);
                reload();
            } catch (e) {
                snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to add License Server"), false);
            }

        }
    }
     */

    const [openLicenses, setOpenLicenses] = useState<Set<string>>(new Set());

    if (!Client.userIsAdmin) return null;
    return <MainContainer main={<>
        <p>Temporarily out-of-service. In the mean time, someone from the backend team can help with maintenance.</p>
    </>}/>;


    // return (
    //     <MainContainer
    //         header={<Heading.h1>License Servers</Heading.h1>}
    //         headerSize={64}
    //         main={(
    //             <>
    //                 <Box maxWidth={800} mt={30} marginLeft="auto" marginRight="auto">
    //                     <form onSubmit={e => submit(e)}>
    //                         <Label mb="1em">
    //                             Name
    //                             <Input
    //                                 ref={nameInput.ref}
    //                                 error={nameInput.hasError}
    //                                 placeholder={"Identifiable name for the license server"}
    //                             />
    //                         </Label>
    //                         <Box marginBottom={30}>
    //                             <Flex height={45}>
    //                                 <Label mb="1em">
    //                                     Address
    //                                     <Input
    //                                         ref={addressInput.ref}
    //                                         error={addressInput.hasError}
    //                                         rightLabel
    //                                         placeholder={"IP address or URL"}
    //                                     />
    //                                 </Label>
    //                                 <Label mb="1em" width="30%">
    //                                     Port
    //                                     <Input
    //                                         ref={portInput.ref}
    //                                         error={portInput.hasError}
    //                                         type={"number"}
    //                                         min={0}
    //                                         max={65535}
    //                                         leftLabel
    //                                         maxLength={5}
    //                                         placeholder={"Port"}
    //                                     />
    //                                 </Label>
    //                             </Flex>
    //                         </Box>
    //                         <Label mb="1em">
    //                             Key
    //                             <Input
    //                                 ref={licenseInput.ref}
    //                                 error={licenseInput.hasError}
    //                                 placeholder="License or key (if needed)"
    //                             />
    //                         </Label>
    //
    //                         <Label>
    //                             Priority
    //                             <Input mb="1em" type="number" autoComplete="off" ref={priorityInput.ref} defaultValue={0} />
    //                         </Label>
    //
    //                         <Label mb="1em">
    //                             Availability
    //                             <Select
    //                                 onChange={e => setAvailable(e.target.value === "available")}
    //                                 defaultValue={isAvailable ? "Available" : "Unavailable"}
    //                             >
    //                                 <option value={"available"}>Available</option>
    //                                 <option value={"unavailable"}>Unavailable</option>
    //                             </Select>
    //                         </Label>
    //
    //                         {isAvailable ? null :
    //                             <Label mb="1em">
    //                                 Unvailability reason
    //                                 <Input ref={reasonInput.ref} />
    //                             </Label>
    //                         }
    //
    //                         <Label>
    //                             Payment Model
    //                             <Flex mb="1em">
    //                                 <Select onChange={e => setPaymentModel(e.target.value as PaymentModel)} defaultValue={paymentModel[0]}>
    //                                     {PaymentModelOptions.map(it =>
    //                                         <option key={it} value={it}>{prettierString(it)}</option>
    //                                     )}
    //                                 </Select>
    //                             </Flex>
    //                         </Label>
    //
    //                         <Label>
    //                             Price per unit
    //                             <Flex mb="1em">
    //                                 <Input autoComplete="off" min={0} defaultValue={1} type="number" ref={pricePerUnitInput.ref} rightLabel />
    //                                 <InputLabel width="60px" rightLabel>DKK</InputLabel>
    //                             </Flex>
    //                         </Label>
    //
    //                         <TextArea error={descriptionTextArea.hasError} width={1} mb="1em" rows={4} ref={descriptionTextArea.ref} placeholder="License description..." />
    //
    //                         <Label width="auto" mb="1em">
    //                             <Checkbox
    //                                 checked={hiddenInGrantApplicationsInput}
    //                                 onClick={() => setHiddenInGrantApplicationsInput(!hiddenInGrantApplicationsInput)}
    //                                 onChange={stopPropagation}
    //                             />
    //                             Hide License Server from grant applicants
    //                         </Label>
    //
    //                         <Button type="submit" color="green" disabled={loading}>Add License Server</Button>
    //                     </form>
    //
    //                     {projectId == null ?
    //                         <Text bold mt={8}>
    //                             You must have an active project in order to grant copies of a license!
    //                         </Text> : null
    //                     }
    //
    //                     <ReactModal
    //                         isOpen={editing != null}
    //                         onRequestClose={() => setEditing(null)}
    //                         shouldCloseOnEsc
    //                         ariaHideApp={false}
    //                         style={defaultModalStyle}
    //                     >
    //                         {!editing ? null : <LicenseServerTagsPrompt licenseServer={editing} onUpdate={reload} />}
    //                     </ReactModal>
    //
    //                     <ReactModal
    //                         isOpen={granting != null}
    //                         onRequestClose={() => setGranting(null)}
    //                         shouldCloseOnEsc
    //                         ariaHideApp={false}
    //                         style={defaultModalStyle}
    //                     >
    //                         {!granting ? null :
    //                             <GrantCopies licenseServer={granting} onGrant={() => setGranting(null)} />}
    //                     </ReactModal>
    //
    //                     <Box mt={30}>
    //                         <Pagination.ListV2
    //                             loading={licenses.loading}
    //                             page={licenses.data}
    //                             infiniteScrollGeneration={infScroll}
    //                             onLoadMore={loadMore}
    //                             pageRenderer={items => (
    //                                 items.map(licenseServer =>
    //                                     <LicenseServerCard
    //                                         key={licenseServer.id}
    //                                         reload={reload}
    //                                         setGranting={setGranting}
    //                                         setEditing={setEditing}
    //                                         licenseServer={licenseServer}
    //                                         openLicenses={openLicenses}
    //                                         setOpenLicenses={license => {
    //                                             const isSelected = openLicenses.has(license.id);
    //                                             if (isSelected) {
    //                                                 openLicenses.delete(license.id);
    //                                             } else {
    //                                                 openLicenses.add(license.id);
    //                                             }
    //                                             setOpenLicenses(new Set(openLicenses))
    //                                         }}
    //                                     />
    //                                 )
    //                             )}
    //                         />
    //                     </Box>
    //                 </Box>
    //             </>
    //         )}
    //     />
    // );
};

interface LicenseServerCardProps {
    openLicenses: Set<string>;
    licenseServer: KubernetesLicense;
    reload(): void;
    setOpenLicenses(license: KubernetesLicense): void;
    setEditing(license: KubernetesLicense): void;
    setGranting(license: KubernetesLicense): void;
}

function LicenseServerCard({openLicenses, licenseServer, reload, setOpenLicenses, setEditing, setGranting}: LicenseServerCardProps) {
    // const isSelected = openLicenses.has(licenseServer.id);
    // const [isEditing, setIsEditing] = useState(false);
    // const projectId = useProjectId();
    //
    // /* Editing */
    //
    // const addressInput = React.useRef<HTMLInputElement>(null);
    // const priorityInput = React.useRef<HTMLInputElement>(null);
    // const [isAvailable, setIsAvailable] = useState<boolean>(licenseServer.availability.type === "available");
    // const unavailableReasonInput = React.useRef<HTMLInputElement>(null);
    // const descriptionInput = React.useRef<HTMLTextAreaElement>(null);
    // const [hiddenInGrantApplications, setHiddenInGrantApplications] = useState<boolean>(licenseServer.hiddenInGrantApplications)
    // const [paymentModelEdit, setPaymentModelEdit] = React.useState<PaymentModel>(licenseServer.paymentModel);
    // const portInput = React.useRef<HTMLInputElement>(null);
    // const pricePerUnitInput = React.useRef<HTMLInputElement>(null);
    // const licenseInput = React.useRef<HTMLInputElement>(null);
    //
    // const [loading, invokeCommand] = useCloudCommand();
    return null;
}

/* NOTE(Jonas): Lots of overlap in both branches, but the whole 'isEditing' for each field is cumbersome to  */
// if (isEditing) {
//     return (
//         <ExpandingCard height={isAvailable ? "650px" : "720px"} key={licenseServer.id} mb={2} padding={20} borderRadius={5}>
//             <Heading.h4 mb="1em">{licenseServer.id}</Heading.h4>
//             <Flex mb="1em">
//                 <Input width="75%" ref={addressInput} defaultValue={licenseServer.address} />
//                 <Text mt="6px" mx="4px">:</Text>
//                 <Input
//                     width="25%"
//                     ref={portInput}
//                     type="number"
//                     min={0}
//                     max={65535}
//                     defaultValue={licenseServer.port}
//                 />
//             </Flex>
//
//             <>
//                 <Label mb="1em">
//                     Availability
//                     <Select onChange={e => setIsAvailable(e.target.value === "available")} defaultValue={prettierString(licenseServer.availability.type)}>
//                         <option value="available">Available</option>
//                         <option value="unavailable">Unavailable</option>
//                     </Select>
//                 </Label>
//
//                 {isAvailable ? null :
//                     <Label mb="1em">
//                         Unvailability reason
//                         <Input ref={unavailableReasonInput} defaultValue={licenseServer.availability.type === "unavailable" ? licenseServer.availability.reason : ""} />
//                     </Label>
//                 }
//             </>
//
//             <Label>
//                 License
//                 <Input mb="1em" ref={licenseInput} autoComplete="off" defaultValue={licenseServer.license} />
//             </Label>
//
//             <Heading.h4>Description</Heading.h4>
//             <TextArea mb="1em" width={1} rows={4} ref={descriptionInput} defaultValue={licenseServer.description} />
//
//             <Label width="auto" mb="1em">
//                 <Checkbox
//                     checked={hiddenInGrantApplications}
//                     onClick={() => setHiddenInGrantApplications(!hiddenInGrantApplications)}
//                     onChange={stopPropagation}
//                 />
//                 Hide License Server from grant applicants
//             </Label>
//
//             <Flex mb="1em">
//                 <Box width="42.5%">
//                     <Heading.h4>Price per unit</Heading.h4>
//                     <Flex mr="6px" mb="1em">
//                         <Input type="number" rightLabel ref={pricePerUnitInput} defaultValue={licenseServer.pricePerUnit / 1_000_000} />
//                         <InputLabel rightLabel>DKK</InputLabel>
//                     </Flex>
//                 </Box>
//
//                 <Box width="42.5%">
//                     <Heading.h4>Payment model</Heading.h4>
//                     <Select ml="6px" onChange={e => setPaymentModelEdit(e.target.value as PaymentModel)}>
//                         {PaymentModelOptions.map(it =>
//                             <option key={it} selected={it === licenseServer.paymentModel} value={it}>{prettierString(it)}</option>
//                         )}
//                     </Select>
//                 </Box>
//                 <Box width="15%">
//                     <Heading.h4>Priority</Heading.h4>
//                     <Input ml="6px" type="number" autoComplete="off" ref={priorityInput} rightLabel defaultValue={licenseServer.priority} />
//                 </Box>
//             </Flex>
//             <Flex mb="1em">
//                 <Button ml="6px" disabled={loading} color="red" width="50%" onClick={() => setIsEditing(false)}>Cancel</Button>
//                 <Button mr="6px" onClick={UpdateLicenseServer} disabled={loading} width="50%">Update</Button>
//             </Flex>
//         </ExpandingCard >
//     );
// } else {
//     return <ExpandingCard height={isSelected ? (licenseServer.availability.type === "available" ? "466px" : "506px") : "96px"} key={licenseServer.id} mb={2} padding={20} borderRadius={5}>
//         <Flex justifyContent="space-between">
//             <Box>
//                 <Flex>
//                     <RotatingIcon
//                         onClick={() => setOpenLicenses(licenseServer)}
//                         size={14} name="close" rotation={isSelected ? 0 : 45} />
//                     <Heading.h4>{licenseServer.id}</Heading.h4>
//                 </Flex>
//                 <Box>{licenseServer.address}:{licenseServer.port}</Box>
//             </Box>
//             <Flex>
//                 <Box>
//                     {licenseServer.license !== null ? (
//                         <Tooltip
//                             tooltipContentWidth="300px"
//                             wrapperOffsetLeft="0"
//                             wrapperOffsetTop="4px"
//                             right="0"
//                             top="1"
//                             mb="50px"
//                             trigger={(
//                                 <Icon
//                                     size="20px"
//                                     mt="8px"
//                                     mr="8px"
//                                     color="gray"
//                                     name="key"
//                                     ml="5px"
//                                 />
//                             )}
//                         >
//                             {licenseServer.license}
//                         </Tooltip>
//                     ) : <Text />}
//                 </Box>
//                 <Box>
//                     <Icon
//                         cursor="pointer"
//                         size="20px"
//                         mt="6px"
//                         mr="8px"
//                         color="gray"
//                         color2="midGray"
//                         name="tags"
//                         onClick={() => setEditing(licenseServer)}
//                     />
//                 </Box>
//                 {!projectId ? null : (
//                     <Box>
//                         <Button onClick={() => setGranting(licenseServer)}>
//                             Grant copies
//                     </Button>
//                     </Box>
//                 )}
//             </Flex>
//         </Flex>
//         {/* HIDDEN ON NOT OPEN */}
//         <Box height="25px" />
//
//         <Heading.h4>License</Heading.h4>
//         <Text mb="1em">{licenseServer.license ?? "None provided"}</Text>
//
//         <Heading.h4>License state</Heading.h4>
//         {licenseServer.availability.type === "available" ?
//             <Text>Available</Text> :
//             <>
//                 <Heading.h5>Unvailable</Heading.h5>
//                 <Text mb="1em">{licenseServer.availability.reason}</Text>
//             </>
//         }
//
//         <Heading.h4>Description</Heading.h4>
//         <Box height={"4.5ch"}>{licenseServer.description}</Box>
//
//         <Box mb="1em">
//             {licenseServer.hiddenInGrantApplications ?
//                 <><TextSpan italic>Hidden</TextSpan> from </>
//                 :
//                 <><TextSpan italic>Visible</TextSpan> for </>
//             }
//             grant applicants
//         </Box>
//
//         <Flex mb="1em">
//             <Box width="40%">
//                 <Heading.h4>Price per unit</Heading.h4>
//                 {currencyFormatter(licenseServer.pricePerUnit)}
//             </Box>
//
//             <Box width="40%">
//                 <Heading.h4>Payment model</Heading.h4>
//                 {prettierString(licenseServer.paymentModel)}
//             </Box>
//
//             <Box width="20%">
//                 <Heading.h4>Priority</Heading.h4>
//                 {licenseServer.priority}
//             </Box>
//         </Flex>
//         <Button mb="1em" onClick={() => setIsEditing(true)} width={1}>Edit</Button>
//     </ExpandingCard>
// }

//     async function UpdateLicenseServer(): Promise<void> {
//         const address = addressInput.current?.value;
//         const priority = parseInt(priorityInput.current?.value ?? `${licenseServer.priority}`, 10);
//         const reason = unavailableReasonInput.current?.value;
//         const description = descriptionInput.current?.value;
//         const port = parseInt(portInput.current?.value ?? "", 10);
//         const pricePerUnit = parseInt(pricePerUnitInput.current?.value ?? "", 10) * 1_000_000;
//         const license = licenseInput.current?.value;
//
//         if (!address) return;
//         if (isNaN(priority)) return;
//         if (!isAvailable && !reason) return;
//         if (isNaN(port)) return;
//         if (!description) return;
//         if (isNaN(pricePerUnit)) return;
//
//         try {
//             await invokeCommand(licenseApi.update(bulkRequestOf({
//                 id: licenseServer.id,
//                 address,
//                 port,
//                 priority,
//                 availability: isAvailable ? {type: "available"} : {type: "unavailable", reason: reason!},
//                 category: licenseServer.category,
//                 description,
//                 hiddenInGrantApplications,
//                 license,
//                 paymentModel: paymentModelEdit,
//                 pricePerUnit,
//                 tags: licenseServer.tags
//             })));
//
//             reload();
//         } catch (e) {
//             snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to update."), false)
//         }
//     }
// }

const ExpandingCard = styled(Card)`
    transition: height 0.5s;
    overflow: hidden;
`;

const RotatingIcon = styled(Icon)`
    size: 14px;
    margin-right: 8px;
    margin-top: 9px;
    cursor: pointer;
    color: var(--blue, #f00);
    transition: transform 0.2s;
`;

export default LicenseServers;
