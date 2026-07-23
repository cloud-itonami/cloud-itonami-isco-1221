(ns salesmgmt.advisor
  "SalesMarketingManagementAdvisor — proposes a sales-management
  operation (approve a discount, draft a campaign, publish a campaign)
  for a registered organization. Swappable mock/llm; the advisor ONLY
  proposes — `salesmgmt.governor` checks the ceiling/floor tables
  independently. Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-discount|:draft-campaign|:publish-campaign
               :effect :propose :product-id str :rate int :final-price int
               :stake kw :confidence n :rationale str}"
  (:require #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as edn])))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake product-id rate final-price] :as request}]
  {:op op
   :effect :propose
   :product-id product-id
   :rate rate
   :final-price final-price
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a sales management advisor. Given a request, propose an
   :op, the :product-id, :rate and :final-price, an honest :confidence
   and a :stake. Never exceed the authority table — the governor checks
   the registered ceiling and floor.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
