(ns salesmgmt.operation-test
  "End-to-end through the real compiled graph and a real store, not
  through a hand-built verdict. The phase gate is only interesting if
  it changes what reaches the SSoT, so every case below asserts the
  store contents as well as the disposition."
  (:require [clojure.test :refer [deftest is testing]]
            [salesmgmt.operation :as operation]
            [salesmgmt.phase :as phase]
            [salesmgmt.store :as store]))

(deftest seed-db-is-deterministic-and-carries-refusable-cases
  (let [a (store/seed-db)
        b (store/seed-db)]
    (is (= (store/client a "client-1") (store/client b "client-1")))
    (is (some? (store/product a "P-1")))
    (testing "the seed can exercise the governor's arithmetic rules"
      (is (= 5 (:discount-ceiling (store/product a "P-2")))
          "a tight ceiling must exist so an over-ceiling rate is expressible")
      (is (= "client-2" (:client-id (store/product a "P-3")))
          "a product of ANOTHER client must exist to refuse cross-client cites"))
    (is (empty? (store/ledger a)) "a fresh seed has committed nothing")))

(deftest phase-3-commits-a-governor-clean-draft
  (let [st (store/seed-db)
        graph (operation/build st {:phase 3})
        result (operation/run-operation!
                graph
                {:client-id "client-1" :op :draft-campaign :stake :low}
                {} "op-commit")]
    (is (= :done (:status result)))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest phase-0-holds-the-very-same-draft
  (testing "identical request and store -- only the phase differs, so a
            hold here is the phase gate and nothing else"
    (let [st (store/seed-db)
          graph (operation/build st {:phase 0})
          result (operation/run-operation!
                  graph
                  {:client-id "client-1" :op :draft-campaign :stake :low}
                  {} "op-hold")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "client-1"))
          "nothing may reach the SSoT at phase 0")
      (is (= :phase-disabled
             (->> (get-in result [:state :audit])
                  (keep :phase-reason)
                  first))
          "and it must be held for the reason the gate names"))))

(deftest publish-campaign-escalates-at-phase-3-and-commits-only-after-approval
  (let [st (store/seed-db)
        graph (operation/build st {:phase 3})
        interrupted (operation/run-operation!
                     graph
                     {:client-id "client-1" :op :publish-campaign :stake :medium}
                     {} "op-approve")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1"))
        "publication must not commit before a human resumes it")
    (let [resumed (operation/approve! graph "op-approve")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))

(deftest an-over-ceiling-discount-is-held-even-at-the-most-permissive-phase
  (testing "the governor's arithmetic HARD rule survives the phase gate"
    (let [st (store/seed-db)
          graph (operation/build st {:phase 3})
          result (operation/run-operation!
                  graph
                  {:client-id "client-1" :op :approve-discount :stake :low
                   :product-id "P-2" :rate 30 :final-price 4800}
                  {} "op-ceiling")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "client-1"))))))

(deftest a-below-floor-price-is-held-even-at-the-most-permissive-phase
  (let [st (store/seed-db)
        graph (operation/build st {:phase 3})
        result (operation/run-operation!
                graph
                {:client-id "client-1" :op :approve-discount :stake :low
                 :product-id "P-1" :rate 15 :final-price 100}
                {} "op-floor")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest a-discount-citing-another-clients-product-is-held
  (let [st (store/seed-db)
        graph (operation/build st {:phase 3})
        result (operation/run-operation!
                graph
                {:client-id "client-1" :op :approve-discount :stake :low
                 :product-id "P-3" :rate 5 :final-price 1100}
                {} "op-cross-client")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest the-phase-comes-from-the-request-context-when-build-is-not-pinned
  (testing "an OS caller that injects :phase per run gets that phase"
    (let [st (store/seed-db)
          graph (operation/build st)
          result (operation/run-operation!
                  graph
                  {:client-id "client-1" :op :draft-campaign :stake :low}
                  {:phase 0} "op-ctx")]
      (is (= :hold (:disposition (:state result)))
          "context :phase 0 must hold what default-phase would commit")))
  (is (= 3 phase/default-phase)))
