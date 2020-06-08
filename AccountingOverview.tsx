import {emptyPage, ReduxObject} from "DefaultObjects";
import {File} from "Files";
import {History} from "history";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, Card, Flex, Icon, Link, Text, theme} from "ui-components";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {AccountingOperations, AccountingProps, AccountingStateProps} from ".";
import {useCloudAPI} from "Authentication/DataHook";
import {Page} from "Types";

function AccountingOverview(props: AccountingProps & {history: History}): JSX.Element {
    const [selectedProject, setSelectedProject] = React.useState<string|null>(null);

    React.useEffect(() => {
        props.onInit();
        reload(true);
        props.setRefresh(() => reload(true));
        return () => props.setRefresh();
    }, []);

    function reload(loading: boolean): void {

    }

    const header = (
        <Heading.h2>Usage</Heading.h2>
    );

    const main = (
        <>
            <Box>
                <Card padding={15} margin={15}>
                    <Flex>
                        <Box width="20%">
                            <Text paddingTop="9px">Personal Project</Text>
                        </Box>
                        <Box width="15%" textAlign="center">
                            <Text>123 GB</Text>
                            <Text color="gray" fontSize={12}>STORAGE USED</Text>
                        </Box>
                        <Box width="15%" textAlign="center">
                            <Text>123M credits</Text>
                            <Text color="gray" fontSize={12}>STANDARD</Text>
                        </Box>
                        <Box width="15%" textAlign="center">
                            <Text>123M credits</Text>
                            <Text color="gray" fontSize={12}>HIGH MEMORY</Text>
                        </Box>
                        <Box width="15%" textAlign="center">
                            <Text>123M credits</Text>
                            <Text color="gray" fontSize={12}>GPU</Text>
                        </Box>
                        <Box width="20%" textAlign="right">
                            <Button onClick={() => setSelectedProject("Personal Project")}>Detailed usage</Button>
                        </Box>
                    </Flex>
                </Card>
                <Card padding={15} margin={15}>
                    <Flex>
                        <Box width="20%">
                            <Text paddingTop="9px">Lorem Ipsum Project</Text>
                        </Box>
                        <Box width="15%" textAlign="center">
                            <Text>123 GB</Text>
                            <span>
                                <Text color="gray" fontSize={12}>STORAGE USED</Text>
                            </span>
                        </Box>
                        <Box width="15%" textAlign="center">
                            <Text>123M credits</Text>
                            <Text color="gray" fontSize={12}>STANDARD</Text>
                        </Box>
                        <Box width="15%" textAlign="center">
                            <Text>123M credits</Text>
                            <Text color="gray" fontSize={12}>HIGH MEMORY</Text>
                        </Box>
                        <Box width="15%" textAlign="center">
                            <Text>123M credits</Text>
                            <Text color="gray" fontSize={12}>GPU</Text>
                        </Box>
                        <Box width="20%" textAlign="right">
                            <Button onClick={() => setSelectedProject("Lorem Ipsum Project")}>Detailed usage</Button>
                        </Box>
                    </Flex>
                </Card>
            </Box>

        </>
    );

    return (<MainContainer main={main} header={header} />);
}

const mapDispatchToProps = (dispatch: Dispatch): AccountingOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Dashboard"));
        dispatch(setActivePage(SidebarPages.None));
    },
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = ({applicationsBrowse}: ReduxObject): AccountingStateProps => ({
});


export default connect(mapStateToProps, mapDispatchToProps)(AccountingOverview);
