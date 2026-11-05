Feature: pay an invoice
  Scenario: a sent invoice is marked paid
    Given the sent invoice F-7 of Ada for 9.90
    When the finance team runs invoices pay F-7
    Then it prints "F-7 Ada 9.90 paid" and F-7 is saved paid
