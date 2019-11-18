import * as Types from "Applications";
import {createFileSystem, listFileSystems, SharedFileSystem, SharedFileSystemMount} from "Applications/FileSystems";
import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import {APICallParameters, APICallState, useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {emptyPage} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import * as Pagination from "Pagination";
import * as React from "react";
import {RefObject, useState} from "react";
import {createRef} from "react";
import * as ReactModal from "react-modal";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Page} from "Types";
import {Input} from "ui-components";
import {Button, ButtonGroup} from "ui-components";
import {Icon} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import Divider from "ui-components/Divider";
import Flex from "ui-components/Flex";
import * as Heading from "ui-components/Heading";
import Label from "ui-components/Label";
import {TextP} from "ui-components/Text";
import {dateToString} from "Utilities/DateUtilities";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {addStandardDialog} from "UtilityComponents";

interface SharedFileSystemParameter extends ParameterProps {
    parameter: Types.SharedFileSystemParameter;
}

export const SharedFileSystemParameter: React.FunctionComponent<SharedFileSystemParameter> = props => {
    const {parameter, parameterRef} = props;
    if (parameter.fsType === "EPHEMERAL") return null;

    const [isCommandLoading, invokeCommand] = useAsyncCommand();
    const [isMountDialogOpen, setIsMountDialogOpen] = useState(false);
    const [selectedMount, setSelectedMount] = useState<SharedFileSystemMount | null>(null);
    const [currentPage, setListParams] = useCloudAPI<Page<SharedFileSystem>>(
        listFileSystems({}),
        emptyPage
    );

    const application = props.application;
    const mountLocation = props.parameter.mountLocation;

    return (
        <BaseParameter parameter={parameter}>
            <Box mb={16}>
                <Flex>
                    <PointerInput
                        readOnly
                        placeholder={"No selected file system"}
                        onClick={() => setIsMountDialogOpen(true)}
                        value={!selectedMount ? "" : `${selectedMount.sharedFileSystem.title} (${dateToString(selectedMount.sharedFileSystem.createdAt)})`}
                    />

                    <input
                        type="hidden"
                        ref={parameterRef as RefObject<HTMLInputElement>}
                        value={!selectedMount ? "" : selectedMount.sharedFileSystem.id}
                    />

                    <ButtonGroup ml={"6px"} width={"115px"}>
                        <Button
                            fullWidth
                            type={"button"}
                            color={"blue"}
                            disabled={isCommandLoading}
                            onClick={async () => {
                                const resp = await invokeCommand<{id: string}>(createFileSystem({title: application.metadata.title}));
                                if (resp !== null) {
                                    setSelectedMount(fakeMount(resp.id, application.metadata.title, mountLocation));
                                }
                            }}
                        >
                            New
                    </Button>

                        <ClickableDropdown
                            trigger={(
                                <Button color={"darkBlue"} type={"button"} className={"last"}>
                                    <Icon name="chevronDown" size=".7em" m=".7em" />
                                </Button>
                            )}
                            options={[{text: "New custom FS", value: "fs_customize"}]}
                            onChange={async () => {
                                const {command} = await createNewDialog();
                                if (command !== undefined && !isCommandLoading) {
                                    const resp = await invokeCommand<{id: string}>(command);
                                    if (resp !== null) {
                                        setSelectedMount(fakeMount(resp.id, command.parameters["title"], mountLocation));
                                    }
                                    setListParams(listFileSystems({}));
                                }
                            }}
                        />
                    </ButtonGroup>
                </Flex>

                <MountDialogStep1
                    isOpen={isMountDialogOpen}
                    selectedMount={selectedMount}
                    currentPage={currentPage}
                    onPageChanged={(page) => setListParams(listFileSystems({page}))}
                    resolve={async (data) => {
                        setIsMountDialogOpen(false);
                        const fs = await mountDialog(selectedMount, data.sharedFileSystem);
                        if (fs !== null) {
                            const mount = {mountedAt: mountLocation, sharedFileSystem: fs};
                            setSelectedMount(mount);
                        }
                    }}

                />
            </Box>
        </BaseParameter>
    );
};

function fakeMount(id: string, name: string, mountedAt: string): SharedFileSystemMount {
    return {
        mountedAt,
        sharedFileSystem: {
            id,
            backend: "",
            createdAt: new Date().getTime(),
            title: name,
            owner: Client.username ?? "nobody"
        }
    };
}

const PointerInput = styled(Input)`
    cursor: pointer;
`;

async function createNewDialog(): Promise<{command?: APICallParameters}> {
    return new Promise(resolve => {
        const ref = createRef<HTMLInputElement>();
        const validator = () => {
            const value = ref.current!.value;
            if (value.length === 0) {
                snackbarStore.addSnack({
                    type: SnackType.Failure,
                    message: "Title cannot be empty"
                });
                return false;
            }
            return true;
        };

        const onConfirm = () => {
            const title = ref.current!.value;
            resolve({command: createFileSystem({title})});
        };

        addStandardDialog({
            title: "Create new shared file system",
            message: (
                <form
                    onSubmit={e => {
                        e.preventDefault();
                        onConfirm();
                        dialogStore.success();
                    }}
                >
                    <Label htmlFor={"sharedFsTitle"}>Title</Label>
                    <Input autoFocus id={"sharedFsTitle"} ref={ref} placeholder={"Spark FS"} />
                </form>
            ),
            onConfirm,
            onCancel: () => undefined,
            validator
        });
    });
}

const MountDialogStep1: React.FunctionComponent<{
    isOpen: boolean,
    selectedMount: SharedFileSystemMount | null,
    currentPage: APICallState<Page<SharedFileSystem>>,
    onPageChanged: (page: number) => void,
    resolve: (data: {sharedFileSystem?: SharedFileSystem}) => void
}> = props => (
    <ReactModal
        isOpen={props.isOpen}
        shouldCloseOnEsc={true}
        onRequestClose={() => props.resolve({})}
        ariaHideApp={false}
        style={defaultModalStyle}
    >
        <>
            <div>
                <Heading.h3>Shared File Systems</Heading.h3>
                <Divider />
            </div>
            <Pagination.List
                loading={props.currentPage.loading}
                page={props.currentPage.data}
                onPageChanged={page => props.onPageChanged(page)}
                pageRenderer={page => (
                    <div>
                        {props.currentPage.data.items.map(fs => (
                            <Flex alignItems={"center"} mb={8} key={fs.id}>
                                <TextP mr={8}>
                                    {fs.title} <br />
                                    Created at: {dateToString(fs.createdAt)}
                                </TextP>
                                <Box ml={"auto"} />
                                <Button
                                    type={"button"}
                                    ml={8}
                                    onClick={() => {
                                        props.resolve({sharedFileSystem: fs});
                                    }}
                                >
                                    Mount
                                </Button>
                            </Flex>
                        ))}
                    </div>
                )}
            />
        </>
    </ReactModal>
);

async function mountDialog(
    selectedMount: SharedFileSystemMount | null,
    sharedFileSystem?: SharedFileSystem
): Promise<SharedFileSystem | null> {
    if (sharedFileSystem === undefined) return null;
    if (selectedMount !== null && selectedMount.sharedFileSystem.id === sharedFileSystem.id) {
        snackbarStore.addSnack({
            type: SnackType.Failure,
            message: "File system has already been mounted"
        });
        return null;
    }

    return sharedFileSystem;
}

