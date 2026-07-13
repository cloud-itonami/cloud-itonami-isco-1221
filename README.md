# cloud-itonami-isco-1221

**Community Sales & Marketing Management** — the ISCO-08 1221 (Sales
and Marketing Managers) actor, a **wave-1 (design & governance)**
occupation per ADR-2607121000.

**Maturity: `:implemented`** — SalesMarketingManagementAdvisor ⊣
SalesMarketingManagementGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
13 tests / 27 assertions green.

The sales-management HARD invariants — both registered number tables,
checked deterministically:

1. **Discount ceiling** — the proposed rate must not exceed the
   product's registered authority ceiling. The authority table is not
   a negotiating position; "the customer is important" does not move
   arithmetic.
2. **Price floor** — the final price must not fall below the
   registered floor. Margin protection is arithmetic, not sentiment.

Also HARD: invented/foreign products, unregistered organization,
non-`:propose` effect. Escalations (always human sign-off):
`:publish-campaign` (external publication), low confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
