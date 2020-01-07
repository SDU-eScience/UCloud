# RPC and Event Stream Test

We will have two services running: ping and pong. Ping will act as the client while pong will act as the server. 

All calls in the test will be public to get rid of the auth service.

## HTTP Test

The ping service will be sending out a random number (1-10) of concurrent requests to the pong service every 10 seconds.
The ping service will keep track of the expected number of answers and actual answers. The service will also count any
non 2XX responses it gets.

## WS Test

Same as HTTP. We will be using two calls: one for normal request-response and another for subscriptions.

## Event Stream Test

Similar to the HTTP test. The pong service will send out a random number of messages every 30 seconds. The ping service
will attempt consumption of these messages. Both services keep track of how many messages they have sent/processed.
We will be using two streams: one for immediate consumption another for batched consumption.
