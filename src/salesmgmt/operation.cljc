(ns salesmgmt.operation
  "OperationActor -- one sales-and-marketing-management operation = one
  supervised actor run, expressed as a langgraph-clj StateGraph. This is
  the OS-facing entry point for this vertical (the shape
  `scripts/itonami-os-maturity-tick.cljs` requires: `phase.cljc` op
  sets + `operation/build` + a `langgraph.graph` actor + `store/seed-db`
  + a governor).

  It does NOT restate the graph. `salesmgmt.actor/build-graph` owns the
  topology; `build` here injects `salesmgmt.phase/gate` into its
  `:decide` seam. One graph, one place to change it -- restating the
  nodes here is how the two copies drift apart, which is the defect
  `cloud-itonami/ma`'s ADR 0001 recorded for this business.

    :intake -> :advise -> :govern -> :decide -+-> :commit
                                              +-> :request-approval (interrupt)
                                              +-> :hold

  The advisor is sealed into `:advise` and only ever proposes; the
  SalesMarketingManagementGovernor censors independently at `:govern`
  (the registered discount ceiling and price floor are arithmetic, not
  a negotiating position); the rollout phase can then only add caution
  at `:decide`. No unbounded inner loop -- each operation is auditable
  and checkpointed, so an interrupted run resumes after human sign-off."
  (:require [langgraph.graph :as g]
            [salesmgmt.actor :as actor]
            [salesmgmt.phase :as phase]))

(defn build
  "Compile an OperationActor bound to `store` (any
  `salesmgmt.store/Store`).

  opts:
    :advisor      -- a `salesmgmt.advisor/Advisor` (default: mock)
    :checkpointer -- langgraph checkpointer (default: in-mem)
    :phase        -- rollout phase 0-3 (default: the request context's
                     `:phase`, else `salesmgmt.phase/default-phase`)

  Absent keys are dropped rather than passed as nil, so
  `actor/build-graph`'s own `:or` defaults still apply."
  [store & [{:keys [advisor checkpointer phase]}]]
  (actor/build-graph
   (cond-> {:store store
            :phase-gate (fn [request context _verdict base]
                          (phase/gate (or phase
                                          (:phase context)
                                          phase/default-phase)
                                      request
                                      base))}
     advisor      (assoc :advisor advisor)
     checkpointer (assoc :checkpointer checkpointer))))

(defn run-operation!
  "Run one operation to completion or to the approval interrupt.
  `thread-id` scopes checkpointing so the run can resume."
  [graph request context thread-id]
  (g/run* graph {:request request :context context} {:thread-id thread-id}))

(defn approve!
  "Human-in-the-loop resume: resuming the thread IS the approval, so the
  interrupted `:request-approval` node advances to `:commit`."
  [graph thread-id]
  (g/run* graph nil {:thread-id thread-id :resume? true}))
