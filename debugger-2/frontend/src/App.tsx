import {useState, useEffect} from 'react';
import './App.css';
import {Header} from './Header/Header';
import {MainContent} from './MainContent/MainContent';
import {Sidebar} from './Sidebar/Sidebar';


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

function addServiceFromRootNode(fullServicePath: string, root: ServiceNode[]) {
    const splitPath = fullServicePath.split("/");
    let _root = root;
    let absolutePath = "";
    for (const p of splitPath) {
        absolutePath += "/" + p;
        const _newRoot = _root.find(it => it.serviceName === p);
        if (_newRoot) {
            _root = _newRoot.children;
        } else {
            _root.push({absolutePath, children: [], serviceName: p});
            const newRoot = _root.find(it => it.serviceName === p)?.children;
            if (!newRoot) return;
            _root = newRoot;
        }
    }
}

function App(): JSX.Element {
    const [activeService, setActiveService] = useState("");
    const [services, setServices] = useState<ServiceNode[]>([]);

    useEffect(() => {
        const s: ServiceNode[] = [];
        addServiceFromRootNode("UCloud/Core", s);
        addServiceFromRootNode("K8/Server", s);
        addServiceFromRootNode("Slurm/Server", s);
        addServiceFromRootNode("Slurm/User/1000", s);
        addServiceFromRootNode("Slurm/User/1100", s);
        addServiceFromRootNode("Slurm/User/1140", s);
        addServiceFromRootNode("Slurm/User/11141", s);
        addServiceFromRootNode("Slurm/User/15121", s);
        setServices(s);
    }, [])

    return <>
        <Header />
        <div className="flex">
            <Sidebar>
                <ServiceList services={services} setActiveService={setActiveService} activeService={activeService} depth={0} />
            </Sidebar>
            <MainContent activeService={activeService} filters="todo" levels="todo" query="todo" />
        </div>
    </>;
}

function ServiceList({services, activeService, setActiveService, depth}: {activeService: string, services: ServiceNode[], setActiveService: (s: string) => void; depth: number;}): JSX.Element {
    if (services.length === 0) return <div />;
    return <div className="mb-12px">
        {services.map(it => {
            const isActive = it.absolutePath === activeService || activeService.startsWith(it.absolutePath);

            if (isLeaf(it)) {
                return <div><span key={it.absolutePath} className="leaf" data-active={isActive} onClick={() => setActiveService(it.absolutePath)}>{it.serviceName}</span></div>
            } else {
                const oneChild = hasOneChild(it);
                return <div data-onechild={oneChild} key={it.absolutePath}>
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
