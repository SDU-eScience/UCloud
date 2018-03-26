import React from "react";

export default SectionContainerCard = ({children}) => (
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
