Feature: clean the orders
  Scenario: an order with a negative amount is dropped
    Given the orders A1 (5.00) and A2 (-3.00)
    When clean_orders runs
    Then only A1 is kept
