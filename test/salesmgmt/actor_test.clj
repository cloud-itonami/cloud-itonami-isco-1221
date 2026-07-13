(ns salesmgmt.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [salesmgmt.actor :as actor]
            [salesmgmt.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-product! st {:product-id "P-1" :client-id "client-1"
                                 :name "widget" :list-price 10000
                                 :price-floor 7000 :discount-ceiling 20})
    st))

(deftest commits-an-in-authority-discount
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-discount :stake :low
                 :product-id "P-1" :rate 15 :final-price 8500}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-ceiling-discount
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-discount :stake :low
                 :product-id "P-1" :rate 35 :final-price 6500}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-publishes-campaign-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :publish-campaign :stake :medium}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
