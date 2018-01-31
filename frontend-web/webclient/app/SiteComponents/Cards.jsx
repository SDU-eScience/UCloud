import React from 'react';
import './Cards.scss';
import { Col } from 'react-bootstrap';


function Card(props) {
    return (
        <Col sm={props.sm} xs={props.xs}>
            <div className="card">
                {props.children}
            </div>
        </Col>
    )
}

function CardHeading(props) {
    return (
     <div className="card-heading bg-pink-500">
         {props.children}
    </div>)
}

export { Card, CardHeading }