import React from 'react';

export default function SectionContainerCard(props) {
    return (
        <section>
            <div className="container-fluid">
                <div className="card">
                    <div className="card-body">
                        {props.children}
                    </div>
                </div>
            </div>
        </section>);
}