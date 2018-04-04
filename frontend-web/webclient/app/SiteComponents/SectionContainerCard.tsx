import * as React from "react";

interface SCCProps { children: React.ReactChildren }
const SectionContainerCard = ({children}:SCCProps) => (
    <section>
        <div className="container-fluid">
            <div className="card">
                <div className="card-body">
                    {children}
                </div>
            </div>
        </div>
    </section>
);

export default SectionContainerCard;