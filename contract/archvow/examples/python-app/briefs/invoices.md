# Invoices (US-001)

The finance team marks an invoice paid from the command line, by its number.

## Paying a sent invoice

`invoices pay F-7` marks the sent invoice F-7 paid, saves it, and prints it on one line: its number,
customer, amount and status.

| before | command | printed | after |
|---|---|---|---|
| F-7, Ada, 9.90, sent | `invoices pay F-7` | `F-7 Ada 9.90 paid` | F-7, Ada, 9.90, paid |
