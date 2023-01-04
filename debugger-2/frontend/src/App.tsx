import {useState} from 'react';
import './App.css';
import {Header} from './Header/Header';
import {MainContent} from './MainContent/MainContent';
import {Sidebar} from './Sidebar/Sidebar';

const _services: ServiceNode[] = [];

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

function addServiceFromRootNode(fullServicePath: string, root: ServiceNode[], doLog = false) {
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
            const newRoot = root.find(it => it.serviceName === p)?.children;
            if (!newRoot) return;
            _root = newRoot;
        }

    }
}



addServiceFromRootNode("UCloud/Core", _services);
addServiceFromRootNode("K8/Server", _services);
addServiceFromRootNode("Slurm/Server", _services);
addServiceFromRootNode("Slurm/User/1000", _services, true);
addServiceFromRootNode("Slurm/User/1100", _services, true);
addServiceFromRootNode("Slurm/User/1140", _services, true);
addServiceFromRootNode("Slurm/User/11141", _services, true);
addServiceFromRootNode("Slurm/User/15121", _services, true);

function App(): JSX.Element {
    const [activeService, setActiveService] = useState("");

    return <>
        <Header />
        <div className="flex">
            <Sidebar>
                <ServiceList services={_services} setActiveService={setActiveService} activeService={activeService} depth={0} />
            </Sidebar>
            <MainContent activeService={activeService} filters="todo" levels="todo" query="todo" />
        </div>
    </>;
}

function ServiceList({services, activeService, setActiveService, depth}: {activeService: string, services: ServiceNode[], setActiveService: (s: string) => void; depth: number;}): JSX.Element {
    if (services.length === 0) return <div />;
    return <div className="mb-12px" style={{marginBottom: "12px"}}>
        {services.map(it => {
            const isActive = it.absolutePath === activeService;

            if (isLeaf(it)) {
                return <div key={it.absolutePath} data-active={isActive} onClick={() => setActiveService(it.absolutePath)}>{it.serviceName}</div>
            } else {
                const oneChild = hasOneChild(it);
                return <div data-onechild={oneChild} key={it.absolutePath}>
                    <div data-active={isActive}> {it.serviceName}/</div>
                    <div data-onechildindent={!oneChild}>
                        <ServiceList services={it.children} activeService={activeService} setActiveService={setActiveService} depth={depth + 1} />
                    </div>
                </div>
            }
        })}
    </div >
}

export default App;
