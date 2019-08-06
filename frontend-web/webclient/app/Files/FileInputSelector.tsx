import * as React from "react";
import FileSelector from "Files/FileSelector";
import {Cloud} from "Authentication/SDUCloudObject";
import {replaceHomeFolder} from "Utilities/FileUtilities";
import Input, {InputLabel} from "ui-components/Input";
import Button from "ui-components/Button";
import styled from "styled-components";
import {useState} from "react";

interface FileInputSelectorProps {
    path: string // selected file
    allowUpload?: boolean
    showError?: boolean
    inputRef?: React.RefObject<HTMLInputElement>
    defaultValue?: string
    isRequired?: boolean
    unitName?: string | React.ReactNode
    unitWidth?: string | number | undefined
    remove?: () => void
    onFileSelect: (file: { path: string }) => void
    disallowedPaths?: string[]

    canSelectFolders?: boolean
    onlyAllowFolders?: boolean
}

export const FileInputSelector: React.FunctionComponent<FileInputSelectorProps> = props => {
    const [visible, setVisible] = useState(false);
    const path = props.path ? props.path : "";
    const onUpload = () => {
        if (!props.allowUpload) return;
    };
    const uploadButton = props.allowUpload ? (<UploadButton onClick={onUpload}/>) : null;
    const removeButton = props.remove ? (<RemoveButton onClick={() => props.remove!()}/>) : null;
    const inputRefValueOrNull = props.inputRef && props.inputRef.current && props.inputRef.current.value;
    const inputValue = inputRefValueOrNull || replaceHomeFolder(path, Cloud.homeFolder);

    return <FileSelector
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

        trigger={
            <>
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
                    props.unitName ? <InputLabel width={props.unitWidth || "auto"}
                                                      rightLabel>{props.unitName}</InputLabel> : null
                }
                {uploadButton}
                {removeButton}
            </>
        }
    />
};

const FileSelectorInput = styled(Input)`
    cursor: pointer;
`;

interface FileSelectorButton {
    onClick: () => void
}

const UploadButton = ({onClick}: FileSelectorButton) => (
    <Button ml="5px" type="button" onClick={onClick}>Upload File</Button>);
const RemoveButton = ({onClick}: FileSelectorButton) => (<Button ml="5px" type="button" onClick={onClick}>✗</Button>);

