# Orders (US-002)

The daily pipeline reads the orders of the day, keeps the valid ones and adds their VAT.

## Cleaning

An order without an order_id, or with a negative amount, is dropped. An order of 0.00 is kept: a free
sample is still an order.

| order_id | customer_id | amount | order_date | kept? |
|---|---|---|---|---|
| A1 | C1 | 5.00 | 2026-10-01 | yes |
| A2 | C2 | -3.00 | 2026-10-01 | no: negative |
| A3 | C1 | 0.00 | 2026-10-01 | yes: a free sample |

## VAT

The VAT is 20 % of the amount: 12.50 gives 2.50, 0.00 gives 0.00.
