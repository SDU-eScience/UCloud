import {useState, useEffect} from 'react';
import './App.css';
import {Header} from './Header/Header';
import {MainContent} from './MainContent/MainContent';
import {Sidebar} from './Sidebar/Sidebar';

const _services = {};

function addService(serviceComponents: string[], serviceList: Record<string, any>) {
    let cpy = serviceList;
    for (const comp of serviceComponents) {
        if (!cpy[comp]) {
            cpy[comp] = {};
        }
        cpy = cpy[comp];
    }
}

addService("UCloud/Core".split("/")!, _services); 
addService("K8/Server".split("/")!, _services); 
addService("Slurm/Server".split("/")!, _services); 
addService("Slurm/User/1000".split("/")!, _services); 
addService("Slurm/User/1100".split("/")!, _services); 
addService("Slurm/User/1140".split("/")!, _services); 
addService("Slurm/User/11141".split("/")!, _services); 
addService("Slurm/User/15121".split("/")!, _services); 

function App(): JSX.Element {
    const [activeService, setActiveService] = useState("");

    useEffect(() => { }, []);

    return <>
        <Header />
        <div className="flex">
            <Sidebar>
                <ServiceList parentPath="" services={_services} setActiveService={setActiveService} activeService={activeService} />
            </Sidebar>
            <MainContent activeService={activeService} filters="todo" levels="todo" query="todo" />
        </div>
    </>;
}



function ServiceList({services, activeService, setActiveService, parentPath}: {parentPath: string, activeService: string, services: Record<string, any>, setActiveService: (arg: string) => void}): JSX.Element {
    const serviceList = Object.entries(services);
    
    return <div>
        {serviceList.map(([parent, children]) => <>
            <div onClick={() => serviceList.length !== 0 ? setActiveService(parentPath + "/" + parent) : null}>{parent}</div>
            <div style={{marginLeft: "10px"}}> <ServiceList services={children} parentPath={parentPath + "/" + parent} activeService={activeService} setActiveService={setActiveService} /></div>
        </>)}
    </div>
}

export default App;
