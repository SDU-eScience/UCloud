import React from 'react';
import './Cards.scss';
import {Col} from 'react-bootstrap';


export const Card = ({xs, sm, children}) => (
    <Col sm={sm} xs={xs}>
        <div className="card">
            {children}
        </div>
    </Col>
);

export const CardHeading = ({children}) => (
    <div className="card-heading bg-pink-500">
        {children}
    </div>
);