(ns salesmgmt.phase
  "Phase 0->3 staged rollout for the ISCO-08 1221 sales-and-marketing
  management actor -- the same rollout seam `cloud-itonami-isic-6612`'s
  `brokerage.phase` establishes, expressed for this repo's three ops.

    Phase 0  read-only          -- no writes, still governor-gated.
    Phase 1  assisted-draft     -- campaign drafting allowed, every
                                   write needs human approval.
    Phase 2  assisted-discount  -- adds discount approval, still
                                   approval.
    Phase 3  supervised auto    -- governor-clean, high-confidence
                                   `:draft-campaign` may auto-commit.
                                   `:publish-campaign` NEVER auto-
                                   commits, at any phase.

  `:publish-campaign` is deliberately ABSENT from every phase's `:auto`
  set, including phase 3 -- a permanent structural fact, not a rollout
  milestone still to come. Publishing is the one act here that reaches
  the public in this client's name and cannot be taken back, so it is
  always a human's call. `salesmgmt.governor` escalates it
  independently -- two layers, not one, agree on this.

  `:approve-discount` is not auto-eligible either. It is arithmetically
  bounded (the governor checks the registered ceiling and floor), but
  the bound being checkable is not a reason to spend margin without a
  human: a discount moves money, a draft does not. `:draft-campaign`
  publishes nothing and commits no spend, so it is the one auto-
  eligible op at phase 3.

  This namespace is the phase table plus the gate that reads it. It is
  pure: it never touches the store and never widens a governor
  disposition -- a phase can only add caution."
  )

(def read-ops
  "Ops that read without writing. This actor has none: every op it
  serves commits an operating record, so `read-ops` is empty rather
  than absent (the same posture `brokerage.phase` takes)."
  #{})

(def write-ops
  "Every op that can write, phase-gated below and governor-gated in
  `salesmgmt.governor` independently."
  #{:approve-discount :draft-campaign :publish-campaign})

;; NOTE the invariant: `:publish-campaign` is a member of `write-ops`
;; (governor-gated like any write) but is NEVER a member of any phase's
;; `:auto` set below. Do not add it there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed
  to auto-commit when governor-clean>}."
  {0 {:label "read-only"         :writes #{}                                    :auto #{}}
   1 {:label "assisted-draft"    :writes #{:draft-campaign}                     :auto #{}}
   2 {:label "assisted-discount" :writes #{:draft-campaign :approve-discount}   :auto #{}}
   3 {:label "supervised-auto"   :writes write-ops                              :auto #{:draft-campaign}}})

(def default-phase 3)

(defn verdict->disposition
  "Map a SalesMarketingManagementGovernor verdict to a base
  disposition, before the phase gate sees it."
  [verdict]
  (cond (:hard? verdict)     :hold
        (:escalate? verdict) :escalate
        :else                :commit))

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition :commit|:escalate|:hold, :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins; the phase can
    never turn a hold into a commit).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE
    (:phase-approval), even when the governor was clean.
  - an unknown op is in no phase's `:writes`, so it fails closed to
    HOLD by the same rule that holds a not-yet-enabled op.

  `op` is read from the request; the caller decides which request that
  is, so this stays a pure function of (phase, op, governor verdict)."
  [phase {:keys [op]} governor-disposition]
  (let [p     (if (contains? phases phase) phase default-phase)
        {:keys [writes auto]} (get phases p)
        read? (contains? read-ops op)]
    (cond
      ;; compliance wins -- a phase never widens a governor hold.
      (= :hold governor-disposition)
      {:disposition :hold :reason nil}

      ;; reads are never phase-restricted; they write nothing.
      read?
      {:disposition governor-disposition :reason nil}

      (not (contains? writes op))
      {:disposition :hold :reason :phase-disabled}

      (= :escalate governor-disposition)
      {:disposition :escalate :reason nil}

      (contains? auto op)
      {:disposition :commit :reason nil}

      :else
      {:disposition :escalate :reason :phase-approval})))
