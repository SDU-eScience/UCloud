import * as React from "react";
import * as Col from "react-bootstrap/lib/Col";

interface Card { xs: number, sm: number, children: React.ReactNode }
export const Card = ({ xs, sm, children }: Card) => (
    <Col sm={sm} xs={xs}>
        <div className="card">
            {children}
        </div>
    </Col>
);

interface CardHeading { children: React.ReactNode }
export const CardHeading = ({ children }: CardHeading) => (
    <div className="card-heading bg-pink-500">
        {children}
    </div>
);