import {useState, useSyncExternalStore, useMemo} from 'react';
import './App.css';
import {Filters} from './Header/Filters';
import {Header} from './Header/Header';
import {Levels} from './Header/Levels';
import {SearchBar} from './Header/SearchBar';
import {MainContent} from './MainContent/MainContent';
import {Sidebar} from './Sidebar/Sidebar';
import {serviceStore} from './WebSockets/Socket';


interface ServiceNode {
    serviceName: string;
    absolutePath: string;
    children: ServiceNode[];
}

function isLeaf(servicenode: ServiceNode): boolean {
    return servicenode.children.length === 0;
}

function hasOneChild(serviceNode: ServiceNode): boolean {
    return serviceNode.children.length === 1;
}

function onlyHasSingularChildren(root: ServiceNode): boolean {
    let _r = root;
    while (hasOneChild(_r) && !isLeaf((_r))) {
        _r = _r.children[0];
    }
    return isLeaf(_r);
}

function addServiceFromRootNode(fullServicePath: string, root: ServiceNode[]) {
    const splitPath = fullServicePath.split("/");
    let _root = root;
    for (const p of splitPath) {
        const _newRoot = _root.find(it => it.serviceName === p);
        if (_newRoot) {
            _root = _newRoot.children;
        } else {
            _root.push({absolutePath: fullServicePath, children: [], serviceName: p});
            const newRoot = _root.find(it => it.serviceName === p)?.children;
            if (!newRoot) return;
            _root = newRoot;
        }
    }
}

function App(): JSX.Element {
    const [activeService, setActiveService] = useState("");
    const [level, setLevel] = useState<string>("");
    const services = useSyncExternalStore(subscription => serviceStore.subscribe(subscription), () => serviceStore.getSnapshot());
    const serviceNodes = useMemo(() => {
        const root: ServiceNode[] = [];
        for (const service of services) {
            addServiceFromRootNode(service, root)
        }
        return root;
    }, [services]);

    console.log(serviceNodes);

    return <>
        <Header>
            <SearchBar />
            <Filters filters={[]} setFilters={() => undefined} />
            <Levels level={level} setLevel={setLevel} />
        </Header>
        <div className="flex">
            <Sidebar>
                <ServiceList services={serviceNodes} setActiveService={setActiveService} activeService={activeService} depth={0} />
            </Sidebar>
            <MainContent activeService={activeService} filters="todo" levels="todo" query="todo" />
        </div>
    </>;
}

function ServiceList({services, activeService, setActiveService, depth}: {activeService: string, services: ServiceNode[], setActiveService: (s: string) => void; depth: number;}): JSX.Element {
    if (services.length === 0) return <div />;
    return <div className="mb-12px">
        {services.map(it => {
            const isActive = it.absolutePath === activeService || activeService.startsWith(it.serviceName);

            if (isLeaf(it)) {
                return <div key={it.absolutePath}>
                    <span className="leaf" data-active={isActive} onClick={() => setActiveService(it.absolutePath)}>
                        {it.serviceName}
                    </span>
                </div>
            } else {
                const oneChild = hasOneChild(it);
                const singularChildren = onlyHasSingularChildren(it);
                return <div data-onechild={oneChild} data-singular={singularChildren} onClick={singularChildren ? () => setActiveService(it.absolutePath) : undefined} key={it.absolutePath}>
                    <div data-active={isActive}> {it.serviceName}/</div>
                    <div data-omitindent={!oneChild}>
                        <ServiceList
                            services={it.children}
                            activeService={activeService}
                            setActiveService={setActiveService}
                            depth={depth + 1}
                        />
                    </div>
                </div>
            }
        })}
    </div >
}

export default App;
