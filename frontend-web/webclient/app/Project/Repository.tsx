import * as React from "react";
import {useCloudAPI, APICallParameters} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import {List, Button, Flex, Input} from "ui-components";
import * as Heading from "ui-components/Heading";
import * as ReactModal from "react-modal";
import {Page} from "Types";
import * as Pagination from "Pagination";
import {ListRow} from "ui-components/List";
import {MainContainer} from "MainContainer/MainContainer";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Client} from "Authentication/HttpClientInstance";
import {errorMessageOrDefault} from "UtilityFunctions";
import {addStandardDialog, addStandardInputDialog} from "UtilityComponents";

const baseContext = "/projects/repositories";


interface RepositoryCreateRequest {
    name: string;
}

function repositoryCreateRequest(payload: RepositoryCreateRequest): APICallParameters<RepositoryCreateRequest> {
    return {
        method: "POST",
        path: baseContext,
        payload
    };
}

interface RepositoryListRequest {
    itemsPerPage: number;
    page: number;
}

function repositoryListRequest(payload: RepositoryListRequest): APICallParameters<RepositoryListRequest> {
    return {
        path: baseContext,
        method: "GET",
        payload
    };
}

function Repositories(): JSX.Element {
    const [repositories, setParameters, parameters] = useCloudAPI<Page<{name: string}>>(repositoryListRequest({
        itemsPerPage: 25, page: 0
    }), emptyPage);
    const [creatingRepo, setCreatingRepo] = React.useState(false);
    const createRepoRef = React.useRef<HTMLInputElement>(null);

    const reload = (): void => setParameters(repositoryListRequest({
        itemsPerPage: parameters.payload.itemsPerPage,
        page: parameters.payload.pageNumber
    }));

    return <MainContainer
        main={<Pagination.List
            page={repositories.data}
            loading={repositories.loading}
            onPageChanged={(newPage, page) => setParameters(repositoryListRequest({
                page: newPage,
                itemsPerPage: page.itemsPerPage
            }))}
            pageRenderer={renderPage}
            customEmptyPage={<Flex>
                No repositories found for this project
            </Flex>}
        />}
        sidebar={
            <Button width={1} onClick={() => setCreatingRepo(true)}>Create repository</Button>
        }
        additional={<ReactModal isOpen={creatingRepo} shouldCloseOnEsc shouldCloseOnOverlayClick onRequestClose={() => setCreatingRepo(false)} style={defaultModalStyle}>
            <Heading.h2>New group</Heading.h2>
            <form onSubmit={createRepository}>
                <Flex>
                    <Input placeholder="Group name..." ref={createRepoRef} />
                    <Button ml="5px">Create</Button>
                </Flex>
            </form>
        </ReactModal>}
    />;

    function renderPage(page: Page<{name: string}>): JSX.Element {
        return (<List>
            {page.items.map(repo => (
                <ListRow
                    key={repo.name}
                    select={() => undefined}
                    isSelected={false}
                    left={repo.name}
                    navigate={() => undefined}
                    right={<>
                        <Button onClick={() => promptRenameRepository(repo.name)}>Rename</Button>
                        <Button ml="8px" color="red" onClick={() => promptDeleteRepository(repo.name)}>Delete</Button>
                    </>}
                />
            ))}
        </List>);
    }

    async function promptRenameRepository(oldName: string): Promise<void> {
        const result = await addStandardInputDialog({
            title: `Rename ${oldName}`,
            addToFront: false,
            confirmText: "Rename",
            defaultValue: "",
            placeholder: "New repository name",
            validationFailureMessage: "New name can't be empty.",
            cancelText: "Cancel",
            validator: val => {
                if (!val) return false;
                if (val.length > 50) return false;
                return true;
            }
        });

        if ("cancelled" in result) return;

        try {
            await Client.post(`${baseContext}/update`, {oldName, newName: result.result});
            reload();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to rename repository."));
        }
    }

    function promptDeleteRepository(name: string): void {
        addStandardDialog({
            title: "Delete repository?",
            message: <>
                Delete {name}?<br />
                This will also delete associated files. This Cannot be undone.
            </>,
            confirmText: "Delete",
            onConfirm: () => deleteRepository(name)
        });
    }

    async function deleteRepository(name: string): Promise<void> {
        try {
            await Client.delete(baseContext, {name});
            reload();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to delete repo."));
        }
    }

    async function createRepository(e: React.SyntheticEvent): Promise<void> {
        e.preventDefault();
        const name = createRepoRef.current?.value ?? "";
        if (!name) {
            snackbarStore.addFailure("Repository name can't be empty.");
            return;
        }

        try {
            const req = repositoryCreateRequest({name});
            await Client.post(req.path!, req.payload);
            setCreatingRepo(false);
            reload();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to create repository"));
        }
    }
}

export default Repositories;
