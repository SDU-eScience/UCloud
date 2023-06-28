import * as React from "react";
import {DependencyList, EventHandler, MouseEvent, useCallback, useEffect, useMemo, useRef, useState} from "react";
import {PageV2} from "@/UCloud";
import {emptyPageV2, pageV2Of} from "@/DefaultObjects";
import {InvokeCommand, useCloudCommand} from "@/Authentication/DataHook";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import * as Pagination from "@/Pagination";
import {PageRenderer} from "@/Pagination/PaginationV2";
import {ToggleSetHook, useToggleSet} from "@/Utilities/ToggleSet";
import {Operation, Operations} from "@/ui-components/Operation";
import {NamingField} from "@/UtilityComponents";
import {ListRow, ListStatContainer} from "@/ui-components/List";
import {doNothing, EmptyObject, errorMessageOrDefault} from "@/UtilityFunctions";
import {Dispatch} from "redux";
import {useScrollStatus} from "@/Utilities/ScrollStatus";
import {useDispatch} from "react-redux";
import {NavigateFunction, useNavigate} from "react-router";
import {Box, List} from "@/ui-components/index";
import {useLoading, useTitle} from "@/Navigation/Redux/StatusActions";
import {StickyBox} from "@/ui-components/StickyBox";
import MainContainer from "@/MainContainer/MainContainer";
import {BrowseType} from "@/Resource/BrowseType";

interface BrowseProps<T> {
    preloadedResources?: T[];
    generateCall: (next?: string) => APICallParameters;
    pageRenderer: PageRenderer<T>;
    onLoad?: (newItems: T[]) => void;

    isSearch?: boolean;
    browseType?: BrowseType;
    loadingRef?: React.MutableRefObject<boolean>;
    reloadRef?: React.MutableRefObject<() => void>;
    onReload?: () => void;
    setRefreshFunction?: boolean;
    hide?: boolean;
    toggleSet?: ToggleSetHook<T>;
    pageSizeRef?: React.MutableRefObject<number>;
}

export function StandardBrowse<T>(props: React.PropsWithChildren<BrowseProps<T>>): JSX.Element | null {
    const hasPreloadedResources = !!props.preloadedResources;
    const [remoteResources, setRemoteResources] = useState<PageV2<T>>(emptyPageV2);
    const [error, setError] = useState<string | undefined>(undefined)
    const [loading, invokeCommand] = useCloudCommand();
    const [infScroll, setInfScroll] = useState(0);
    const resources = useMemo(() =>
        hasPreloadedResources ? pageV2Of(...props.preloadedResources!) : remoteResources,
        [props.preloadedResources, remoteResources]);

    useEffect(() => {
        if (props.onLoad) props.onLoad(resources.items);
    }, [resources, props.onLoad]);

    const isLoading = hasPreloadedResources ? false : loading;
    if (props.loadingRef) props.loadingRef.current = isLoading;

    if (props.pageSizeRef && !props.preloadedResources) {
        props.pageSizeRef.current = remoteResources.items.length;
    }

    const reload = useCallback(async () => {
        setError(undefined);
        if (hasPreloadedResources) return;
        try {
            const result = await invokeCommand<PageV2<T>>(
                props.generateCall(),
                {defaultErrorHandler: false}
            );
            if (result != null) {
                setInfScroll(prev => prev + 1);
                setRemoteResources(result);
            }
        } catch (e) {
            setError(errorMessageOrDefault(e, "Failed to load the page. Try again later."));
        }
        if (props.onReload) props.onReload();
    }, [props.generateCall, props.onReload, hasPreloadedResources]);

    if (props.reloadRef) props.reloadRef.current = reload;

    const loadMore = useCallback(async () => {
        setError(undefined);
        if (hasPreloadedResources) return;
        if (resources.next) {
            try {
                const result = await invokeCommand<PageV2<T>>(
                    props.generateCall(resources.next),
                    {defaultErrorHandler: false}
                );

                if (result != null) setRemoteResources(result);
            } catch (e) {
                setError(errorMessageOrDefault(e, "Failed to load the page. Try again later."));
            }
        }
    }, [props.generateCall, hasPreloadedResources, resources.next]);

    if (props.toggleSet) props.toggleSet.allItems.current = resources.items;

    useEffect(() => {reload().then(doNothing).catch(doNothing)}, [reload]);

    if (props.setRefreshFunction !== false) {
        useRefreshFunction(reload);
    }

    if (props.hide === true) return null;

    return <>
        <Pagination.ListV2 page={resources} pageRenderer={props.pageRenderer} loading={isLoading}
            onLoadMore={loadMore} customEmptyPage={props.pageRenderer([], {hasNext: false})} error={error}
            infiniteScrollGeneration={infScroll} dataIsStatic={hasPreloadedResources} />
    </>
}

export interface ItemRenderer<T, CB = any> {
    Icon?: React.FunctionComponent<{resource?: T, size: string; browseType: BrowseType; callbacks: CB;}>;
    MainTitle?: React.FunctionComponent<{resource?: T; browseType: BrowseType; callbacks: CB;}>;
    Stats?: React.FunctionComponent<{resource?: T; browseType: BrowseType; callbacks: CB;}>;
    ImportantStats?: React.FunctionComponent<{resource?: T; callbacks: CB; browseType: BrowseType}>;
}

interface ItemRowProps<T, CB> {
    item?: T;
    browseType: BrowseType;
    renderer: ItemRenderer<T, CB>;

    toggleSet: ToggleSetHook<T>;
    navigate?: (item: T) => void;

    operations: Operation<T, CB>[];
    callbacks: CB;
    itemTitle: string;
    itemTitlePlural?: string;
    disableSelection?: boolean;
    highlight?: boolean;

    renaming?: RenamingState<T>;
}

export const ItemRow = <T, CB>(
    props: React.PropsWithChildren<ItemRowProps<T, CB>>
): JSX.Element | null => {
    const renderer = props.renderer;
    const renameInputRef = useRef<HTMLInputElement>(null);
    const openOperationsRef = useRef<(left: number, top: number) => void>(doNothing);

    const onRename = useCallback(async () => {
        if (props.renaming && renameInputRef.current && props.item) {
            const text = renameInputRef.current.value;
            await props.renaming.onRename(props.item, text);
            props.renaming.onRenameCancel();
        }
    }, [props.item, props.renaming]);

    const renameValue = useMemo(() => {
        if (!props.item) return "";
        return props.renaming?.renameValue?.(props.item) ?? "";
    }, [props.renaming, props.item]);

    const onContextMenu = useCallback<EventHandler<MouseEvent<never>>>((e) => {
        e.stopPropagation();
        e.preventDefault();
        openOperationsRef.current(e.clientX, e.clientY);
    }, []);

    return <ListRow
        disableSelection={props.disableSelection}
        onContextMenu={onContextMenu}
        icon={renderer.Icon ? <renderer.Icon resource={props.item} size={"36px"} browseType={props.browseType} callbacks={props.callbacks} /> : null}
        highlight={props.highlight}
        left={
            props.item && props.renaming?.isRenaming(props.item) === true ?
                <NamingField onCancel={props.renaming?.onRenameCancel ?? doNothing} confirmText={"Rename"}
                    inputRef={renameInputRef} onSubmit={onRename}
                    defaultValue={renameValue} /> :
                renderer.MainTitle ? <renderer.MainTitle browseType={props.browseType} resource={props.item} callbacks={props.callbacks} /> : null
        }
        isSelected={props.item && props.toggleSet.checked.has(props.item)}
        select={() => props.item ? props.toggleSet.toggle(props.item) : 0}
        navigate={props.navigate && props.item ? () => props.navigate?.(props.item!) : undefined}
        leftSub={
            <ListStatContainer>
                {renderer.Stats ? <renderer.Stats browseType={props.browseType} resource={props.item} callbacks={props.callbacks} /> : null}
            </ListStatContainer>
        }
        right={
            <>
                {renderer.ImportantStats ?
                    <renderer.ImportantStats resource={props.item} browseType={props.browseType} callbacks={props.callbacks} /> : null}
                {props.item ?
                    <Operations
                        selected={props.toggleSet.checked.items}
                        location={"IN_ROW"}
                        entityNameSingular={props.itemTitle}
                        entityNamePlural={props.itemTitlePlural}
                        extra={props.callbacks}
                        operations={props.operations}
                        row={props.item}
                        openFnRef={openOperationsRef}
                        forceEvaluationOnOpen
                    /> : null}
            </>
        }
    />;
}

export const ItemRowMemo = React.memo(ItemRow) as typeof ItemRow;

interface RenamingState<T> {
    setRenaming: (item: T) => void;
    isRenaming: (item: T) => boolean;
    renameValue: (item: T) => string;
    onRename: (item: T, text: string) => void;
    onRenameCancel: () => void;
}

export function useRenamingState<T>(
    nameExtractor: (item: T) => string,
    nameExtractorDeps: DependencyList,
    eqFn: (a: T, b: T) => boolean,
    eqFnDeps: DependencyList,
    onRename: (item: T, text: string) => Promise<void>,
    onRenameDeps: DependencyList,
): RenamingState<T> {
    const memoOnRename = useMemo(() => onRename, onRenameDeps);
    const memoNameExtractor = useMemo(() => nameExtractor, nameExtractorDeps);
    const memoEqFn = useMemo(() => eqFn, eqFnDeps);
    const [renaming, setRenaming] = useState<T | null>(null);
    const isRenaming = useCallback((item: T): boolean => {
        return renaming !== null && memoEqFn(renaming, item);
    }, [memoEqFn, renaming]);
    const onRenameCancel = useCallback(() => {
        setRenaming(null);
    }, [setRenaming]);

    return useMemo((): RenamingState<T> => ({
        setRenaming,
        isRenaming,
        renameValue: memoNameExtractor,
        onRename: memoOnRename,
        onRenameCancel
    }), [isRenaming, setRenaming, onRenameCancel, memoNameExtractor, memoOnRename]);
}

export interface StandardCallbacks<T = any> {
    commandLoading: boolean;
    invokeCommand: InvokeCommand;
    reload: () => void;
    onSelect?: (resource: T) => void;
    embedded: boolean;
    dispatch: Dispatch;
    navigate: NavigateFunction;
}

interface StandardListBrowse<T, CB> {
    preloadedResources?: T[];
    generateCall: (next?: string) => APICallParameters;
    renderer: ItemRenderer<T, CB>;
    operations: Operation<T, StandardCallbacks<T> & CB>[];
    title: string;
    titlePlural?: string;
    embedded?: boolean | "inline" | "dialog";
    onSelect?: (item: T) => void;
    onSelectRestriction?: (item: T) => boolean;
    emptyPage?: JSX.Element;
    extraCallbacks?: CB;
    hide?: boolean;
    navigate?: (item: T) => void;
    header?: React.ReactNode;
    headerSize?: number;
}

export function StandardList<T, CB = EmptyObject>(
    props: React.PropsWithChildren<StandardListBrowse<T, CB>>
): JSX.Element | null {
    const scrollingContainerRef = useRef<HTMLDivElement>(null);
    const toggleSet = useToggleSet<T>([]);
    const scrollStatus = useScrollStatus(scrollingContainerRef, true);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const loadingRef = useRef<boolean>(false);
    const reloadRef = useRef<() => void>(doNothing);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const titlePlural = props.titlePlural ?? (props.title.endsWith("s") ? props.title + "es" : props.title + "s");
    const isMainContainer = props.embedded === false || props.embedded === undefined;
    const isInDialog = props.embedded === "dialog" || props.embedded === true;
    const isInline = !isMainContainer && !isInDialog;
    const extraCallbacks = useMemo(() => (props.extraCallbacks ?? {}) as CB, [props.extraCallbacks]);
    const callbacks = useMemo((): StandardCallbacks<T> & CB => ({
        dispatch,
        navigate,
        reload: () => reloadRef.current(),
        onSelect: props.onSelect,
        onSelectRestriction: props.onSelectRestriction,
        embedded: !isMainContainer,
        commandLoading,
        invokeCommand,
        ...extraCallbacks
    }), [props.onSelect, dispatch, navigate, props.embedded, commandLoading, invokeCommand, extraCallbacks]);

    const allOperations = useMemo(() => {
        const ops: Operation<T, StandardCallbacks<T> & CB>[] = [...props.operations];
        ops.push({
            text: "Use",
            primary: true,
            canAppearInLocation: loc => loc !== "SIDEBAR",
            enabled: (selected, cb) => cb.onSelect !== undefined && selected.length === 1,
            onClick: (selected, cb) => {
                cb.onSelect?.(selected[0]);
            }
        });
        return ops;
    }, [props.operations]);

    const pageRenderer = useCallback<PageRenderer<T>>(items => {
        return <>
            <List childPadding={"8px"} bordered={false}>
                {items.length > 0 ? null : props.emptyPage ? props.emptyPage :
                    <>
                        No {titlePlural.toLowerCase()} available.
                    </>
                }

                {items.map((it, idx) =>
                    <ItemRow
                        key={idx}
                        browseType={isMainContainer ? BrowseType.MainContent : BrowseType.Embedded}
                        renderer={props.renderer} callbacks={callbacks} operations={allOperations}
                        item={it} itemTitle={props.title} itemTitlePlural={titlePlural} toggleSet={toggleSet}
                        navigate={props.navigate}
                    />
                )}
            </List>
        </>
    }, [toggleSet, props.renderer, callbacks, allOperations, props.title, titlePlural]);

    const onReload = useCallback(() => {
        toggleSet.uncheckAll();
    }, []);

    const main = useMemo(() =>
        <StandardBrowse generateCall={props.generateCall} pageRenderer={pageRenderer}
            reloadRef={reloadRef} loadingRef={loadingRef}
            hide={props.hide} setRefreshFunction={isMainContainer} onReload={onReload}
            preloadedResources={props.preloadedResources} />,
        [props.generateCall, pageRenderer, reloadRef, loadingRef, props.hide, props.preloadedResources]
    );

    if (isMainContainer) {
        useTitle(titlePlural);
        useLoading(commandLoading || loadingRef.current);
    }


    if (isInDialog) {
        return <Box divRef={scrollingContainerRef}>
            <StickyBox shadow={!scrollStatus.isAtTheTop} normalMarginX="20px">
                <Operations selected={toggleSet.checked.items} location="TOPBAR"
                    entityNameSingular={props.title} entityNamePlural={titlePlural}
                    extra={callbacks} operations={allOperations} />
            </StickyBox>
            {main}
        </Box>;
    } else if (isInline) {
        return <>
            <Operations selected={toggleSet.checked.items} location={"TOPBAR"}
                entityNameSingular={props.title} entityNamePlural={titlePlural}
                extra={callbacks} operations={allOperations} />
            {main}
        </>;
    } else {
        return <MainContainer
            header={props.header}
            headerSize={props.headerSize}
            main={<>
                <Operations selected={toggleSet.checked.items} location={"TOPBAR"}
                    entityNameSingular={props.title} entityNamePlural={titlePlural}
                    extra={callbacks} operations={allOperations} />
                {main}
            </>}
        />;
    }
}
