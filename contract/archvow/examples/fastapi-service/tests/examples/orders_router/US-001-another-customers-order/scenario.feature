Feature: read an order
  Rule: orders_router.INV1
    Scenario: another customer's order is not found
      Given ann has an order of 5.00
      When bob reads it
      Then the answer is 404 "Order not found"
