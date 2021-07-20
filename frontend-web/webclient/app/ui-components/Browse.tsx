import * as React from "react";
import {DependencyList, EventHandler, MouseEvent, useCallback, useEffect, useMemo, useRef, useState} from "react";
import {PageV2} from "UCloud";
import {emptyPageV2, pageV2Of} from "DefaultObjects";
import {InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import * as Pagination from "Pagination";
import {PageRenderer} from "Pagination/PaginationV2";
import {ToggleSetHook, useToggleSet} from "Utilities/ToggleSet";
import {Operation, Operations} from "ui-components/Operation";
import {NamingField} from "UtilityComponents";
import {ListRow, ListStatContainer} from "ui-components/List";
import {doNothing, EmptyObject} from "UtilityFunctions";
import {Dispatch} from "redux";
import * as H from "history";
import {useScrollStatus} from "Utilities/ScrollStatus";
import {useDispatch} from "react-redux";
import {useHistory} from "react-router";
import {Box, List} from "ui-components/index";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {StickyBox} from "ui-components/StickyBox";
import MainContainer from "MainContainer/MainContainer";

interface BrowseProps<T> {
    preloadedResources?: T[];
    generateCall: (next?: string) => APICallParameters;
    pageRenderer: PageRenderer<T>;

    loadingRef?: React.MutableRefObject<boolean>;
    reloadRef?: React.MutableRefObject<() => void>;
    onReload?: () => void;
    setRefreshFunction?: boolean;
    hide?: boolean;
    toggleSet?: ToggleSetHook<T>;
}

export function StandardBrowse<T>(props: React.PropsWithChildren<BrowseProps<T>>): JSX.Element | null {
    const hasPreloadedResources = !!props.preloadedResources;
    const [remoteResources, fetchResources] = useCloudAPI<PageV2<T>>({noop: true}, emptyPageV2);
    const [infScroll, setInfScroll] = useState(0);
    const resources = useMemo(() => {
            return hasPreloadedResources ? pageV2Of(...props.preloadedResources!) : remoteResources.data;
        },

        [props.preloadedResources, remoteResources.data]
    );
    const isLoading = hasPreloadedResources ? false : remoteResources.loading;
    if (props.loadingRef) props.loadingRef.current = isLoading;

    const reload = useCallback(() => {
        setInfScroll(prev => prev + 1);
        if (hasPreloadedResources) return;
        fetchResources(props.generateCall());
        if (props.onReload) props.onReload();
    }, [props.generateCall, props.onReload, hasPreloadedResources]);
    if (props.reloadRef) props.reloadRef.current = reload;

    const loadMore = useCallback(() => {
        if (hasPreloadedResources) return;
        if (resources.next) {
            fetchResources(props.generateCall(resources.next));
        }
    }, [props.generateCall, hasPreloadedResources, resources.next]);

    if (props.toggleSet) props.toggleSet.allItems.current = resources.items;

    useEffect(() => reload(), [reload]);

    if (props.setRefreshFunction !== false) {
        useRefreshFunction(reload);
    }

    if (props.hide === true) return null;

    return <Pagination.ListV2 page={resources} pageRenderer={props.pageRenderer} loading={isLoading}
                              onLoadMore={loadMore} customEmptyPage={props.pageRenderer([])}
                              infiniteScrollGeneration={infScroll} dataIsStatic={hasPreloadedResources}/>;
}

export interface ItemRenderer<T> {
    Icon?: React.FunctionComponent<{ resource?: T, size: string; }>;
    MainTitle?: React.FunctionComponent<{ resource?: T; }>;
    Stats?: React.FunctionComponent<{ resource?: T }>;
    ImportantStats?: React.FunctionComponent<{ resource?: T }>;
}

interface ItemRowProps<T, CB> {
    item?: T;
    renderer: ItemRenderer<T>;

    toggleSet: ToggleSetHook<T>;
    navigate?: (item: T) => void;

    operations: Operation<T, CB>[];
    callbacks: CB;
    itemTitle: string;
    itemTitlePlural?: string;

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
        onContextMenu={onContextMenu}
        icon={renderer.Icon ? <renderer.Icon resource={props.item} size={"36px"}/> : null}
        left={
            props.item && props.renaming?.isRenaming(props.item) === true ?
                <NamingField onCancel={props.renaming?.onRenameCancel ?? doNothing} confirmText={"Rename"}
                             inputRef={renameInputRef} onSubmit={onRename}
                             defaultValue={renameValue}/> :
                renderer.MainTitle ? <renderer.MainTitle resource={props.item}/> : null
        }
        isSelected={props.item && props.toggleSet.checked.has(props.item)}
        select={() => props.item ? props.toggleSet.toggle(props.item) : 0}
        navigate={props.navigate && props.item ? () => props.navigate?.(props.item!) : undefined}
        leftSub={
            <ListStatContainer>
                {renderer.Stats ? <renderer.Stats resource={props.item}/> : null}
            </ListStatContainer>
        }
        right={
            <>
                {renderer.ImportantStats ? <renderer.ImportantStats resource={props.item}/> : null}
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
                    /> : null}
            </>
        }
    />;
}

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
    }), [onRename, isRenaming, setRenaming, onRenameCancel, memoNameExtractor, memoOnRename]);
}

export interface StandardCallbacks<T> {
    commandLoading: boolean;
    invokeCommand: InvokeCommand;
    reload: () => void;
    onSelect?: (resource: T) => void;
    embedded: boolean;
    dispatch: Dispatch;
    history: H.History;
}

interface StandardListBrowse<T, CB> {
    preloadedResources?: T[];
    generateCall: (next?: string) => APICallParameters;
    renderer: ItemRenderer<T>;
    operations: Operation<T, StandardCallbacks<T> & CB>[];
    title: string;
    titlePlural?: string;
    embedded?: boolean | "inline" | "dialog";
    onSelect?: (item: T) => void;
    emptyPage?: JSX.Element;
    sidebarPage?: SidebarPages;
    extraCallbacks?: CB;
    hide?: boolean;
    navigate?: (item: T) => void;
}

export function StandardList<T, CB = EmptyObject>(
    props: React.PropsWithChildren<StandardListBrowse<T, CB>>
): JSX.Element | null {
    const scrollingContainerRef = useRef<HTMLDivElement>(null);
    const toggleSet = useToggleSet<T>([]);
    const scrollStatus = useScrollStatus(scrollingContainerRef, true);
    const dispatch = useDispatch();
    const history = useHistory();
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
        history,
        reload: () => reloadRef.current(),
        onSelect: props.onSelect,
        embedded: !isMainContainer,
        commandLoading,
        invokeCommand,
        ...extraCallbacks
    }), [props.onSelect, dispatch, history, props.embedded, commandLoading, invokeCommand, extraCallbacks]);

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
        return <List childPadding={"8px"} bordered={false}>
            {items.length > 0 ? null : props.emptyPage ? props.emptyPage :
                <>
                    No {titlePlural.toLowerCase()} available.
                </>
            }
            {items.map((it, idx) =>
                <ItemRow
                    key={idx}
                    renderer={props.renderer} callbacks={callbacks} operations={allOperations}
                    item={it} itemTitle={props.title} itemTitlePlural={titlePlural} toggleSet={toggleSet}
                    navigate={props.navigate}
                />
            )}
        </List>
    }, [toggleSet, props.renderer, callbacks, allOperations, props.title, titlePlural]);

    const main = useMemo(() =>
        <StandardBrowse generateCall={props.generateCall} pageRenderer={pageRenderer}
                        reloadRef={reloadRef} loadingRef={loadingRef}
                        hide={props.hide} setRefreshFunction={isMainContainer}
                        preloadedResources={props.preloadedResources}/>,
        [props.generateCall, pageRenderer, reloadRef, loadingRef, props.hide, props.preloadedResources]);
    if (isMainContainer) {
        useTitle(titlePlural);
        useLoading(commandLoading || loadingRef.current);
        useSidebarPage(props.sidebarPage ?? SidebarPages.None);
    }


    if (isInDialog) {
        return <Box ref={scrollingContainerRef}>
            <StickyBox shadow={!scrollStatus.isAtTheTop} normalMarginX={"20px"}>
                <Operations selected={toggleSet.checked.items} location={"TOPBAR"}
                            entityNameSingular={props.title} entityNamePlural={titlePlural}
                            extra={callbacks} operations={allOperations}/>
            </StickyBox>
            {main}
        </Box>;
    } else if (isInline) {
        return <>
            <Operations selected={toggleSet.checked.items} location={"TOPBAR"}
                        entityNameSingular={props.title} entityNamePlural={titlePlural}
                        extra={callbacks} operations={allOperations}/>
            {main}
        </>;
    } else {
        return <MainContainer
            main={main}
            sidebar={
                <Operations selected={toggleSet.checked.items} location={"SIDEBAR"}
                            entityNameSingular={props.title} entityNamePlural={titlePlural}
                            extra={callbacks} operations={allOperations}/>
            }
        />;
    }
}
