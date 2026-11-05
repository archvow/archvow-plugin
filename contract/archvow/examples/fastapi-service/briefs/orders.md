# Orders (US-001)

A customer creates orders and reads their own; nobody reads another customer's order.

## Reading an order

A customer reads one of their orders by its id. An order of another customer is answered as if it did
not exist: 404, "Order not found" - the answer says nothing about whose it is.

| order of | read by | answer |
|---|---|---|
| ann | ann | 200, the order |
| ann | bob | 404, {"detail": "Order not found"} |
