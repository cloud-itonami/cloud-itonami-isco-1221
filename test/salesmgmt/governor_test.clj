(ns salesmgmt.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [salesmgmt.store :as store]
            [salesmgmt.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-product! st {:product-id "P-1" :client-id "client-1"
                                 :name "widget" :list-price 10000
                                 :price-floor 7000 :discount-ceiling 20})
    st))

(defn- discount [rate final-price]
  {:op :approve-discount :effect :propose :product-id "P-1"
   :rate rate :final-price final-price :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-ceiling-and-above-floor
  (let [st (fresh-store)
        v (governor/check req {} (discount 15 8500) st)]
    (is (:ok? v))))

(deftest ok-at-exact-limits
  (testing "20% at exactly the floor is within authority"
    (let [st (fresh-store)
          v (governor/check req {} (discount 20 8000) st)]
      (is (:ok? v)))))

(deftest hard-over-ceiling
  (testing "the authority table is not a negotiating position"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (discount 21 7900)
                                          :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :discount-over-ceiling (:rule %)) (:violations v))))))

(deftest hard-below-price-floor
  (testing "margin protection is arithmetic, not sentiment"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (discount 20 6999)
                                          :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :below-price-floor (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (discount 10 9000) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (discount 10 9000)
                                        :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-invented-product
  (let [st (fresh-store)
        v (governor/check req {} (assoc (discount 10 9000)
                                        :product-id "P-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-product (:rule %)) (:violations v)))))

(deftest hard-on-foreign-product
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (discount 10 9000) st)]
      (is (:hard? v))
      (is (some #(= :product-wrong-client (:rule %)) (:violations v))))))

(deftest escalates-campaign-publication
  (let [st (fresh-store)
        v (governor/check req {} {:op :publish-campaign :effect :propose
                                  :confidence 0.9 :stake :medium} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} {:op :draft-campaign :effect :propose
                                  :confidence 0.3 :stake :low} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
