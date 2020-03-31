import {Client} from "Authentication/HttpClientInstance";
import FileSelector from "Files/FileSelector";
import * as React from "react";
import {useState} from "react";
import styled from "styled-components";
import {Button, Flex, Icon} from "ui-components";
import Input, {InputLabel} from "ui-components/Input";
import {replaceHomeOrProjectFolder} from "Utilities/FileUtilities";

interface FileInputSelectorProps {
    path: string; // selected file
    allowUpload?: boolean;
    showError?: boolean;
    inputRef?: React.RefObject<HTMLInputElement>;
    defaultValue?: string;
    isRequired?: boolean;
    unitName?: string | React.ReactNode;
    unitWidth?: string | number | undefined;
    remove?: () => void;
    onFileSelect: (file: {path: string}) => void;
    disallowedPaths?: string[];

    canSelectFolders?: boolean;
    onlyAllowFolders?: boolean;
}

export const FileInputSelector: React.FunctionComponent<FileInputSelectorProps> = props => {
    const [visible, setVisible] = useState(false);
    const path = props.path ? props.path : "";

    const removeButton = props.remove ? (<RemoveButton onClick={() => props.remove!()} />) : null;
    const inputRefValueOrNull = props.inputRef?.current?.value;
    const inputValue = inputRefValueOrNull ?? replaceHomeOrProjectFolder(path, Client);

    return (
        <FileSelector
            visible={visible}

            canSelectFolders={props.canSelectFolders}
            onlyAllowFolders={props.onlyAllowFolders}

            disallowedPaths={props.disallowedPaths}

            onFileSelect={file => {
                if (file !== null) {
                    props.onFileSelect(file);
                }

                setVisible(false);
            }}

            trigger={(
                <Flex>
                    <FileSelectorInput
                        defaultValue={props.defaultValue}
                        showError={props.showError && props.isRequired}
                        ref={props.inputRef}
                        required={props.isRequired}
                        placeholder="No file selected"
                        value={inputValue}
                        rightLabel={!!props.unitName}
                        onChange={() => undefined}
                        onClick={() => setVisible(true)}
                    />
                    {
                        !props.unitName ? null : (
                            <InputLabel width={props.unitWidth ?? "auto"} backgroundColor="lightBlue" rightLabel>
                                {props.unitName}
                            </InputLabel>
                        )}
                    {removeButton}
                </Flex>
            )}
        />
    );
};

const FileSelectorInput = styled(Input)`
    cursor: pointer;
`;

interface FileSelectorButton {
    onClick: () => void;
}

const RemoveButton = ({onClick}: FileSelectorButton): JSX.Element => (
    <Button color="red" ml="8px" onClick={onClick}><Icon name="close" size="1em" /></Button>
);
