(ns salesmgmt.store
  "SSoT for the ISCO-08 1221 community sales-management actor (itonami
  actor pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client  — a registered organization (:client-id, :name)
    product — a registered offering {:product-id :client-id :name
              :list-price :price-floor :discount-ceiling}
              (prices in smallest currency unit; ceiling in whole
              percent). The floor and ceiling are REGISTERED authority
              limits — a number table, not a negotiating position.
    record  — a committed operating record (approved discount, campaign
              draft, published campaign) — written ONLY via
              commit-record!.
    ledger  — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (product [s product-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-product! [s p])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (product [_ product-id] (get-in @a [:products product-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-product! [s p]
    (swap! a assoc-in [:products (:product-id p)] p) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :products {} :records [] :ledger []}
                                   seed)))))
