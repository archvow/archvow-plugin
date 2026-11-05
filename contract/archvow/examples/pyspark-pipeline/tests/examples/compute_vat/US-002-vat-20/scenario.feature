Feature: compute the VAT
  Rule: compute_vat.INV1
    Scenario: the VAT is 20 % of the amount
      Given the orders A1 (12.50) and A3 (0.00)
      When compute_vat runs
      Then A1 has a vat of 2.50 and A3 a vat of 0.00
