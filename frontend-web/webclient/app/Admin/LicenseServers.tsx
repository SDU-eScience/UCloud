import {setActivePage, setLoading, SetStatusLoading} from "Navigation/Redux/StatusActions";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {SidebarPages} from "ui-components/Sidebar";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {Button, Input, Label, Flex, Box, Text, Icon, Tooltip} from "ui-components";
import * as Heading from "ui-components/Heading";
import {usePromiseKeeper} from "PromiseKeeper";
import {Client} from "Authentication/HttpClientInstance";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SnackType} from "Snackbar/Snackbars";
import styled from "styled-components";
import {defaultErrorHandler} from "UtilityFunctions";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {addStandardDialog} from "UtilityComponents";

interface LicenseServer {
    id: string,
    name: string;
    address: string;
    port: string;
    license: string | null;
}

async function loadLicenseServers(): Promise<LicenseServer[]> {
    const {response} = await Client.get<Array<LicenseServer>>(`/app/license/listAll`);
    return response.map(item => {
        const entry: LicenseServer = {
            id: item.id,
            name: item.name,
            address: item.address,
            port: item.port,
            license: item.license
        };
        return entry;
    });
}


function LicenseServers(props: LicenseServersOperations) {
    const [submitted, setSubmitted] = React.useState(false);
    const [name, setName] = React.useState("");
    const [address, setAddress] = React.useState("");
    const [port, setPort] = React.useState("");
    const [license, setLicense] = React.useState("");
    const [nameError, setNameError] = React.useState(false);
    const [addressError, setAddressError] = React.useState(false);
    const [portError, setPortError] = React.useState(false);
    const [licenseServers, setLicenseServers] = React.useState<LicenseServer[]>([]);

    const promiseKeeper = usePromiseKeeper();

    React.useEffect(() => {
        props.setActivePage();
    }, []);


    async function setLicenseServersOnInit() {
        setLicenseServers(await loadLicenseServers());
    }

    React.useEffect(() =>  {
        setLicenseServersOnInit();
    }, []);

 


    async function submit(e: React.SyntheticEvent) {
        e.preventDefault();

        let hasNameError = false;
        let hasAddressError = false;
        let hasPortError = false;

        if (!name) hasNameError = true;
        if (!address) hasAddressError = true;
        if (!port) hasPortError = true;

        setNameError(hasNameError)
        setAddressError(hasAddressError)
        setPortError(hasPortError);

        if (!hasNameError && !hasAddressError && !hasPortError) {
            try {
                props.setLoading(true);
                await promiseKeeper.makeCancelable(
                    Client.post("/api/app/license/new", {name, address, port, license}, "")
                ).promise;
                snackbarStore.addSnack(
                    {message: `License server '${name}' successfully added`, type: SnackType.Success}
                );
                setName(() => "");
                setAddress(() => "");
                setPort(() => "");
                setLicense(() => "");
            } catch (e) {
                const status = defaultErrorHandler(e);
            } finally {
                props.setLoading(false);
                setLicenseServersOnInit()
            }
        }
    }

    if (!Client.userIsAdmin) return null;

    /*const {
        nameError,
        addressError,
        portError,
        name,
        address,
        port,
        submitted
    } = state;*/

    const LeftAlignedTableHeader = styled(TableHeader)`
        text-align: left;
    `;






    return (
        <MainContainer
            header={<Heading.h1>License Servers</Heading.h1>}
            headerSize={64}
            main={(
                <>
                    <Box maxWidth={800} marginLeft="auto" marginRight="auto">
                        <p>Admins can manage license servers on this page.</p>
                        <form onSubmit={e => submit(e)}>
                            <Label mb="1em">
                                Name
                                <Input
                                    value={name}
                                    color={nameError ? "red" : undefined}
                                    onChange={e => setName(e.target.value)}
                                    placeholder="Identifiable name for the license server"
                                />
                            </Label>
                            <Box marginBottom={30}>
                                <Flex height={45}>
                                    <Label mb="1em">
                                        Address
                                        <Input
                                            value={address}
                                            rightLabel
                                            color={addressError ? "red" : undefined}
                                            onChange={e => setAddress(e.target.value)}
                                            placeholder="IP address or URL"
                                        />
                                    </Label>
                                    <Label mb="1em" width="30%">
                                        Port
                                        <Input
                                            value={port}
                                            leftLabel
                                            color={portError ? "red" : undefined}
                                            onChange={e => setPort(e.target.value)}
                                            placeholder="Port"
                                        />
                                    </Label>
                                </Flex>
                            </Box>
                            <Label mb="1em">
                                Key
                                <Input
                                    value={license}
                                    color={portError ? "red" : undefined}
                                    onChange={e => setLicense(e.target.value)}
                                    placeholder="License or key (if needed)"
                                />
                            </Label>
                            <Button
                                type="submit"
                                color="green"
                                disabled={submitted}
                            >
                                Add License Server
                            </Button>
                        </form>

                        <Box mt={30}>
                            { (licenseServers.length > 0) ? (
                                <Table>
                                    <LeftAlignedTableHeader>
                                        <TableRow>
                                            <TableHeaderCell width="300px">Name</TableHeaderCell>
                                            <TableHeaderCell>Address</TableHeaderCell>
                                            <TableHeaderCell width={50}>Key</TableHeaderCell>
                                            <TableHeaderCell width={50}>Delete</TableHeaderCell>
                                        </TableRow>
                                    </LeftAlignedTableHeader>
                                    <tbody>
                                        {licenseServers.map(licenseServer => (
                                            <TableRow key={licenseServer.id}>
                                                <TableCell>{licenseServer.name}</TableCell>
                                                <TableCell>{licenseServer.address}</TableCell>
                                                <TableCell textAlign="right">
                                                    { licenseServer.license !== null ? (
                                                        <Tooltip
                                                            tooltipContentWidth="400px"
                                                            wrapperOffsetLeft="0"
                                                            wrapperOffsetTop="4px"
                                                            right="0"
                                                            top="1"
                                                            mb="50px"
                                                            trigger={(
                                                                <Icon
                                                                    size="20px"
                                                                    mt="4px"
                                                                    mr="8px"
                                                                    color="gray"
                                                                    name="key"
                                                                />
                                                            )}
                                                        >
                                                            { licenseServer.license }
                                                        </Tooltip>
                                                    ) : (
                                                        <Text></Text>
                                                    )}
                                                </TableCell>
                                                <TableCell textAlign="right">
                                                    <Button
                                                        color={"red"}
                                                        type={"button"}
                                                        onClick={() => addStandardDialog({
                                                            title: `Are you sure?`,
                                                            message: (
                                                                <Box>
                                                                    <Text>
                                                                        Remove license server {licenseServer.id}?
                                                                    </Text>
                                                                </Box>
                                                            ),
                                                            onConfirm: async () => {
                                                                /*await invokeCommand(updateLicenseServer(
                                                                    {
                                                                        licenseId: licenseServer.id,
                                                                        changes: [
                                                                            {
                                                                                name: licenseServer.name,
                                                                                address: licenseServer.address,
                                                                                port: licenseServer.port,
                                                                                license: licenseServer.license
                                                                            }
                                                                        ]
                                                                    }
                                                                ));*/
                                                                await setLicenseServers(await loadLicenseServers());
                                                            }
                                                        })}
                                                    >
                                                        <Icon size={16} name="trash" />
                                                    </Button>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </tbody>
                                </Table>
                            ) : (
                                <Text textAlign="center">No license servers found</Text>
                            )}
                        </Box>
                    </Box>
                </>
            )}
        />
    );

}

interface LicenseServersOperations extends SetStatusLoading {
    setActivePage: () => void;
}

const mapDispatchToProps = (dispatch: Dispatch): LicenseServersOperations => ({
    setActivePage: () => dispatch(setActivePage(SidebarPages.Admin)),
    setLoading: loading => dispatch(setLoading(loading))
});

export default connect(null, mapDispatchToProps)(LicenseServers);