(ns salesmgmt.governor
  "SalesMarketingManagementGovernor — the independent safety/
  traceability layer for the ISCO-08 1221 community sales-management
  actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors
  section). Modeled on cloud-itonami-isco-4311's bookkeeping.governor.
  Sales-management twist: the discount ceiling and the price floor are
  REGISTERED numbers checked deterministically — an authority table is
  not a negotiating position, and 'the customer is important' does not
  move arithmetic.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. product basis     — a discount must cite a REGISTERED product
                           belonging to this client (no invented
                           offerings).
    4. discount ceiling  — the proposed rate (whole percent) must be <=
                           the product's registered :discount-ceiling.
    5. price floor       — the proposed final price must be >= the
                           product's registered :price-floor (margin
                           protection is arithmetic, not sentiment).
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :publish-campaign (external publication).
    7. low confidence (< `confidence-floor`)."
  (:require [salesmgmt.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record prod]
  (let [{:keys [op product-id rate final-price]} proposal
        discount? (= :approve-discount op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and discount? (nil? product-id))
      (conj {:rule :no-product :detail "値引きは product の引用が必須（商品の捏造禁止）"})

      (and discount? product-id (nil? prod))
      (conj {:rule :unknown-product :detail (str "未登録 product: " product-id)})

      (and discount? prod (not= (:client-id prod) (:client-id request)))
      (conj {:rule :product-wrong-client :detail "product が別 client のもの"})

      (and discount? prod (integer? rate)
           (> rate (:discount-ceiling prod)))
      (conj {:rule :discount-over-ceiling
             :detail (str "値引率 " rate "% > 権限上限 " (:discount-ceiling prod)
                          "%（権限表は交渉材料ではない）")})

      (and discount? prod (integer? final-price)
           (< final-price (:price-floor prod)))
      (conj {:rule :below-price-floor
             :detail (str "最終価格 " final-price " < フロア " (:price-floor prod)
                          "（マージン保護は算術であって情ではない）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `salesmgmt.store/Store`. Pure — never mutates
  the store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        prod (some->> (:product-id proposal) (store/product store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record prod)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :publish-campaign (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
