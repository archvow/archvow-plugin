Feature: clean the orders
  Rule: clean_orders.INV1
    Scenario: an order of 0.00 is kept
      Given the orders A1 (5.00) and A3 (0.00, a free sample)
      When clean_orders runs
      Then A1 and A3 are both kept
