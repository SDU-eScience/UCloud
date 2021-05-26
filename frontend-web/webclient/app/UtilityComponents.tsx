import {KeyCode} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled, {keyframes, css} from "styled-components";
import {
    Box, Button, Divider, Flex, ButtonGroup, Link, Text
} from "ui-components";
import * as Heading from "ui-components/Heading";
import Input from "ui-components/Input";
import {height, HeightProps, padding, PaddingProps, width, WidthProps} from "styled-system";
import {useEffect, useRef} from "react";
import {Client} from "Authentication/HttpClientInstance";
import {Spacer} from "ui-components/Spacer";
import {ErrorWrapper} from "ui-components/Error";
import {ThemeColor} from "ui-components/theme";
import {stopPropagationAndPreventDefault} from "UtilityFunctions";

interface StandardDialog {
    title?: string;
    message: string | JSX.Element;
    onConfirm: () => void | Promise<void>;
    onCancel?: () => void;
    cancelText?: string;
    confirmText?: string;
    validator?: () => boolean;
    addToFront?: boolean;
    confirmButtonColor?: ThemeColor;
    cancelButtonColor?: ThemeColor;
}

export function addStandardDialog(
    {
        title,
        message,
        onConfirm,
        onCancel = () => undefined,
        validator = () => true,
        cancelText = "Cancel",
        confirmText = "Confirm",
        addToFront = false,
        cancelButtonColor = "red",
        confirmButtonColor = "green"
    }: StandardDialog
): void {
    const validate = (): void => {
        if (validator()) onConfirm();
        dialogStore.success();
    };

    dialogStore.addDialog((
        <div>
            <div>
                <Heading.h3>{title}</Heading.h3>
                {title ? <Divider /> : null}
                <div>{message}</div>
            </div>
            <Flex mt="20px">
                <Button onClick={dialogStore.failure} color={cancelButtonColor} mr="5px">{cancelText}</Button>
                <Button
                    onClick={validate}
                    color={confirmButtonColor}
                >
                    {confirmText}
                </Button>
            </Flex>
        </div>
    ), onCancel, addToFront);
}

interface InputDialog {
    title: string;
    placeholder?: string;
    cancelText?: string;
    confirmText?: string;
    validator?: (val: string) => boolean;
    validationFailureMessage?: string;
    addToFront?: boolean;
    type?: "input" | "textarea";
    help?: JSX.Element;
    width?: string;
}

export async function addStandardInputDialog(
    {
        title,
        help,
        validator = () => true,
        cancelText = "Cancel",
        confirmText = "Submit",
        addToFront = false,
        placeholder = "",
        validationFailureMessage = "error",
        type = "input",
        width = "300px",
    }: InputDialog
): Promise<{result: string}> {
    return new Promise((resolve, reject) => dialogStore.addDialog(
        <div>
            <div>
                <Heading.h3>{title}</Heading.h3>
                {title ? <Divider /> : null}
                {help ? help : null}
                <Input
                    id={"dialog-input"}
                    as={type}
                    width={width}
                    placeholder={placeholder}
                />
            </div>
            <Flex mt="20px">
                <Button onClick={dialogStore.failure} color="red" mr="5px">{cancelText}</Button>
                <Button
                    onClick={() => {
                        const elem = document.querySelector("#dialog-input") as HTMLInputElement;
                        if (validator(elem.value)) {
                            dialogStore.success();
                            resolve({result: elem.value});
                        } else snackbarStore.addFailure(validationFailureMessage, false);
                    }}
                    color="green"
                >
                    {confirmText}
                </Button>
            </Flex>
        </div>, () => reject({cancelled: true}), addToFront));
}

interface ConfirmCancelButtonsProps {
    confirmText?: string;
    cancelText?: string;
    height?: number | string;

    onConfirm(e: React.SyntheticEvent<HTMLButtonElement>): void;

    onCancel(e: React.SyntheticEvent<HTMLButtonElement>): void;
}

export const ConfirmCancelButtons = ({
    confirmText = "Confirm",
    cancelText = "Cancel",
    onConfirm,
    onCancel,
    height
}: ConfirmCancelButtonsProps): JSX.Element => (
    <ButtonGroup width="175px" height={height}>
        <Button onClick={onConfirm} type="button" color="green">{confirmText}</Button>
        <Button onClick={onCancel} type="button" color="red">{cancelText}</Button>
    </ButtonGroup>
);

export const NamingField: React.FunctionComponent<{
    onCancel: () => void;
    confirmText: string;
    inputRef: React.MutableRefObject<HTMLInputElement | null>;
    onSubmit: (e: React.SyntheticEvent) => void;
    defaultValue?: string;
}> = props => {
    const submit = React.useCallback((e) => {
        e.preventDefault();
        props.onSubmit(e);
    }, [props.onSubmit]);

    const keyDown = React.useCallback((e) => {
        if (e.keyCode === KeyCode.ESC) {
            props.onCancel();
        }
    }, [props.onCancel]);

    return (
        <form onSubmit={submit}>
            <Flex>
                <Input
                    pt="0px"
                    pb="0px"
                    pr="0px"
                    pl="0px"
                    noBorder
                    defaultValue={props.defaultValue ? props.defaultValue : ""}
                    fontSize={20}
                    maxLength={1024}
                    onKeyDown={keyDown}
                    borderRadius="0px"
                    type="text"
                    width="100%"
                    autoFocus
                    ref={props.inputRef}
                />
                <div onClick={stopPropagationAndPreventDefault} style={{transform: "translateY(2px)", marginBottom: "2px"}}>
                    <ConfirmCancelButtons
                        confirmText={props.confirmText}
                        cancelText="Cancel"
                        onConfirm={submit}
                        onCancel={props.onCancel}
                    />
                </div>
            </Flex>
        </form>
    );
};

const loremText = `
    Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam fringilla ipsum sem, id egestas risus mollis nec.
    Quisque sed efficitur lectus. Vestibulum magna erat, auctor at malesuada ut, scelerisque nec quam. Nam mattis at
    turpis nec vestibulum. Donec vel sapien tempus, porta odio sed, tincidunt metus. Integer ex turpis, pharetra at
    pellentesque ut, suscipit ut tortor. In hac habitasse platea dictumst. Morbi blandit fermentum gravida. Proin in
    ultricies mi, sed bibendum dui. Ut eros risus, ultrices vel nisi ac, sodales dictum arcu. Donec mattis urna nec
    arcu posuere efficitur. Integer luctus ac tellus non tempus. Proin sodales volutpat auctor. Nam laoreet, tellus
    in sodales egestas, odio ante bibendum odio, vel elementum ipsum quam at neque. Aliquam nec nisl sodales, placerat
    odio sit amet, aliquam metus.

    Aenean at nunc venenatis, ultricies ex id, varius enim. Fusce lacinia vulputate est vel bibendum. Ut at consequat
    nulla. Sed placerat erat dolor, in molestie neque egestas nec. Nulla rhoncus, mauris vitae hendrerit volutpat,
    dui mauris scelerisque quam, id rutrum tortor elit nec nunc. Etiam id elementum metus, a tristique mi. Aenean
    imperdiet, quam ac tempus feugiat, magna tellus tristique mauris, at vehicula quam mi vel nunc. Maecenas volutpat
    aliquam elit, eget elementum lorem interdum at.

    Vestibulum id lacus vitae nisi tristique tincidunt. Maecenas facilisis turpis vel metus auctor, in imperdiet dolor
    ultrices. Integer venenatis hendrerit vehicula. Integer id mauris erat. Vivamus posuere sollicitudin purus, vitae
    interdum massa posuere ut. Etiam et eleifend diam, in luctus diam. Aenean volutpat sem id lacus imperdiet malesuada.
    Morbi vulputate est eget leo luctus gravida. Aenean commodo a libero a feugiat. Ut ullamcorper elementum ex, quis
    ultrices nulla congue scelerisque. Phasellus bibendum eu metus ut efficitur.

    Praesent tempor ipsum ac euismod consequat. Quisque semper tortor ac magna aliquam, consectetur pretium sapien
    suscipit. Phasellus eu augue eget massa gravida feugiat sed sit amet ipsum. Aenean condimentum aliquam sapien vel
    suscipit. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Orci varius
    natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Integer scelerisque sem leo, nec bibendum
    elit vestibulum ut. Phasellus lacinia venenatis sollicitudin.

    Pellentesque molestie varius fermentum. In eu purus non lacus tincidunt lacinia. In hac habitasse platea dictumst.
    Quisque blandit sed nulla at accumsan. Donec finibus est eros, euismod iaculis diam porttitor ac. Duis nec arcu
    eleifend, ullamcorper quam et, ultricies metus. Vivamus non justo id quam lobortis volutpat.
`.split(" ").map(it => it.trim());

export const Lorem: React.FunctionComponent<{maxLength?: number}> = ({maxLength = 240}) => {
    let builder = "";
    let length = 0;
    let counter = 0;

    // eslint-disable-next-line no-constant-condition
    while (true) {
        const nextWord = loremText[counter];
        counter++;
        counter = counter % loremText.length;

        if (nextWord.length + length > maxLength) break;
        if (counter !== 1) builder += " ";
        builder += nextWord;
        length += nextWord.length;
    }
    return <>{builder}</>;
};

export const ImagePlaceholder = styled.div<WidthProps & HeightProps & PaddingProps>`
  ${width} ${height} ${padding}
  background-color: var(--purple, #f00);
`;

export const Center = styled(Box)`
  text-align: center;
`;

// eslint-disable-next-line @typescript-eslint/explicit-module-boundary-types
export function useTraceUpdate(props: any, msg?: string): void {
    const prev = useRef(props);
    useEffect(() => {
        const changedProps = Object.entries(props).reduce((ps, [k, v]) => {
            if (prev.current[k] !== v) {
                ps[k] = [prev.current[k], v];
            }
            return ps;
        }, {});
        if (Object.keys(changedProps).length > 0) {
            // This should only be used for debugging
            // eslint-disable-next-line no-console
            console.log('Changed props:', msg, changedProps);
        }
        prev.current = props;
    });
}

const shakeKeyframes = keyframes`
  10%, 90% {
    transform: translate3d(-1px, 0, 0);
  }

  20%, 80% {
    transform: translate3d(2px, 0, 0);
  }

  30%, 50%, 70% {
    transform: translate3d(-4px, 0, 0);
  }

  40%, 60% {
    transform: translate3d(4px, 0, 0);
  }
`;

export const shakeAnimation = css<{shaking?: boolean}>`
  &.shaking {
    transform: translate3d(0, 0, 0);
    animation: ${shakeKeyframes} 0.82s cubic-bezier(.36, .07, .19, .97) both;
  }

  ${p => p.shaking ? css`& {
    transform: translate3d(0, 0, 0);
    animation: ${shakeKeyframes} 0.82s cubic-bezier(.36, .07, .19, .97) both;
  }` : null}
`;

export const shakingClassName = "shaking";

export const ShakingBox = styled(Box) <{shaking?: boolean}>`
  ${shakeAnimation}
`;

const MISSING_COMPUTE_CREDITS = "NOT_ENOUGH_COMPUTE_CREDITS";
const MISSING_STORAGE_CREDITS = "NOT_ENOUGH_STORAGE_CREDITS";
const EXCEEDED_STORAGE_QUOTA = "NOT_ENOUGH_STORAGE_QUOTA";
const NOT_ENOUGH_LICENSE_CREDITS = "NOT_ENOUGH_LICENSE_CREDITS";

export function WalletWarning(props: {errorCode?: string}): JSX.Element | null {
    if (!props.errorCode) return null;
    return (
        <ErrorWrapper bg="lightRed"
            borderColor="red"
            width={1}
        >
            <WarningToOptions errorCode={props.errorCode} />
        </ErrorWrapper>
    );
}

function WarningToOptions(props: {errorCode: string}): JSX.Element {
    const trashFolder = Client.hasActiveProject ?
        `${Client.currentProjectFolder}/Members' Files/${Client.username}/Trash` :
        `${Client.homeFolder}Trash`;

    const workspacePath = Client.hasActiveProject ?
        `${Client.currentProjectFolder}/Members' Files/${Client.username}` : Client.homeFolder;

    const applyPath = Client.hasActiveProject ? "/project/grants/existing" : "/projects/browser/personal";

    switch (props.errorCode) {
        case MISSING_COMPUTE_CREDITS:
        case MISSING_STORAGE_CREDITS: {
            const computeOrStorage = props.errorCode === MISSING_COMPUTE_CREDITS ? "compute" : "storage";
            return (
                <Box mb="8px">
                    <Heading.h4 mb="20px" color="#000">You do not have enough {computeOrStorage} credits.</Heading.h4>
                    <Spacer
                        left={<Text color="#000">Apply for more {computeOrStorage} resources.</Text>}
                        right={<Link to={applyPath}><Button width="150px" height="30px">Apply</Button></Link>}
                    />
                </Box>
            );
        }
        case NOT_ENOUGH_LICENSE_CREDITS: {
            return (
                <Box mb="8px">
                    <Heading.h4 mb="20px" color="#000">You do not have enough license credits.</Heading.h4>
                    <Spacer
                        left={<Text color="#000">You do not have enough credits to use this license. Even free licenses
                            requires a non-zero positive balance.</Text>}
                        right={<Link to={applyPath}><Button width="150px" height="30px">Apply</Button></Link>}
                    />
                </Box>
            )
        }
        case EXCEEDED_STORAGE_QUOTA:
            return (
                <>
                    <Heading.h4 mb="20px" color="#000">You do not have enough storage credit. To get more storage, you
                        could do one of the following:</Heading.h4>
                    <Box mb="8px">
                        <Spacer
                            left={<Text color="#000">Delete files. Your personal workspace files, including job results
                                and trash also count towards your storage quota.</Text>}
                            right={<Link to={"/files/"}><Button width="150px" height="30px">Workspace
                                files</Button></Link>}
                        />
                    </Box>
                    <Box mb="8px">
                        <Spacer
                            left={<Text color="#000">Empty your trash folder.</Text>}
                            right={<Link to={"/files/"}><Button width="150px" height="30px">To trash
                                folder</Button></Link>}
                        />
                    </Box>
                    <Box mb="8px">
                        <Spacer
                            left={<Text color="#000">Apply for more storage resources.</Text>}
                            right={<Link to={applyPath}><Button width="150px" height="30px">Apply</Button></Link>}
                        />
                    </Box>
                </>
            );
        default:
            return <></>;
    }
}
